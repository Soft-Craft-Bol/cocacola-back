package com.cocacola.domain.service;

import com.cocacola.commons.enums.Channel;
import com.cocacola.commons.enums.EventStatus;
import com.cocacola.commons.enums.MessageType;
import com.cocacola.domain.helpers.IdGenerator;
import com.cocacola.domain.helpers.NotFoundException;
import com.cocacola.domain.model.EmailMessage;
import com.cocacola.domain.model.Event;
import com.cocacola.domain.model.MessageLog;
import com.cocacola.domain.model.Participant;
import com.cocacola.domain.repository.EmailGateway;
import com.cocacola.domain.repository.EventRepository;
import com.cocacola.domain.repository.MessageLogRepository;
import com.cocacola.domain.repository.ParticipantRepository;
import com.cocacola.domain.repository.WhatsAppGateway;
import com.cocacola.utils.QrCodes;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * Comunicaciones con los participantes por correo y WhatsApp: confirmación con QR, recordatorio, agradecimiento
 * con encuesta e invitación. Las de marketing (agradecimiento e invitación) solo se envían a quien dio su consentimiento.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommunicationService {

    public record SendResult(int total, int sent, int skipped, int errors, String note) {
    }

    private final EventRepository events;
    private final ParticipantRepository participants;
    private final MessageLogRepository logs;
    private final EmailGateway email;
    private final WhatsAppGateway whatsapp;

    @Value("${app.public-url:http://localhost:5173}")
    private String publicUrl;

    @Value("${app.timezone:America/Bogota}")
    private String timezone;

    public boolean emailConfigured() {
        return email.isConfigured();
    }

    public boolean whatsappConfigured() {
        return whatsapp.isConfigured();
    }

    public List<MessageLog> history(String eventId, int limit) {
        return logs.findRecentByEventId(eventId, limit);
    }

    // ------------------------------------------------------------------ envío manual

    /** audience: ALL | ATTENDED | NOT_ATTENDED. targetEventId solo aplica a INVITATION. */
    public SendResult send(String eventId, MessageType type, Channel channel, String audience, String targetEventId) {
        Event event = events.findById(eventId).orElseThrow(() -> new NotFoundException("Evento"));
        Event target = type == MessageType.INVITATION
                ? events.findById(targetEventId == null ? "" : targetEventId)
                        .orElseThrow(() -> new IllegalArgumentException("Selecciona el evento al que invitas"))
                : null;
        if (!configured(channel)) {
            return new SendResult(0, 0, 0, 0, (channel == Channel.EMAIL ? "El correo" : "WhatsApp") + " no está configurado en el backend.");
        }
        boolean marketing = type == MessageType.THANKS || type == MessageType.INVITATION;
        List<Participant> list = participants.findByEventId(eventId).stream()
                .filter(p -> switch (audience == null ? "ALL" : audience) {
                    case "ATTENDED" -> p.getCheckedInAt() != null;
                    case "NOT_ATTENDED" -> p.getCheckedInAt() == null;
                    default -> true;
                })
                .filter(p -> !marketing || p.isConsent())
                .toList();
        int sent = 0, skipped = 0, errors = 0;
        for (Participant p : list) {
            String key = typeKey(type, target);
            if (logs.existsSent(p.getId(), key, channel.name()) || !hasContact(p, channel)) {
                skipped++;
                continue;
            }
            if (deliver(p, event, target, type, channel)) sent++;
            else errors++;
        }
        String note = marketing ? "Solo se incluyó a quienes aceptaron recibir comunicaciones." : null;
        return new SendResult(list.size(), sent, skipped, errors, note);
    }

    // ------------------------------------------------------------------ automatizaciones

    /** Confirmación de registro (asíncrona): correo y WhatsApp si están configurados. */
    @Async
    public void onRegistered(Participant p, Event event) {
        for (Channel channel : Channel.values()) {
            if (configured(channel) && hasContact(p, channel) && !logs.existsSent(p.getId(), typeKey(MessageType.CONFIRMATION, null), channel.name())) {
                deliver(p, event, null, MessageType.CONFIRMATION, channel);
            }
        }
    }

    /** Recordatorio (24 h antes) y agradecimiento con encuesta (día siguiente). Idempotente: se puede ejecutar cada hora. */
    public void runAutomations() {
        Instant now = Instant.now();
        for (Event e : events.findAll()) {
            Duration until = Duration.between(now, e.getDate());
            if (e.getStatus() != EventStatus.FINISHED && !until.isNegative() && until.toHours() >= 20 && until.toHours() <= 28) {
                autoSend(e, MessageType.REMINDER, "ALL");
            }
            Duration since = until.negated();
            if (!since.isNegative() && since.toHours() >= 18 && since.toHours() <= 60) {
                autoSend(e, MessageType.THANKS, "ATTENDED");
            }
        }
    }

    private void autoSend(Event e, MessageType type, String audience) {
        for (Channel channel : Channel.values()) {
            if (!configured(channel)) continue;
            SendResult r = send(e.getId(), type, channel, audience, null);
            if (r.sent() > 0) log.info("Automatización {} ({}) en '{}': {} enviados", type, channel, e.getName(), r.sent());
        }
    }

    // ------------------------------------------------------------------ envío individual

    private boolean deliver(Participant p, Event event, Event target, MessageType type, Channel channel) {
        MessageLog entry = MessageLog.builder().id(IdGenerator.newId()).eventId(event.getId()).participantId(p.getId())
                .channel(channel.name()).type(typeKey(type, target)).sentAt(Instant.now()).build();
        try {
            if (channel == Channel.EMAIL) {
                email.send(buildEmail(p, event, target, type));
            } else {
                whatsapp.sendText(p.getPhone(), buildText(p, event, target, type));
            }
            entry.setStatus("SENT");
            logs.save(entry);
            return true;
        } catch (RuntimeException ex) {
            entry.setStatus("ERROR");
            entry.setDetail(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            logs.save(entry);
            log.warn("No se pudo enviar {} a {}: {}", type, p.getId(), ex.getMessage());
            return false;
        }
    }

    private EmailMessage buildEmail(Participant p, Event event, Event target, MessageType type) {
        String name = HtmlUtils.htmlEscape(p.getFirstName());
        Event about = type == MessageType.INVITATION ? target : event;
        String subject;
        String body;
        Map<String, byte[]> images = Map.of();
        switch (type) {
            case CONFIRMATION -> {
                subject = "Tu registro a " + event.getName();
                body = "<p>¡Hola " + name + "! Tu registro fue confirmado.</p>" + details(event)
                        + "<p>Presenta este código QR en la entrada:</p><p><img src=\"cid:qr\" width=\"180\" height=\"180\" alt=\"QR\"/></p>"
                        + "<p style=\"color:#666\">Código: <b>" + p.getQrCode() + "</b></p>";
                images = Map.of("qr", QrCodes.png(p.getQrCode(), 360));
            }
            case REMINDER -> {
                subject = "Te esperamos mañana: " + event.getName();
                body = "<p>¡Hola " + name + "! Te recordamos tu evento.</p>" + details(event)
                        + "<p>No olvides tu código QR:</p><p><img src=\"cid:qr\" width=\"180\" height=\"180\" alt=\"QR\"/></p>";
                images = Map.of("qr", QrCodes.png(p.getQrCode(), 360));
            }
            case THANKS -> {
                subject = "Gracias por acompañarnos en " + event.getName();
                body = "<p>¡Hola " + name + "! Gracias por ser parte de <b>" + HtmlUtils.htmlEscape(event.getName()) + "</b>.</p>"
                        + "<p>Cuéntanos tu experiencia, solo toma un minuto:</p>"
                        + "<p><a href=\"" + surveyUrl(p) + "\" style=\"background:#e61a27;color:#fff;padding:12px 22px;border-radius:24px;text-decoration:none\">Responder la encuesta</a></p>";
            }
            default -> {
                subject = "Te invitamos a " + about.getName();
                body = "<p>¡Hola " + name + "! Nos encantaría verte en nuestro próximo evento.</p>" + details(about)
                        + "<p><a href=\"" + publicUrl + "/registro/" + about.getId() + "\" style=\"background:#e61a27;color:#fff;padding:12px 22px;border-radius:24px;text-decoration:none\">Quiero asistir</a></p>";
            }
        }
        return new EmailMessage(p.getEmail(), subject, wrap(body), images);
    }

    private String buildText(Participant p, Event event, Event target, MessageType type) {
        return switch (type) {
            case CONFIRMATION -> "Hola " + p.getFirstName() + ", tu registro a " + event.getName() + " fue confirmado.\n"
                    + plainDetails(event) + "\nTu código de ingreso: " + p.getQrCode() + "\nPresentalo en la entrada.";
            case REMINDER -> "Hola " + p.getFirstName() + ", te recordamos " + event.getName() + ".\n" + plainDetails(event)
                    + "\nTu código de ingreso: " + p.getQrCode();
            case THANKS -> "Gracias por acompañarnos en " + event.getName() + ", " + p.getFirstName() + ". Cuéntanos tu experiencia: " + surveyUrl(p);
            default -> "Hola " + p.getFirstName() + ", te invitamos a " + target.getName() + ". " + plainDetails(target)
                    + "\nRegístrate aquí: " + publicUrl + "/registro/" + target.getId();
        };
    }

    // ------------------------------------------------------------------ utilidades

    private boolean configured(Channel channel) {
        return channel == Channel.EMAIL ? email.isConfigured() : whatsapp.isConfigured();
    }

    private static boolean hasContact(Participant p, Channel channel) {
        String v = channel == Channel.EMAIL ? p.getEmail() : p.getPhone();
        return v != null && !v.isBlank();
    }

    private static String typeKey(MessageType type, Event target) {
        return type == MessageType.INVITATION && target != null ? "INVITATION:" + target.getId() : type.name();
    }

    private String surveyUrl(Participant p) {
        return publicUrl + "/encuesta/" + p.getQrCode();
    }

    private String when(Event e) {
        return e.getDate() == null ? "" : DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy, HH:mm", Locale.forLanguageTag("es-CO"))
                .format(e.getDate().atZone(ZoneId.of(timezone)));
    }

    private String details(Event e) {
        return "<p><b>" + HtmlUtils.htmlEscape(e.getName()) + "</b><br/>" + when(e) + "<br/>" + HtmlUtils.htmlEscape(e.getLocation() == null ? "" : e.getLocation()) + "</p>";
    }

    private String plainDetails(Event e) {
        return when(e) + (e.getLocation() == null ? "" : " · " + e.getLocation());
    }

    private static String wrap(String body) {
        return "<div style=\"font-family:Arial,sans-serif;max-width:560px;margin:auto;border:1px solid #eee;border-radius:12px;overflow:hidden\">"
                + "<div style=\"background:#e61a27;color:#fff;padding:18px 22px;font-size:20px\"><b>Coca-Cola</b> · Eventos</div>"
                + "<div style=\"padding:22px;color:#222;line-height:1.5\">" + body + "</div></div>";
    }
}
