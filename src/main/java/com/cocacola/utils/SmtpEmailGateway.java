package com.cocacola.utils;

import com.cocacola.domain.helpers.StorageException;
import com.cocacola.domain.model.EmailMessage;
import com.cocacola.domain.repository.EmailGateway;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Correo por SMTP (Gmail). Está configurado solo si hay servidor, usuario y contraseña
 * (spring.mail.host / username / password, normalmente con MAIL_USERNAME y MAIL_PASSWORD).
 */
@Component
public class SmtpEmailGateway implements EmailGateway {

    private final ObjectProvider<JavaMailSender> sender;
    private final String host;
    private final String username;
    private final String password;
    private final String from;

    public SmtpEmailGateway(ObjectProvider<JavaMailSender> sender,
                            @Value("${spring.mail.host:}") String host,
                            @Value("${spring.mail.username:}") String username,
                            @Value("${spring.mail.password:}") String password,
                            @Value("${mail.from:}") String from) {
        this.sender = sender;
        this.host = host;
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password;
        this.from = from == null || from.isBlank() ? this.username : from.trim();
    }

    @Override
    public boolean isConfigured() {
        return !host.isBlank() && !username.isBlank() && !password.isBlank() && sender.getIfAvailable() != null;
    }

    @Override
    public void send(EmailMessage message) {
        JavaMailSender mailSender = sender.getIfAvailable();
        if (!isConfigured() || mailSender == null) {
            throw new StorageException("El correo no está configurado: define MAIL_USERNAME y MAIL_PASSWORD (contraseña de aplicación de Gmail)");
        }
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(from, "Coca-Cola Eventos");
            helper.setTo(message.to().split("\\s*,\\s*"));
            helper.setSubject(message.subject());
            helper.setText(message.html(), true);
            if (message.inlineImages() != null) {
                for (var image : message.inlineImages().entrySet()) {
                    helper.addInline(image.getKey(), new ByteArrayResource(image.getValue()), "image/png");
                }
            }
            mailSender.send(mime);
        } catch (Exception ex) {
            throw new StorageException("No se pudo enviar el correo: " + friendly(ex), ex);
        }
    }

    /** Traduce los errores típicos de Gmail a un mensaje accionable. */
    private static String friendly(Exception ex) {
        String msg = String.valueOf(ex.getMessage());
        Throwable root = ex;
        while (root.getCause() != null) root = root.getCause();
        String all = msg + " " + root.getMessage();
        if (all.contains("534") || all.contains("535") || all.contains("Authentication") || all.contains("Username and Password not accepted")) {
            return "Gmail rechazó el usuario o la contraseña. Usa una contraseña de aplicación (con verificación en dos pasos activada), no tu contraseña normal.";
        }
        if (root instanceof java.net.UnknownHostException) return "No se pudo resolver smtp.gmail.com (revisa tu conexión a internet o el DNS).";
        if (root instanceof java.net.ConnectException || root instanceof java.net.SocketTimeoutException) {
            return "No se pudo conectar con Gmail (puerto 587 bloqueado o sin internet).";
        }
        return msg;
    }
}
