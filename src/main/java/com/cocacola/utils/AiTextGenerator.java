package com.cocacola.utils;

import com.cocacola.domain.repository.TextGenerator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Usa los proveedores de IA configurados, en orden; si uno falla o no responde, prueba el siguiente. */
@Component
public class AiTextGenerator implements TextGenerator {

    private static final long TTL_MS = 6L * 60 * 60 * 1000;
    private static final int MAX_ENTRIES = 300;

    private final List<AiProvider> providers;
    // La misma consulta (mismos datos) devuelve el texto guardado: evita esperar a la IA y gastar tokens
    private final java.util.Map<String, Map.Entry<Long, String>> answers = new java.util.concurrent.ConcurrentHashMap<>();

    public AiTextGenerator(List<AiProvider> providers) {
        this.providers = providers;
    }

    @Override
    public boolean isConfigured() {
        return providers.stream().anyMatch(AiProvider::isConfigured);
    }

    @Override
    public Optional<String> generate(String system, String prompt) {
        String key = Integer.toHexString(system.hashCode()) + ":" + prompt.length() + ":" + prompt.hashCode();
        var hit = answers.get(key);
        if (hit != null && hit.getKey() > System.currentTimeMillis()) return Optional.of(hit.getValue());
        Optional<String> text = generateUncached(system, prompt);
        text.ifPresent(t -> {
            if (answers.size() > MAX_ENTRIES) answers.clear();
            answers.put(key, Map.entry(System.currentTimeMillis() + TTL_MS, t));
        });
        return text;
    }

    private Optional<String> generateUncached(String system, String prompt) {
        for (AiProvider provider : providers) {
            if (!provider.isConfigured()) continue;
            Optional<String> text = provider.generate(system, prompt);
            if (text.isPresent()) return text;
        }
        return Optional.empty();
    }
}
