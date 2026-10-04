package com.cocacola.utils;

import com.cocacola.domain.repository.TextGenerator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Usa los proveedores de IA configurados, en orden; si uno falla o no responde, prueba el siguiente. */
@Component
public class AiTextGenerator implements TextGenerator {

    private final List<AiProvider> providers;

    public AiTextGenerator(List<AiProvider> providers) {
        this.providers = providers;
    }

    @Override
    public boolean isConfigured() {
        return providers.stream().anyMatch(AiProvider::isConfigured);
    }

    @Override
    public Optional<String> generate(String system, String prompt) {
        for (AiProvider provider : providers) {
            if (!provider.isConfigured()) continue;
            Optional<String> text = provider.generate(system, prompt);
            if (text.isPresent()) return text;
        }
        return Optional.empty();
    }
}
