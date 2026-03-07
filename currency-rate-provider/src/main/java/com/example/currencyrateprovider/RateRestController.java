package com.example.currencyrateprovider;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST endpoint for USDRUB rate. Used for Pact contract verification
 * (consumer contract is defined against this HTTP API).
 */
@RestController
@RequestMapping("/api")
public class RateRestController {

    private final RateService rateService;

    public RateRestController(RateService rateService) {
        this.rateService = rateService;
    }

    @GetMapping(value = "/rate", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Double> getRate() {
        return Map.of("rate", rateService.getCurrentUsdRubRate());
    }
}
