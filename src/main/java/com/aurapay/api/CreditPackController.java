package com.aurapay.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/credit-packs")
public class CreditPackController {

    @GetMapping
    public List<CreditPack> listCreditPacks() {
        // Demo catalog only. Prices are expressed in INR paise (minor units).
        return List.of(
                new CreditPack("starter-100", "Starter", 100, 10000),
                new CreditPack("creator-500", "Creator", 500, 40000),
                new CreditPack("studio-1200", "Studio", 1200, 80000)
        );
    }

    public record CreditPack(
            String id,
            String name,
            int credits,
            long amountInPaise
    ) {}
}
