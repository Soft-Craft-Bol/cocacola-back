package com.cocacola.domain.service;

import com.cocacola.domain.model.EmailMessage;
import com.cocacola.domain.model.Notification;
import com.cocacola.domain.repository.EmailGateway;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/** Envía por correo (Gmail) las notificaciones importantes al equipo responsable. Es asíncrono: nunca frena la operación. */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationMailer {

    private final EmailGateway email;
    private final com.cocacola.persistence.crud.EventOperationsRepository operations;
    private final com.cocacola.domain.repository.UserRepository users;

    @Value("${notifications.email-enabled:true}")
    private boolean enabled;

    @Value("${notifications.email-to:}")
    private String recipients;

    @Value("${spring.mail.username:}")
    private String username;

    @Value("${app.public-url:http://localhost:5173}")
    private String publicUrl;

    /** Destinatarios configurados o, si no hay, la propia cuenta de Gmail. */
    public String recipients() {
        return recipients == null || recipients.isBlank() ? (username == null ? "" : username.trim()) : recipients.trim();
    }

    @Async
    public void send(Notification n) {
        String target = recipients();
        if (n.getEventId() != null) {
            var settings = operations.findById(n.getEventId());
            if (settings.isPresent()) {
                var s = settings.get();
                target = java.util.stream.Stream.of(s.getOrganizerUserId(), s.getManagerUserId())
                        .filter(java.util.Objects::nonNull).distinct().map(users::findById)
                        .flatMap(java.util.Optional::stream).filter(u -> u.isActive())
                        .map(u -> u.getEmail()).filter(e -> e != null && !e.isBlank()).distinct()
                        .collect(java.util.stream.Collectors.joining(","));
            }
        }
        // Las informativas (por ejemplo "mitad de la meta") quedan solo en la campana; por correo van metas y alertas
        if (!enabled || "info".equals(n.getSeverity()) || !email.isConfigured() || target.isBlank()) return;
        try {
            String link = n.getEventId() == null ? publicUrl : publicUrl + "/eventos/" + n.getEventId();
            String color = "warning".equals(n.getSeverity()) ? "#f59e0b" : "#16a34a";
            String html = "<div style=\"font-family:Arial,sans-serif;max-width:560px;margin:auto;border:1px solid #eee;border-radius:12px;overflow:hidden\">"
                    + "<div style=\"background:#e61a27;color:#fff;padding:16px 22px;font-size:18px\"><b>Coca-Cola</b> · Eventos</div>"
                    + "<div style=\"padding:22px;color:#222;line-height:1.5\">"
                    + "<p style=\"margin:0 0 6px;color:" + color + ";font-weight:bold\">" + HtmlUtils.htmlEscape(n.getTitle()) + "</p>"
                    + "<p style=\"margin:0 0 18px\">" + HtmlUtils.htmlEscape(n.getMessage()) + "</p>"
                    + "<a href=\"" + link + "\" style=\"background:#e61a27;color:#fff;padding:10px 20px;border-radius:22px;text-decoration:none\">Abrir en la plataforma</a>"
                    + "</div></div>";
            email.send(new EmailMessage(target, "[Coca-Cola Eventos] " + n.getTitle(), html, Map.of()));
        } catch (RuntimeException ex) {
            log.warn("No se pudo enviar por correo la notificación '{}': {}", n.getTitle(), ex.getMessage());
        }
    }
}
