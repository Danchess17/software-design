package com.example.rateprinter;

import com.example.currency.grpc.GrpcObservabilityConstants;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * gRPC client: sends {@link GrpcObservabilityConstants#CLIENT_ID} for server-side metrics, logs each
 * request/response payload.
 */
@Component
@GrpcGlobalClientInterceptor
public class GrpcClientLoggingInterceptor implements ClientInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GrpcClientLoggingInterceptor.class);

    private final String clientId;

    public GrpcClientLoggingInterceptor(
            @Value("${grpc.observability.client-id:}") String configuredId,
            @Value("${spring.application.name:rate-printer}") String applicationName) {
        this.clientId = !configuredId.isEmpty() ? configuredId : applicationName;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
                next.newCall(method, callOptions)) {

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                headers.discardAll(GrpcObservabilityConstants.CLIENT_ID);
                headers.put(GrpcObservabilityConstants.CLIENT_ID, clientId);
                log.info("gRPC client request: method={} clientId={}", method.getFullMethodName(), clientId);
                Listener<RespT> wrappedListener = new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(
                        responseListener) {
                    @Override
                    public void onMessage(RespT message) {
                        log.info("gRPC client response: method={} message={}", method.getFullMethodName(), message);
                        super.onMessage(message);
                    }
                };
                super.start(wrappedListener, headers);
            }

            @Override
            public void sendMessage(ReqT message) {
                log.info("gRPC client request message: method={} message={}", method.getFullMethodName(), message);
                super.sendMessage(message);
            }
        };
    }
}
