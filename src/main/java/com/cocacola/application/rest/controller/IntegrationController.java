package com.cocacola.application.rest.controller;

import com.cocacola.domain.service.CommunicationService;
import com.cocacola.domain.service.CrmService;
import com.cocacola.domain.service.InsightsService;
import com.cocacola.domain.helpers.StorageException;
import com.cocacola.domain.service.NotificationMailer;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final NotificationMailer mailer;

    /** Envía un correo de prueba (a ?to= o, por defecto, a los destinatarios de notificaciones). */
    @PostMapping("/email/test")
    public Map<String, String> testEmail(@RequestParam(required = false) String to) {
        if (!communications.emailConfigured()) {
            throw new StorageException("El correo no está configurado: define MAIL_USERNAME y MAIL_PASSWORD (contraseña de aplicación de Gmail)");
        }
        return Map.of("to", communications.sendTestEmail(to, mailer.recipients()));
    }

    @GetMapping("/status")
    public Map<String, Boolean> status() {
        return Map.of("email", communications.emailConfigured(), "whatsapp", communications.whatsappConfigured(),
                "crm", crm.configured(), "ai", insights.aiConfigured());
    }
}
