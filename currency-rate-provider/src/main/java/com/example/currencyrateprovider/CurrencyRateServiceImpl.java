package com.example.currencyrateprovider;

import com.example.currencyrateprovider.grpc.CurrencyRateServiceGrpc;
import com.example.currencyrateprovider.grpc.GetRateRequest;
import com.example.currencyrateprovider.grpc.GetRateResponse;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.Random;

@GrpcService
public class CurrencyRateServiceImpl extends CurrencyRateServiceGrpc.CurrencyRateServiceImplBase {

    private static final double BASE_RATE = 80.0;
    private static final double RANDOM_BOUND = 10.0;
    private final Random random = new Random();

    @Override
    public void getUsdRubRate(GetRateRequest request, StreamObserver<GetRateResponse> responseObserver) {
        // Base rate +/- random value so the number is not constant
        double randomOffset = (random.nextDouble() - 0.5) * 2 * RANDOM_BOUND;
        double rate = BASE_RATE + randomOffset;

        GetRateResponse response = GetRateResponse.newBuilder()
                .setRate(rate)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
