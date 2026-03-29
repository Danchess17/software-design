package com.example.currencyrateprovider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(RateRestController.class);

    private final RateService rateService;

    public RateRestController(RateService rateService) {
        this.rateService = rateService;
    }

    @GetMapping(value = "/rate", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Double> getRate() {
        log.info("REST server request: GET /api/rate");
        Map<String, Double> body = Map.of("rate", rateService.getCurrentUsdRubRate());
        log.info("REST server response: {}", body);
        return body;
    }
}
