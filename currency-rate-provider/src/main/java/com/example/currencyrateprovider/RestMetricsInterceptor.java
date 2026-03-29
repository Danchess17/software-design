package com.example.currencyrateprovider;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * REST (/api/**): latency with mean + percentiles, RPS via timer count, HTTP 500 counter. "client" = remote host.
 */
public class RestMetricsInterceptor implements HandlerInterceptor {

    static final String TIMER_SAMPLE = RestMetricsInterceptor.class.getName() + ".sample";

    private final MeterRegistry meterRegistry;

    public RestMetricsInterceptor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(TIMER_SAMPLE, Timer.start(meterRegistry));
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {

        Timer.Sample sample = (Timer.Sample) request.getAttribute(TIMER_SAMPLE);
        String ra = request.getRemoteAddr();
        String client = ra == null || ra.isEmpty() ? "unknown" : ra;
        Timer timer = Timer.builder("currency.http.server.requests")
                .description("REST /api request duration")
                .publishPercentiles(0.5, 0.95, 0.99)
                .tag("client", client)
                .register(meterRegistry);
        if (sample != null) {
            sample.stop(timer);
        }
        if (response.getStatus() == HttpServletResponse.SC_INTERNAL_SERVER_ERROR) {
            meterRegistry.counter("currency.http.server.errors.http500", "client", client).increment();
        }
    }
}
