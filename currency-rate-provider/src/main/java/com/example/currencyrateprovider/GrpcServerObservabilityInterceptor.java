package com.example.currencyrateprovider;

import com.example.currency.grpc.GrpcObservabilityConstants;
import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * gRPC server: logs each request/response message, records latency (mean + percentiles) and RPS via
 * {@link Timer}, counts INTERNAL status as HTTP-500-class server errors. Client dimension from
 * {@link GrpcObservabilityConstants#CLIENT_ID} metadata.
 */
@Component
@GrpcGlobalServerInterceptor
public class GrpcServerObservabilityInterceptor implements ServerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GrpcServerObservabilityInterceptor.class);

    private final MeterRegistry meterRegistry;

    public GrpcServerObservabilityInterceptor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String clientId = Optional.ofNullable(headers.get(GrpcObservabilityConstants.CLIENT_ID))
                .orElse("unknown");
        String method = call.getMethodDescriptor().getFullMethodName();

        Timer timer = Timer.builder("currency.grpc.server.requests")
                .description("Currency gRPC unary call duration")
                .publishPercentiles(0.5, 0.95, 0.99)
                .tag("client", clientId)
                .register(meterRegistry);

        Timer.Sample sample = Timer.start(meterRegistry);

        ServerCall<ReqT, RespT> wrappedCall = new ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {

            @Override
            public void sendMessage(RespT message) {
                log.info("gRPC server response: method={} clientId={} message={}", method, clientId, message);
                super.sendMessage(message);
            }

            @Override
            public void close(Status status, Metadata trailers) {
                sample.stop(timer);
                if (status.getCode() == Status.Code.INTERNAL) {
                    meterRegistry.counter("currency.grpc.server.errors.http500", "client", clientId).increment();
                }
                super.close(status, trailers);
            }
        };

        ServerCall.Listener<ReqT> listener = next.startCall(wrappedCall, headers);
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT>(listener) {
            @Override
            public void onMessage(ReqT message) {
                log.info("gRPC server request: method={} clientId={} message={}", method, clientId, message);
                super.onMessage(message);
            }
        };
    }
}
