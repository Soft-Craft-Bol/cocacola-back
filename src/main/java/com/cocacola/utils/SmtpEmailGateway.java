package com.cocacola.utils;

import com.cocacola.domain.helpers.StorageException;
import com.cocacola.domain.model.EmailMessage;
import com.cocacola.domain.repository.EmailGateway;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** Correo por SMTP. Se activa definiendo spring.mail.host (y usuario/clave) en application.properties. */
@Component
public class SmtpEmailGateway implements EmailGateway {

    private final ObjectProvider<JavaMailSender> sender;
    private final String host;
    private final String from;

    public SmtpEmailGateway(ObjectProvider<JavaMailSender> sender,
                            @Value("${spring.mail.host:}") String host,
                            @Value("${mail.from:no-reply@cocacola-eventos.local}") String from) {
        this.sender = sender;
        this.host = host;
        this.from = from;
    }

    @Override
    public boolean isConfigured() {
        return host != null && !host.isBlank() && sender.getIfAvailable() != null;
    }

    @Override
    public void send(EmailMessage message) {
        JavaMailSender mailSender = sender.getIfAvailable();
        if (!isConfigured() || mailSender == null) {
            throw new StorageException("El correo no está configurado: define spring.mail.host en application.properties");
        }
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.html(), true);
            if (message.inlineImages() != null) {
                for (var image : message.inlineImages().entrySet()) {
                    helper.addInline(image.getKey(), new org.springframework.core.io.ByteArrayResource(image.getValue()), "image/png");
                }
            }
            mailSender.send(mime);
        } catch (Exception ex) {
            throw new StorageException("No se pudo enviar el correo: " + ex.getMessage(), ex);
        }
    }
}
