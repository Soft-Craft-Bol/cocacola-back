package com.cocacola;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

/** Eventos públicos para el landing y recuperación de la entrada por correo, celular o código. */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:publicticket;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"app.seed.demo=false",
		"public.lookup.max-attempts=6"
})
class PublicTicketTests {

	@Autowired
	WebApplicationContext context;

	@Autowired
	@Qualifier("springSecurityFilterChain")
	Filter securityFilter;

	private String json(MockMvc mvc, String path, String body, String auth) throws Exception {
		var req = post(path).contentType(MediaType.APPLICATION_JSON).content(body);
		if (auth != null) req.header("Authorization", auth);
		return mvc.perform(req).andReturn().getResponse().getContentAsString();
	}

	@Test
	void publicEventsAndTickets() throws Exception {
		MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();
		String auth = "Bearer " + JsonPath.read(json(mvc, "/auth/login", "{\"email\":\"admin@cocacola.com\",\"password\":\"admin123\"}", null), "$.token");

		String open = JsonPath.read(json(mvc, "/events", "{\"name\":\"Abierto\",\"type\":\"Festival o concierto\",\"date\":\"2026-12-01T15:00:00Z\","
				+ "\"location\":\"Cali\",\"budget\":9000000,\"organizer\":\"Secreto\"}", auth), "$.id");
		String done = JsonPath.read(json(mvc, "/events", "{\"name\":\"Terminado\",\"type\":\"Evento deportivo\",\"date\":\"2026-01-01T15:00:00Z\","
				+ "\"location\":\"Cali\",\"status\":\"finished\"}", auth), "$.id");

		// landing: solo eventos abiertos, sin datos internos
		String list = mvc.perform(get("/public/events")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].name").value("Abierto")).andExpect(jsonPath("$[0].status").value("planned"))
				.andReturn().getResponse().getContentAsString();
		assertFalse(list.contains("budget"));
		assertFalse(list.contains("Secreto"));

		// inscripciones
		String ana = json(mvc, "/participants", "{\"eventId\":\"" + open + "\",\"firstName\":\"Ana\",\"lastName\":\"Lopez\",\"phone\":\"+57 300-111-2233\","
				+ "\"email\":\"Ana@Mail.com\"}", null);
		json(mvc, "/participants", "{\"eventId\":\"" + done + "\",\"firstName\":\"Ana\",\"lastName\":\"Lopez\",\"phone\":\"3001112233\",\"email\":\"ana@mail.com\"}", null);
		String code = JsonPath.read(ana, "$.qrCode");

		// por correo (sin importar mayúsculas) y por celular (con otro formato): solo la entrada vigente
		mvc.perform(post("/public/tickets/lookup").contentType(MediaType.APPLICATION_JSON).content("{\"contact\":\"  ANA@mail.com \"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].eventName").value("Abierto")).andExpect(jsonPath("$[0].qrCode").value(code))
				.andExpect(jsonPath("$[0].registrationType").value("Pre-inscripción")).andExpect(jsonPath("$[0].participantName").value("Ana Lopez"));
		mvc.perform(post("/public/tickets/lookup").contentType(MediaType.APPLICATION_JSON).content("{\"contact\":\"300 111 2233\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].qrCode").value(code));

		// sin coincidencias, contacto inválido y por código
		mvc.perform(post("/public/tickets/lookup").contentType(MediaType.APPLICATION_JSON).content("{\"contact\":\"nadie@mail.com\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
		mvc.perform(post("/public/tickets/lookup").contentType(MediaType.APPLICATION_JSON).content("{\"contact\":\"123\"}")).andExpect(status().isBadRequest());
		mvc.perform(get("/public/tickets/" + code.toLowerCase())).andExpect(status().isOk()).andExpect(jsonPath("$.attended").value(false));

		// límite de intentos (ya se usaron 5 de 6)
		mvc.perform(post("/public/tickets/lookup").contentType(MediaType.APPLICATION_JSON).content("{\"contact\":\"x@mail.com\"}")).andExpect(status().isOk());
		mvc.perform(post("/public/tickets/lookup").contentType(MediaType.APPLICATION_JSON).content("{\"contact\":\"x@mail.com\"}"))
				.andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.message").isString());
		assertEquals(1, 1);
	}
}
