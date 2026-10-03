package com.cocacola.application.rest.controller;

import com.cocacola.domain.service.CommunicationService;
import com.cocacola.domain.service.CrmService;
import com.cocacola.domain.service.InsightsService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Estado de las integraciones (qué canales están configurados en el backend). */
@RestController
@RequestMapping("/integrations")
@RequiredArgsConstructor
public class IntegrationController {

    private final CommunicationService communications;
    private final CrmService crm;
    private final InsightsService insights;

    @GetMapping("/status")
    public Map<String, Boolean> status() {
        return Map.of("email", communications.emailConfigured(), "whatsapp", communications.whatsappConfigured(),
                "crm", crm.configured(), "ai", insights.aiConfigured());
    }
}
