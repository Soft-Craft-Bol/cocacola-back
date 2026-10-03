package com.cocacola.config;

import com.cocacola.domain.service.CommunicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Tareas asíncronas y programadas: recordatorios y agradecimientos automáticos (cada hora, idempotentes). */
@Slf4j
@Configuration
@EnableAsync
@EnableScheduling
@RequiredArgsConstructor
public class AutomationConfig {

    private final CommunicationService communications;

    @Value("${app.automation.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "0 5 * * * *")
    public void hourly() {
        if (!enabled) return;
        try {
            communications.runAutomations();
        } catch (RuntimeException ex) {
            log.warn("Falló la automatización de comunicaciones: {}", ex.getMessage());
        }
    }
}
