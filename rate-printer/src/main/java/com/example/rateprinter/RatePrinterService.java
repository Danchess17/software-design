package com.example.rateprinter;

import com.example.currencyrateprovider.grpc.CurrencyRateServiceGrpc;
import com.example.currencyrateprovider.grpc.GetRateRequest;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RatePrinterService {

    @GrpcClient("currencyRateProvider")
    private CurrencyRateServiceGrpc.CurrencyRateServiceBlockingStub currencyRateStub;

    @Scheduled(fixedRate = 5000)  // every 5 seconds
    public void printRate() {
        try {
            var response = currencyRateStub.getUsdRubRate(GetRateRequest.getDefaultInstance());
            double rate = response.getRate();
            System.out.printf("[%s] USDRUB: %.4f%n", java.time.LocalDateTime.now(), rate);
        } catch (Exception e) {
            System.err.printf("[%s] Error fetching rate: %s%n", java.time.LocalDateTime.now(), e.getMessage());
        }
    }
}
