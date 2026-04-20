package com.example.currencyrateprovider;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcObservationConfig implements WebMvcConfigurer {

    private final MeterRegistry meterRegistry;

    public WebMvcObservationConfig(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RestMetricsInterceptor(meterRegistry)).addPathPatterns("/api/**");
    }
}
