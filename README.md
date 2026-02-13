# Latest Value Price Service

This is a small Maven-based Java project that provides an in-memory, thread-safe **latest-value price service**.

The service is intended to be used from within the same JVM via the `PriceService` interface. It supports
batch-oriented price loading where only fully completed batches become visible to consumers.

## Features

- In-memory storage, no external database.
- Batch lifecycle:
  - `startBatch(batchId)`
  - `uploadChunk(batchId, List<Price>)` (chunks may be uploaded in parallel)
  - `completeBatch(batchId)` or `cancelBatch(batchId)`
- Only prices from completed batches are visible.
- Cancelled batches are discarded.
- Latest price is chosen by `asOf` timestamp (not by arrival order).
- Thread-safe implementation using `ConcurrentHashMap` and simple synchronization.

## Project Structure

- `com.example.pricing.model.Price` – immutable price value object.
- `com.example.pricing.service.PriceService` – service API exposed in the same JVM.
- `com.example.pricing.service.InMemoryPriceService` – in-memory implementation.
- `InMemoryPriceServiceTest` – unit tests covering the main behaviors.

## Prerequisites

- Java 17 or later
- Maven 3.8+ on your `PATH`

## Building and Running Tests

From the project root directory:

```bash
mvn clean test
```

This will compile the project and run all unit tests.

## Using the Service

Instantiate the service and interact with it through the `PriceService` interface:

```java
PriceService service = new InMemoryPriceService();

service.startBatch("batch-1");
service.uploadChunk("batch-1", List.of(
        new Price("INSTR1", Instant.now(), "payload-1")
));
service.completeBatch("batch-1");

Optional<Price> latest = service.getLatestPrice("INSTR1");
```

