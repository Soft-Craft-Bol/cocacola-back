package com.cocacola;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

/** Emisión y canje de cupones contra la API, con H2 en memoria. */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:coupons;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"app.seed.demo=false"
})
class CouponTests {

	@Autowired
	WebApplicationContext context;

	@Autowired
	@Qualifier("springSecurityFilterChain")
	Filter securityFilter;

	@Test
	void issueRedeemAndDeleteCoupons() throws Exception {
		MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();

		String login = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"admin@cocacola.com\",\"password\":\"admin123\"}"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String auth = "Bearer " + JsonPath.read(login, "$.token").toString();

		String event = mvc.perform(post("/events").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Evento cupones\",\"type\":\"Festival o concierto\",\"date\":\"2026-11-01T15:00:00Z\","
						+ "\"location\":\"Medellin\",\"productIds\":[\"p1\"]}"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String eventId = JsonPath.read(event, "$.id");

		String reg = mvc.perform(post("/participants").contentType(MediaType.APPLICATION_JSON)
				.content("{\"eventId\":\"" + eventId + "\",\"firstName\":\"Luis\",\"lastName\":\"Mora\",\"phone\":\"3007654321\","
						+ "\"email\":\"luis@mail.com\",\"consent\":true}"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String pid = JsonPath.read(reg, "$.id");

		// no se emite un cupón antes del check-in
		String issueBody = "{\"eventId\":\"" + eventId + "\",\"participantId\":\"" + pid + "\",\"benefit\":\"Gaseosa gratis\"}";
		mvc.perform(post("/coupons").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(issueBody))
				.andExpect(status().isConflict());

		mvc.perform(post("/participants/" + pid + "/checkin").header("Authorization", auth)).andExpect(status().isOk());

		String coupon = mvc.perform(post("/coupons").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(issueBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("issued"))
				.andExpect(jsonPath("$.participantName").value("Luis Mora"))
				.andExpect(jsonPath("$.consent").value(true))
				.andReturn().getResponse().getContentAsString();
		String couponId = JsonPath.read(coupon, "$.id");
		String code = JsonPath.read(coupon, "$.code");

		// vigencia pasada: rechazada al emitir
		mvc.perform(post("/coupons").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("{\"eventId\":\"" + eventId + "\",\"participantId\":\"" + pid + "\",\"benefit\":\"Vencido\","
						+ "\"validUntil\":\"2020-01-01T00:00:00Z\"}"))
				.andExpect(status().isBadRequest());

		// el código solo sirve para su titular
		mvc.perform(post("/coupons/redeem").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("{\"eventId\":\"" + eventId + "\",\"participantId\":\"otro\",\"code\":\"" + code + "\"}"))
				.andExpect(status().isBadRequest());

		// canje con código en minúsculas y espacios
		mvc.perform(post("/coupons/redeem").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("{\"eventId\":\"" + eventId + "\",\"participantId\":\"" + pid + "\",\"code\":\" " + code.toLowerCase() + " \"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("redeemed"))
				.andExpect(jsonPath("$.redeemInteractionId").isNotEmpty());

		// un cupón solo se canjea una vez
		mvc.perform(post("/coupons/redeem").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("{\"eventId\":\"" + eventId + "\",\"participantId\":\"" + pid + "\",\"code\":\"" + code + "\"}"))
				.andExpect(status().isConflict());

		// el canje queda como interacción de tipo redeem del participante
		mvc.perform(get("/interactions").param("participantId", pid).header("Authorization", auth))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].type").value("redeem"));

		// listado del evento con el participante
		mvc.perform(get("/coupons").param("eventId", eventId).header("Authorization", auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].status").value("redeemed"))
				.andExpect(jsonPath("$[0].participantEmail").value("luis@mail.com"));

		// un cupón canjeado no se elimina
		mvc.perform(delete("/coupons/" + couponId).header("Authorization", auth)).andExpect(status().isConflict());

		// un cupón no emitido sí se puede eliminar
		String second = mvc.perform(post("/coupons").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(issueBody))
				.andReturn().getResponse().getContentAsString();
		mvc.perform(delete("/coupons/" + JsonPath.read(second, "$.id")).header("Authorization", auth)).andExpect(status().isOk());

		// borrar el evento borra sus cupones
		mvc.perform(delete("/events/" + eventId).header("Authorization", auth)).andExpect(status().isOk());
		mvc.perform(get("/coupons").param("eventId", eventId).header("Authorization", auth))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
	}
}
