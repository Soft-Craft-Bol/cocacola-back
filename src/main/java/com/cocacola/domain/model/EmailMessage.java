package com.cocacola.domain.model;

import java.util.Map;

/** Correo HTML con imágenes incrustadas (cid) opcionales. */
public record EmailMessage(String to, String subject, String html, Map<String, byte[]> inlineImages) {
}
