package com.cocacola;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Qualifier;

/** Flujo completo contra la API (sin levantar Tomcat) usando la base H2 en memoria. */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:cocacola;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"app.seed.demo=false"
})
class ApiFlowTests {

	@Autowired
	WebApplicationContext context;

	@Autowired
	@Qualifier("springSecurityFilterChain")
	Filter securityFilter;

	private MockMvc mvc() {
		return MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();
	}

	private String read(String json, String path) {
		return JsonPath.read(json, path).toString();
	}

	@Test
	void fullEventFlow() throws Exception {
		MockMvc mvc = mvc();

		// sin token: 401
		mvc.perform(get("/events")).andExpect(status().isUnauthorized());

		// login admin
		String login = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"admin@cocacola.com\",\"password\":\"admin123\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("admin"))
				.andReturn().getResponse().getContentAsString();
		String auth = "Bearer " + read(login, "$.token");

		mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"admin@cocacola.com\",\"password\":\"mala\"}"))
				.andExpect(status().isUnauthorized());

		// catalogo publico
		mvc.perform(get("/products")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(7));

		// crear evento
		String event = mvc.perform(post("/events").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Evento Test\",\"type\":\"Festival o concierto\",\"date\":\"2026-11-01T15:00:00Z\","
						+ "\"location\":\"Medellin\",\"campaign\":\"Verano Zero\",\"productIds\":[\"p1\",\"p3\"]}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("planned"))
				.andReturn().getResponse().getContentAsString();
		String eventId = read(event, "$.id");

		// registro publico (sin token), con consentimiento
		String reg = mvc.perform(post("/participants").contentType(MediaType.APPLICATION_JSON)
				.content("{\"eventId\":\"" + eventId + "\",\"firstName\":\"Ana\",\"lastName\":\"Lopez\",\"phone\":\"3001234567\","
						+ "\"email\":\"Ana@Mail.com\",\"city\":\"Medellin\",\"ageRange\":\"25-34\",\"preferences\":[\"p1\"],\"consent\":true,\"source\":\"Codigo QR\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.isReturning").value(false))
				.andExpect(jsonPath("$.email").value("ana@mail.com"))
				.andExpect(jsonPath("$.registeredAt").isString())
				.andReturn().getResponse().getContentAsString();
		String pid = read(reg, "$.id");
		String qr = read(reg, "$.qrCode");

        mvc.perform(get("/participants/page").param("eventId", eventId))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/participants/page").header("Authorization", auth)
                .param("eventId", eventId).param("search", "ANA LOPEZ").param("size", "20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(pid))
                .andExpect(jsonPath("$.content[0].preferences[0]").value("p1"));

		// correo duplicado en el mismo evento: 409
		mvc.perform(post("/participants").contentType(MediaType.APPLICATION_JSON)
				.content("{\"eventId\":\"" + eventId + "\",\"firstName\":\"Ana\",\"lastName\":\"Lopez\",\"phone\":\"3001234567\",\"email\":\"ana@mail.com\"}"))
				.andExpect(status().isConflict());

		// interactuar antes del check-in: 409
		mvc.perform(post("/interactions").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("{\"eventId\":\"" + eventId + "\",\"participantId\":\"" + pid + "\",\"type\":\"activity\"}"))
				.andExpect(status().isConflict());

		// check-in por codigo QR
		mvc.perform(get("/participants/by-code/" + qr.toLowerCase()).header("Authorization", auth))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(pid));
		mvc.perform(post("/participants/" + pid + "/checkin").header("Authorization", auth))
				.andExpect(status().isOk()).andExpect(jsonPath("$.checkedInAt").isNotEmpty());
		mvc.perform(post("/participants/" + pid + "/checkin").header("Authorization", auth))
				.andExpect(status().isConflict());

		// actividad + degustacion + canje + conversion
		String act = mvc.perform(post("/activities").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("{\"eventId\":\"" + eventId + "\",\"name\":\"Zona de degustacion\",\"type\":\"tasting\"}"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String actId = read(act, "$.id");
		for (String body : new String[] {
				"\"type\":\"tasting\",\"productId\":\"p1\",\"rating\":5,\"wouldBuy\":true,\"wantsPromos\":true",
				"\"type\":\"redeem\"", "\"type\":\"conversion\"" }) {
			mvc.perform(post("/interactions").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
					.content("{\"eventId\":\"" + eventId + "\",\"participantId\":\"" + pid + "\",\"activityId\":\"" + actId + "\"," + body + "}"))
					.andExpect(status().isOk());
		}

		// encuesta (una sola por participante)
		String survey = "{\"eventId\":\"" + eventId + "\",\"participantId\":\"" + pid
				+ "\",\"organization\":5,\"service\":4,\"experiences\":5,\"products\":4,\"overall\":5,\"nps\":10}";
		mvc.perform(post("/surveys").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(survey))
				.andExpect(status().isOk());
		mvc.perform(post("/surveys").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(survey))
				.andExpect(status().isConflict());

		// metricas del evento
		mvc.perform(get("/metrics/events/" + eventId).header("Authorization", auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.registered").value(1))
				.andExpect(jsonPath("$.attended").value(1))
				.andExpect(jsonPath("$.attendanceRate").value(100.0))
				.andExpect(jsonPath("$.productInteractions").value(1))
				.andExpect(jsonPath("$.conversions").value(1))
				.andExpect(jsonPath("$.consents").value(1))
				.andExpect(jsonPath("$.satisfaction").value(4.6))
				.andExpect(jsonPath("$.nps").value(100.0))
				.andExpect(jsonPath("$.productInterest[0].name").value("Coca-Cola Original"))
				.andExpect(jsonPath("$.funnel.length()").value(6));

		mvc.perform(get("/metrics/overview").header("Authorization", auth))
				.andExpect(status().isOk()).andExpect(jsonPath("$.events").value(1))
				.andExpect(jsonPath("$.evolution[0].Asistentes").value(1));

		// segundo evento: la misma persona pasa a ser recurrente
		String event2 = mvc.perform(post("/events").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Evento 2\",\"type\":\"Evento deportivo\",\"date\":\"2026-12-01T15:00:00Z\",\"location\":\"Cali\"}"))
				.andReturn().getResponse().getContentAsString();
		mvc.perform(post("/participants").contentType(MediaType.APPLICATION_JSON)
				.content("{\"eventId\":\"" + read(event2, "$.id") + "\",\"firstName\":\"Ana\",\"lastName\":\"Lopez\",\"phone\":\"3001234567\",\"email\":\"ana@mail.com\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.isReturning").value(true));

		// permisos: organizador no puede crear usuarios ni eventos
		String org = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"organizador@cocacola.com\",\"password\":\"org123\"}")).andReturn().getResponse().getContentAsString();
		String orgAuth = "Bearer " + read(org, "$.token");
		mvc.perform(get("/users").header("Authorization", orgAuth)).andExpect(status().isForbidden());
		mvc.perform(delete("/events/" + eventId).header("Authorization", orgAuth)).andExpect(status().isForbidden());

		// borrar evento elimina sus datos
		mvc.perform(delete("/events/" + eventId).header("Authorization", auth)).andExpect(status().isOk());
		mvc.perform(get("/events/" + eventId)).andExpect(status().isNotFound());
		mvc.perform(get("/participants/" + pid).header("Authorization", auth)).andExpect(status().isNotFound());
	}
}
