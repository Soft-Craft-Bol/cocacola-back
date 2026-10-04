package com.cocacola;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cocacola.utils.AiProvider;
import com.cocacola.utils.AiTextGenerator;
import com.cocacola.utils.OpenAiCompatibleProvider;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Selección y respaldo entre proveedores de IA, y reglas de activación del proveedor compatible con OpenAI. */
class AiTextGeneratorTests {

	@Test
	void usesFirstConfiguredAndFallsBack() {
		AiProvider claude = mock(AiProvider.class);
		AiProvider free = mock(AiProvider.class);
		when(claude.isConfigured()).thenReturn(false);
		when(free.isConfigured()).thenReturn(true);
		when(free.generate("s", "p")).thenReturn(Optional.of("texto"));

		AiTextGenerator generator = new AiTextGenerator(List.of(claude, free));
		assertTrue(generator.isConfigured());
		assertEquals(Optional.of("texto"), generator.generate("s", "p"));
		verify(claude, never()).generate("s", "p");

		// si el primero está configurado pero falla, responde el segundo
		when(claude.isConfigured()).thenReturn(true);
		when(claude.generate("s", "p")).thenReturn(Optional.empty());
		assertEquals(Optional.of("texto"), generator.generate("s", "p"));

		// ninguno configurado
		assertFalse(new AiTextGenerator(List.of()).isConfigured());
		assertEquals(Optional.empty(), new AiTextGenerator(List.of()).generate("s", "p"));
	}

	@Test
	void openAiCompatibleActivationRules() {
		// sin clave en un servidor remoto: desactivado
		assertFalse(new OpenAiCompatibleProvider("https://opencode.ai/zen/v1", "", "big-pickle").isConfigured());
		// con clave: activo
		assertTrue(new OpenAiCompatibleProvider("https://opencode.ai/zen/v1/", "k", "big-pickle").isConfigured());
		// Ollama local no necesita clave
		assertTrue(new OpenAiCompatibleProvider("http://localhost:11434/v1", "", "llama3.2").isConfigured());
		// sin modelo o sin URL: desactivado
		assertFalse(new OpenAiCompatibleProvider("https://x/v1", "k", "").isConfigured());
		assertFalse(new OpenAiCompatibleProvider("", "k", "m").isConfigured());
		// sin configurar no hace ninguna llamada
		assertEquals(Optional.empty(), new OpenAiCompatibleProvider("", "", "").generate("s", "p"));
	}
}
