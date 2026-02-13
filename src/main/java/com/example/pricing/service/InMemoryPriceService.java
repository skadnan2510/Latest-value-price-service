package com.example.pricing.service;

import com.example.pricing.model.Price;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, in-memory implementation of {@link PriceService}.
 *
 * Design notes:
 * - Latest values are stored in a ConcurrentHashMap keyed by instrument id.
 * - Batches are accumulated in memory and only applied to the latest map when completed.
 * - Consumers never see partial batches because batch data is only merged on {@link #completeBatch(String)}.
 * - Incorrect method call order is tolerated: missing batches on complete/cancel are treated as no-ops,
 *   and uploadChunk will create an in-progress batch implicitly.
 */
public class InMemoryPriceService implements PriceService {

    private final ConcurrentHashMap<String, BatchState> batches = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Price> latestPrices = new ConcurrentHashMap<>();

    @Override
    public void startBatch(String batchId) {
        String id = normalizeBatchId(batchId);
        // Reset existing batch or create a new one.
        batches.compute(id, (key, existing) -> {
            if (existing == null) {
                return new BatchState();
            }
            existing.reset();
            return existing;
        });
    }

    @Override
    public void uploadChunk(String batchId, List<Price> prices) {
        String id = normalizeBatchId(batchId);
        if (prices == null || prices.isEmpty()) {
            return;
        }

        BatchState state = batches.computeIfAbsent(id, key -> new BatchState());
        state.addPrices(prices);
    }

    @Override
    public void completeBatch(String batchId) {
        String id = normalizeBatchId(batchId);
        BatchState state = batches.remove(id);
        if (state == null) {
            // Unknown batch id, nothing to do.
            return;
        }

        List<Price> completed = state.completeAndSnapshot();
        if (completed.isEmpty()) {
            return;
        }

        // Apply completed batch to latest prices.
        for (Price price : completed) {
            if (price == null) {
                continue;
            }
            String instrumentId = price.getId();
            if (instrumentId == null) {
                continue;
            }
            latestPrices.compute(instrumentId, (key, existing) -> selectLatest(existing, price));
        }
    }

    @Override
    public void cancelBatch(String batchId) {
        String id = normalizeBatchId(batchId);
        BatchState state = batches.remove(id);
        if (state != null) {
            state.cancel();
        }
    }

    @Override
    public Optional<Price> getLatestPrice(String instrumentId) {
        if (instrumentId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(latestPrices.get(instrumentId));
    }

    @Override
    public Map<String, Price> getLatestPrices(Collection<String> instrumentIds) {
        if (instrumentIds == null || instrumentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Price> result = new ConcurrentHashMap<>();
        for (String id : instrumentIds) {
            if (id == null) {
                continue;
            }
            Price price = latestPrices.get(id);
            if (price != null) {
                result.put(id, price);
            }
        }
        return result;
    }

    private static String normalizeBatchId(String batchId) {
        Objects.requireNonNull(batchId, "batchId must not be null");
        String trimmed = batchId.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("batchId must not be empty");
        }
        return trimmed;
    }

    private static Price selectLatest(Price existing, Price candidate) {
        if (existing == null) {
            return candidate;
        }
        Instant existingAsOf = existing.getAsOf();
        Instant candidateAsOf = candidate.getAsOf();

        if (candidateAsOf == null) {
            return existing;
        }
        if (existingAsOf == null) {
            return candidate;
        }
        return candidateAsOf.isAfter(existingAsOf) ? candidate : existing;
    }

    /**
     * Mutable batch state guarded by its own intrinsic lock.
     */
    private static final class BatchState {
        private final List<Price> prices = new ArrayList<>();
        private boolean cancelled = false;
        private boolean completed = false;

        synchronized void addPrices(List<Price> newPrices) {
            if (cancelled || completed || newPrices == null || newPrices.isEmpty()) {
                return;
            }
            prices.addAll(newPrices);
        }

        synchronized List<Price> completeAndSnapshot() {
            if (cancelled || completed) {
                return Collections.emptyList();
            }
            completed = true;
            return new ArrayList<>(prices);
        }

        synchronized void cancel() {
            if (cancelled) {
                return;
            }
            cancelled = true;
            prices.clear();
        }

        synchronized void reset() {
            cancelled = false;
            completed = false;
            prices.clear();
        }
    }
}

