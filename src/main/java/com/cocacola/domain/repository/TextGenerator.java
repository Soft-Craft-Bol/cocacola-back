package com.cocacola.domain.repository;

import java.util.Optional;

/** Puerto de generación de texto con IA (Claude). Vacío si no está configurado o falla. */
public interface TextGenerator {

    boolean isConfigured();

    Optional<String> generate(String system, String prompt);
}
