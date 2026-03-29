package com.example.currencyrateprovider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Logs application version on startup ({@link BuildProperties} from {@code spring-boot:build-info}).
 */
@Component
public class StartupVersionLogger {

    private static final Logger log = LoggerFactory.getLogger(StartupVersionLogger.class);

    private final ObjectProvider<BuildProperties> buildProperties;

    public StartupVersionLogger(ObjectProvider<BuildProperties> buildProperties) {
        this.buildProperties = buildProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logVersion() {
        BuildProperties props = buildProperties.getIfAvailable();
        String version = props != null ? props.getVersion() : "unknown";
        String name = props != null ? props.getName() : "currency-rate-provider";
        log.info("Application started: name={} version={}", name, version);
    }
}
