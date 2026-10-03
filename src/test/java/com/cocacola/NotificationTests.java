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

/** Notificaciones al alcanzar metas y ante una encuesta con NPS bajo. */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:notif;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"app.seed.demo=false"
})
class NotificationTests {

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
	void goalsAndLowNps() throws Exception {
		MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();
		String auth = "Bearer " + JsonPath.read(json(mvc, "/auth/login", "{\"email\":\"admin@cocacola.com\",\"password\":\"admin123\"}", null), "$.token");

		String ev = json(mvc, "/events", "{\"name\":\"Meta\",\"type\":\"Festival o concierto\",\"date\":\"2026-11-01T15:00:00Z\","
				+ "\"location\":\"Cali\",\"expected\":2}", auth);
		String eventId = JsonPath.read(ev, "$.id");

		String p1 = json(mvc, "/participants", "{\"eventId\":\"" + eventId + "\",\"firstName\":\"A\",\"lastName\":\"Uno\",\"phone\":\"3001\",\"email\":\"a@mail.com\"}", null);
		json(mvc, "/participants", "{\"eventId\":\"" + eventId + "\",\"firstName\":\"B\",\"lastName\":\"Dos\",\"phone\":\"3002\",\"email\":\"b@mail.com\"}", null);

		// 1 de 2 registros (mitad) y 2 de 2 (meta)
		mvc.perform(get("/notifications/count").header("Authorization", auth)).andExpect(jsonPath("$.unread").value(2));

		String pid = JsonPath.read(p1, "$.id");
		mvc.perform(post("/participants/" + pid + "/checkin").header("Authorization", auth)).andExpect(status().isOk());
		// asistencia: 1 de 2 (mitad)
		mvc.perform(get("/notifications/count").header("Authorization", auth)).andExpect(jsonPath("$.unread").value(3));

		json(mvc, "/surveys", "{\"eventId\":\"" + eventId + "\",\"participantId\":\"" + pid + "\",\"organization\":2,\"service\":2,"
				+ "\"experiences\":3,\"products\":3,\"overall\":2,\"nps\":3}", auth);
		mvc.perform(get("/notifications/count").header("Authorization", auth)).andExpect(jsonPath("$.unread").value(4));

		String list = mvc.perform(get("/notifications").header("Authorization", auth)).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].type").value("NPS_BAJO")).andExpect(jsonPath("$[0].read").value(false))
				.andReturn().getResponse().getContentAsString();
		String first = JsonPath.read(list, "$[0].id");

		mvc.perform(post("/notifications/" + first + "/read").header("Authorization", auth)).andExpect(status().isOk());
		mvc.perform(get("/notifications/count").header("Authorization", auth)).andExpect(jsonPath("$.unread").value(3));
		mvc.perform(post("/notifications/read-all").header("Authorization", auth)).andExpect(status().isOk());
		mvc.perform(get("/notifications/count").header("Authorization", auth)).andExpect(jsonPath("$.unread").value(0));
		mvc.perform(get("/notifications").param("unread", "true").header("Authorization", auth)).andExpect(jsonPath("$.length()").value(0));
		mvc.perform(get("/notifications")).andExpect(status().isUnauthorized());
	}
}
