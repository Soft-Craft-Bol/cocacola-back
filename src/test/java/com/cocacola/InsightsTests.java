package com.cocacola;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.Filter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Segmentación, afinidad, predicción, recomendaciones y resumen (con los datos de demostración). */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:insights;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"app.seed.demo=true"
})
class InsightsTests {

	@Autowired
	WebApplicationContext context;

	@Autowired
	@Qualifier("springSecurityFilterChain")
	Filter securityFilter;

	@Test
	void insights() throws Exception {
		MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();
		String login = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"marketing@cocacola.com\",\"password\":\"mkt123\"}")).andReturn().getResponse().getContentAsString();
		String auth = "Bearer " + JsonPath.read(login, "$.token");
		String orgAuth = "Bearer " + JsonPath.read(mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"organizador@cocacola.com\",\"password\":\"org123\"}")).andReturn().getResponse().getContentAsString(), "$.token");

		mvc.perform(get("/insights/segments").header("Authorization", orgAuth)).andExpect(status().isForbidden());

		// segmentos: cubren a todos los participantes del evento, sin solaparse
		String segs = mvc.perform(get("/insights/segments").param("eventId", "e1").header("Authorization", auth))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(5)).andReturn().getResponse().getContentAsString();
		List<Integer> counts = JsonPath.read(segs, "$[*].count");
		List<Double> pcts = JsonPath.read(segs, "$[*].pct");
		assertEquals(90, counts.stream().mapToInt(Integer::intValue).sum());
		assertEquals(100.0, pcts.stream().mapToDouble(Double::doubleValue).sum(), 0.01);
		List<Integer> ausentes = JsonPath.read(segs, "$[?(@.key=='ausentes')].count");
		assertTrue(ausentes.get(0) > 0);

		// afinidad: ordenada de mayor a menor, con explicación
		String aff = mvc.perform(get("/insights/affinity").param("eventId", "e1").param("limit", "10").header("Authorization", auth))
				.andExpect(status().isOk()).andExpect(jsonPath("$.top.length()").value(10)).andReturn().getResponse().getContentAsString();
		List<Integer> scores = JsonPath.read(aff, "$.top[*].score");
		for (int i = 1; i < scores.size(); i++) assertTrue(scores.get(i - 1) >= scores.get(i));
		assertTrue(scores.get(0) > scores.get(scores.size() - 1) || scores.get(0) > 0);
		List<List<String>> factors = JsonPath.read(aff, "$.top[*].factors");
		assertFalse(factors.get(0).isEmpty());

		// predicción: el evento activo (e3) usa la asistencia histórica
		String fc = mvc.perform(get("/insights/forecast").header("Authorization", auth))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		List<String> ids = JsonPath.read(fc, "$[*].eventId");
		assertEquals(List.of("e3"), ids);
		int expected = JsonPath.read(fc, "$[0].expectedAttendees");
		int low = JsonPath.read(fc, "$[0].low");
		int high = JsonPath.read(fc, "$[0].high");
		int registered = JsonPath.read(fc, "$[0].registered");
		assertTrue(low <= expected && expected <= high && high <= registered);

		// recomendaciones y resumen local (sin clave de IA)
		mvc.perform(get("/insights/recommendations").param("eventId", "e1").header("Authorization", auth))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].title").isString());
		mvc.perform(get("/insights/summary").param("eventId", "e1").param("ai", "true").header("Authorization", auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.aiSource").value("local"))
				.andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.startsWith("Asistieron")));
		mvc.perform(get("/insights/summary").header("Authorization", auth)).andExpect(status().isOk());
		mvc.perform(get("/insights/status").header("Authorization", auth)).andExpect(jsonPath("$.aiConfigured").value(false));
	}
}
