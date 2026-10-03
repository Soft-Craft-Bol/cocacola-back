package com.cocacola.domain.helpers;

import java.util.UUID;

public final class IdGenerator {

    private IdGenerator() {
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    /** Codigo corto legible para el QR de asistencia. */
    public static String shortCode(int length) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, length).toUpperCase();
    }
}
