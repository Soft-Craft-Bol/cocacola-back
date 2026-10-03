package com.cocacola.domain.repository;

import com.cocacola.domain.model.EmailMessage;

/** Puerto de correo electrónico (SMTP). */
public interface EmailGateway {

    boolean isConfigured();

    void send(EmailMessage message);
}
