package com.example.pricing.service;

import com.example.pricing.model.Price;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryPriceServiceTest {

    private final InMemoryPriceService service = new InMemoryPriceService();

    @Test
    void completedBatchBecomesVisible() {
        String batchId = "batch-1";
        Instant now = Instant.now();
        Price p1 = new Price("INSTR1", now, "payload-1");

        service.startBatch(batchId);
        service.uploadChunk(batchId, Collections.singletonList(p1));

        assertEquals(Optional.empty(), service.getLatestPrice("INSTR1"),
                "Price from in-progress batch must not be visible");

        service.completeBatch(batchId);

        Optional<Price> latest = service.getLatestPrice("INSTR1");
        assertTrue(latest.isPresent());
        assertEquals(p1.getAsOf(), latest.get().getAsOf());
        assertEquals(p1.getPayload(), latest.get().getPayload());
    }

    @Test
    void cancelledBatchIsDiscarded() {
        String batchId = "batch-cancel";
        Price p1 = new Price("INSTR1", Instant.now(), "payload-1");

        service.startBatch(batchId);
        service.uploadChunk(batchId, Collections.singletonList(p1));
        service.cancelBatch(batchId);

        assertEquals(Optional.empty(), service.getLatestPrice("INSTR1"));

        // Completing after cancel should be a no-op since the batch is removed on cancel.
        service.completeBatch(batchId);
        assertEquals(Optional.empty(), service.getLatestPrice("INSTR1"));
    }

    @Test
    void latestPriceIsChosenByAsOfNotArrivalOrder() {
        String batch1 = "b1";
        String batch2 = "b2";

        Instant t1 = Instant.now();
        Instant t2 = t1.plusSeconds(10);

        Price older = new Price("INSTR1", t1, "older");
        Price newer = new Price("INSTR1", t2, "newer");

        // First batch with newer timestamp
        service.startBatch(batch1);
        service.uploadChunk(batch1, Collections.singletonList(newer));
        service.completeBatch(batch1);

        // Second batch with older timestamp should not override the newer one
        service.startBatch(batch2);
        service.uploadChunk(batch2, Collections.singletonList(older));
        service.completeBatch(batch2);

        Optional<Price> latest = service.getLatestPrice("INSTR1");
        assertTrue(latest.isPresent());
        assertEquals(newer.getAsOf(), latest.get().getAsOf());
        assertEquals("newer", latest.get().getPayload());
    }

    @Test
    void inProgressBatchesAreNeverVisible() {
        String batch1 = "b1";
        String batch2 = "b2";

        Price p1 = new Price("INSTR1", Instant.now(), "p1");
        Price p2 = new Price("INSTR2", Instant.now(), "p2");

        service.startBatch(batch1);
        service.uploadChunk(batch1, Collections.singletonList(p1));

        service.startBatch(batch2);
        service.uploadChunk(batch2, Collections.singletonList(p2));
        service.completeBatch(batch2);

        // Only INSTR2 should be visible as batch1 is still in progress.
        assertEquals(Optional.empty(), service.getLatestPrice("INSTR1"));
        Optional<Price> latest2 = service.getLatestPrice("INSTR2");
        assertTrue(latest2.isPresent());
        assertEquals("p2", latest2.get().getPayload());
    }

    @Test
    void incorrectCallOrderIsTolerated() {
        // Completing or cancelling unknown batches should not throw.
        service.completeBatch("missing");
        service.cancelBatch("missing");

        // Uploading before start implicitly creates the batch.
        Price p1 = new Price("INSTR1", Instant.now(), "p1");
        String batchId = "implicit-batch";
        service.uploadChunk(batchId, Collections.singletonList(p1));
        service.completeBatch(batchId);

        Optional<Price> latest = service.getLatestPrice("INSTR1");
        assertTrue(latest.isPresent());
        assertEquals("p1", latest.get().getPayload());
    }

    @Test
    void getLatestPricesReturnsOnlyRequestedIds() {
        String batchId = "batch";
        Price p1 = new Price("INSTR1", Instant.now(), "p1");
        Price p2 = new Price("INSTR2", Instant.now(), "p2");

        service.startBatch(batchId);
        service.uploadChunk(batchId, Arrays.asList(p1, p2));
        service.completeBatch(batchId);

        Map<String, Price> latest = service.getLatestPrices(Arrays.asList("INSTR1", "MISSING"));
        assertEquals(1, latest.size());
        assertTrue(latest.containsKey("INSTR1"));
        assertEquals("p1", latest.get("INSTR1").getPayload());
    }

    @Test
    void concurrentChunkUploadsAreHandledSafely() throws ExecutionException, InterruptedException {
        String batchId = "concurrent-batch";
        service.startBatch(batchId);

        int threadCount = 4;
        int pricesPerThread = 50;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            List<Callable<Void>> tasks = Arrays.asList(
                    createUploadTask(batchId, 0, pricesPerThread),
                    createUploadTask(batchId, pricesPerThread, pricesPerThread * 2),
                    createUploadTask(batchId, pricesPerThread * 2, pricesPerThread * 3),
                    createUploadTask(batchId, pricesPerThread * 3, pricesPerThread * 4)
            );

            List<Future<Void>> futures = executor.invokeAll(tasks);
            for (Future<Void> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }

        service.completeBatch(batchId);

        // All instrument ids should be present. We reconstruct ids via individual lookups.
        int found = 0;
        for (int i = 0; i < threadCount * pricesPerThread; i++) {
            String id = "INSTR-" + i;
            if (service.getLatestPrice(id).isPresent()) {
                found++;
            }
        }
        assertEquals(threadCount * pricesPerThread, found);
    }

    private Callable<Void> createUploadTask(String batchId, int fromInclusive, int toExclusive) {
        return () -> {
            for (int i = fromInclusive; i < toExclusive; i++) {
                Price p = new Price("INSTR-" + i, Instant.now(), "p-" + i);
                service.uploadChunk(batchId, Collections.singletonList(p));
            }
            return null;
        };
    }
}

