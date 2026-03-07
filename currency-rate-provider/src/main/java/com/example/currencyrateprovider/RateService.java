package com.example.currencyrateprovider;

import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class RateService {

    private static final double BASE_RATE = 80.0;
    private static final double RANDOM_BOUND = 10.0;
    private final Random random = new Random();

    public double getCurrentUsdRubRate() {
        double randomOffset = (random.nextDouble() - 0.5) * 2 * RANDOM_BOUND;
        return BASE_RATE + randomOffset;
    }
}
