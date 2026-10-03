package com.cocacola.domain.repository;

/** Puerto de WhatsApp (API Cloud de Meta). */
public interface WhatsAppGateway {

    boolean isConfigured();

    void sendText(String phone, String text);
}
