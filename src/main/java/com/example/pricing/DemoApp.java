package com.example.pricing;

import com.example.pricing.model.Price;
import com.example.pricing.service.InMemoryPriceService;
import com.example.pricing.service.PriceService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class DemoApp {
    public static void main(String[] args) {
        PriceService service = new InMemoryPriceService();

      //batch1 start
        service.startBatch("batch-1");
        service.uploadChunk("batch-1", List.of(
                new Price("Apple", Instant.now(), "120"),
                new Price("Banana", Instant.now(), "100"),
                new Price("Grapes", Instant.now(), "50")
        ));
        
        //batch2 start
        service.startBatch("batch-2");
        service.uploadChunk("batch-2", List.of(
        		new Price("Apple", Instant.now(), "180"),
        		new Price("Banana", Instant.now(), "200"),
        		new Price("Grapes", Instant.now(), "5")
        		));
        
        service.completeBatch("batch-2");
        //batch1 complete
        service.completeBatch("batch-1");
        
        //batch2 complete

        Optional<Price> latest = service.getLatestPrice("chikku");
        System.out.println("Latest price: " + latest.map(Price::getPayload).orElse("none"));
    }
}	