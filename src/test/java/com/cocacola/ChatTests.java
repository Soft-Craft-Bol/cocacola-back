package com.cocacola;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cocacola.domain.repository.TextGenerator;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.Filter;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Asistente de consulta: solo con datos de la plataforma, con reglas fijas y sin datos personales. */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:chat;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"app.seed.demo=true"
})
class ChatTests {

	@Autowired WebApplicationContext context;
	@Autowired @Qualifier("springSecurityFilterChain") Filter securityFilter;
	@MockitoBean TextGenerator ai;

	@Test
	void chatAnswersFromPlatformDataOnly() throws Exception {
		MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();
		String login = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"admin@cocacola.com\",\"password\":\"admin123\"}")).andReturn().getResponse().getContentAsString();
		String auth = "Bearer " + JsonPath.read(login, "$.token");
		String body = "{\"message\":\"Cuántas personas asistieron?\",\"history\":[{\"role\":\"user\",\"text\":\"hola\"}]}";

		mvc.perform(post("/chat").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
		mvc.perform(post("/chat").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("{\"message\":\" \"}"))
				.andExpect(status().isBadRequest());

		// sin IA configurada: lo dice y no inventa
		when(ai.isConfigured()).thenReturn(false);
		mvc.perform(post("/chat").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk()).andExpect(jsonPath("$.source").value("local"));

		// con IA: recibe las reglas, los datos agregados y la pregunta
		when(ai.isConfigured()).thenReturn(true);
		when(ai.generate(anyString(), anyString())).thenReturn(Optional.of("Asistieron 10 personas."));
		mvc.perform(post("/chat").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk()).andExpect(jsonPath("$.source").value("ia")).andExpect(jsonPath("$.answer").value("Asistieron 10 personas."));
		ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
		verify(ai).generate(system.capture(), prompt.capture());
		assertTrue(system.getValue().contains("SOLO con la información de la sección DATOS"));
		assertTrue(prompt.getValue().contains("DATOS:") && prompt.getValue().contains("Totales:") && prompt.getValue().contains("PREGUNTA: Cuántas"));
		assertTrue(!prompt.getValue().contains("@"), "el resumen no debe incluir correos");
	}
}
