package com.cocacola;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Narración (sin voz configurada responde 503 para usar la del navegador) y análisis de predicciones. */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:narration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"app.seed.demo=false",
		"ai.openai.api-key=",
		"tts.elevenlabs.api-key="
})
class NarrationTests {

	@Autowired WebApplicationContext context;
	@Autowired @Qualifier("springSecurityFilterChain") Filter securityFilter;

	@Test
	void narrationFallsBackAndPredictionsAreExplained() throws Exception {
		MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();
		String login = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"admin@cocacola.com\",\"password\":\"admin123\"}")).andReturn().getResponse().getContentAsString();
		String auth = "Bearer " + JsonPath.read(login, "$.token");

		mvc.perform(get("/insights/status").header("Authorization", auth)).andExpect(status().isOk()).andExpect(jsonPath("$.narration").value("navegador"));
		mvc.perform(post("/insights/narrate").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Hola\"}"))
				.andExpect(status().isServiceUnavailable());
		mvc.perform(post("/insights/narrate").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"  \"}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/insights/narrate").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Hola\"}")).andExpect(status().isUnauthorized());
		mvc.perform(get("/insights/predictions/analysis").header("Authorization", auth)).andExpect(status().isOk())
				.andExpect(jsonPath("$.source").value("local")).andExpect(jsonPath("$.text").isNotEmpty());
	}
}
