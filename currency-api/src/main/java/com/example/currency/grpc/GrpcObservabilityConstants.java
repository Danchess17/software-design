package com.example.currency.grpc;

import io.grpc.Metadata;

/**
 * Shared gRPC metadata for per-client metrics and logging on the server.
 */
public final class GrpcObservabilityConstants {

    /** Sent by clients so the server can tag RPS/latency/error metrics per caller. */
    public static final Metadata.Key<String> CLIENT_ID =
            Metadata.Key.of("x-client-id", Metadata.ASCII_STRING_MARSHALLER);

    private GrpcObservabilityConstants() {
    }
}
