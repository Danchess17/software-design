package com.example.currencyrateprovider;

import com.example.currencyrateprovider.grpc.CurrencyRateServiceGrpc;
import com.example.currencyrateprovider.grpc.GetRateRequest;
import com.example.currencyrateprovider.grpc.GetRateResponse;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class CurrencyRateServiceImpl extends CurrencyRateServiceGrpc.CurrencyRateServiceImplBase {

    private final RateService rateService;

    public CurrencyRateServiceImpl(RateService rateService) {
        this.rateService = rateService;
    }

    @Override
    public void getUsdRubRate(GetRateRequest request, StreamObserver<GetRateResponse> responseObserver) {
        double rate = rateService.getCurrentUsdRubRate();
        GetRateResponse response = GetRateResponse.newBuilder().setRate(rate).build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
