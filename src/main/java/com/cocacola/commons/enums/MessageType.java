package com.cocacola.commons.enums;

public enum MessageType {
    /** Confirmación de registro con su QR (transaccional). */
    CONFIRMATION,
    /** Recordatorio previo al evento (transaccional). */
    REMINDER,
    /** Agradecimiento y encuesta posterior (solo con consentimiento). */
    THANKS,
    /** Invitación a un próximo evento (solo con consentimiento). */
    INVITATION
}
