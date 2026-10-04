package com.cocacola.utils;

import java.util.Optional;

/** Proveedor concreto de IA (Claude, o cualquier API compatible con OpenAI). */
public interface AiProvider {

    boolean isConfigured();

    Optional<String> generate(String system, String prompt);
}
