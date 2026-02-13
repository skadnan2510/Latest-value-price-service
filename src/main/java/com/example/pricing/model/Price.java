package com.example.pricing.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable value object representing a price for a financial instrument.
 *
 * The payload is intentionally kept as a generic Object to allow callers
 * to attach any structure they need (e.g. BigDecimal, custom DTO, map).
 */
public final class Price {

    private final String id;
    private final Instant asOf;
    private final Object payload;

    public Price(String id, Instant asOf, Object payload) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.asOf = Objects.requireNonNull(asOf, "asOf must not be null");
        this.payload = payload;
    }

    public String getId() {
        return id;
    }

    public Instant getAsOf() {
        return asOf;
    }

    public Object getPayload() {
        return payload;
    }
}

