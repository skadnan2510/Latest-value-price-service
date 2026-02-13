package com.example.pricing.service;

import com.example.pricing.model.Price;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory latest-value price service API.
 *
 * All methods are expected to be thread-safe.
 */
public interface PriceService {

    /**
     * Marks the beginning of a new batch.
     * If a batch with the same id already exists, it is reset to an empty in-progress batch.
     */
    void startBatch(String batchId);

    /**
     * Adds a chunk of prices to the given batch.
     * This method is safe to call from multiple threads for the same batch id.
     * The batch will be created implicitly if it does not exist yet.
     */
    void uploadChunk(String batchId, List<Price> prices);

    /**
     * Marks the batch as complete and makes its prices visible.
     * Only the prices from completed batches are visible to consumers.
     * Completing an unknown batch id is a no-op.
     */
    void completeBatch(String batchId);

    /**
     * Cancels the batch and discards any collected prices.
     * Cancelling an unknown batch id is a no-op.
     */
    void cancelBatch(String batchId);

    /**
     * Returns the latest price for the given instrument, if available.
     */
    Optional<Price> getLatestPrice(String instrumentId);

    /**
     * Returns the latest prices for the given instrument ids.
     * Missing instruments are simply absent from the result map.
     */
    Map<String, Price> getLatestPrices(Collection<String> instrumentIds);
}

