package com.cocacola;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cocacola.domain.model.EmailMessage;
import com.cocacola.domain.repository.CrmGateway;
import com.cocacola.domain.repository.EmailGateway;
import com.cocacola.domain.repository.WhatsAppGateway;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.Filter;
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

/** Correo, WhatsApp, CRM y encuesta pública (con las pasarelas simuladas). */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:comms;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"app.seed.demo=false"
})
class CommunicationTests {

	@Autowired
	WebApplicationContext context;

	@Autowired
	@Qualifier("springSecurityFilterChain")
	Filter securityFilter;

	@MockitoBean
	EmailGateway email;

	@MockitoBean
	WhatsAppGateway whatsapp;

	@MockitoBean
	CrmGateway crm;

	private String json(MockMvc mvc, String path, String body, String auth) throws Exception {
		var req = post(path).contentType(MediaType.APPLICATION_JSON).content(body);
		if (auth != null) req.header("Authorization", auth);
		return mvc.perform(req).andReturn().getResponse().getContentAsString();
	}

	@Test
	void communicationsCrmAndPublicSurvey() throws Exception {
		reset(email, whatsapp, crm);
		when(email.isConfigured()).thenReturn(true);
		when(whatsapp.isConfigured()).thenReturn(true);
		when(crm.isConfigured()).thenReturn(true);

		MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();
		String admin = "Bearer " + JsonPath.read(json(mvc, "/auth/login", "{\"email\":\"admin@cocacola.com\",\"password\":\"admin123\"}", null), "$.token");
		String org = "Bearer " + JsonPath.read(json(mvc, "/auth/login", "{\"email\":\"organizador@cocacola.com\",\"password\":\"org123\"}", null), "$.token");

		mvc.perform(get("/integrations/status").header("Authorization", org)).andExpect(status().isForbidden());
		mvc.perform(get("/integrations/status").header("Authorization", admin)).andExpect(jsonPath("$.email").value(true))
				.andExpect(jsonPath("$.crm").value(true)).andExpect(jsonPath("$.ai").value(false));

		String eventId = JsonPath.read(json(mvc, "/events", "{\"name\":\"Gran Evento\",\"type\":\"Festival o concierto\","
				+ "\"date\":\"2026-11-01T15:00:00Z\",\"location\":\"Cali\"}", admin), "$.id");

		// registro con consentimiento: confirmación automática por correo y WhatsApp + envío al CRM
		String ana = json(mvc, "/participants", "{\"eventId\":\"" + eventId + "\",\"firstName\":\"Ana\",\"lastName\":\"Lopez\",\"phone\":\"3001112233\","
				+ "\"email\":\"ana@mail.com\",\"consent\":true}", null);
		String luis = json(mvc, "/participants", "{\"eventId\":\"" + eventId + "\",\"firstName\":\"Luis\",\"lastName\":\"Paz\",\"phone\":\"3004445566\","
				+ "\"email\":\"luis@mail.com\",\"consent\":false}", null);
		ArgumentCaptor<EmailMessage> mail = ArgumentCaptor.forClass(EmailMessage.class);
		verify(email, timeout(5000).times(2)).send(mail.capture());
		assertTrue(mail.getAllValues().stream().allMatch(m -> m.subject().contains("Gran Evento")));
		EmailMessage first = mail.getAllValues().get(0);
		assertTrue(first.inlineImages().containsKey("qr"));
		assertTrue(first.inlineImages().get("qr").length > 100);
		verify(whatsapp, timeout(5000).times(2)).sendText(any(), contains("Tu código de ingreso"));
		verify(crm, timeout(5000).times(1)).push(any());   // solo quien dio su consentimiento

		// reenvío de confirmación: ya se envió, no se duplica
		String r1 = json(mvc, "/communications/send", "{\"eventId\":\"" + eventId + "\",\"type\":\"CONFIRMATION\",\"channel\":\"EMAIL\"}", admin);
		assertEquals(0, (int) JsonPath.read(r1, "$.sent"));
		assertEquals(2, (int) JsonPath.read(r1, "$.skipped"));

		// agradecimiento: solo asistentes con consentimiento
		String anaId = JsonPath.read(ana, "$.id");
		String luisId = JsonPath.read(luis, "$.id");
		mvc.perform(post("/participants/" + anaId + "/checkin").header("Authorization", admin)).andExpect(status().isOk());
		mvc.perform(post("/participants/" + luisId + "/checkin").header("Authorization", admin)).andExpect(status().isOk());
		reset(email);
		when(email.isConfigured()).thenReturn(true);
		String r2 = json(mvc, "/communications/send", "{\"eventId\":\"" + eventId + "\",\"type\":\"THANKS\",\"channel\":\"EMAIL\",\"audience\":\"ATTENDED\"}", admin);
		assertEquals(1, (int) JsonPath.read(r2, "$.total"));
		assertEquals(1, (int) JsonPath.read(r2, "$.sent"));
		ArgumentCaptor<EmailMessage> thanks = ArgumentCaptor.forClass(EmailMessage.class);
		verify(email, times(1)).send(thanks.capture());
		assertEquals("ana@mail.com", thanks.getValue().to());
		assertTrue(thanks.getValue().html().contains("/encuesta/"));

		// el historial registra los envíos
		mvc.perform(get("/communications/log").param("eventId", eventId).header("Authorization", admin))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(5));

		// CRM: CSV con consentimiento únicamente
		String csv = mvc.perform(get("/crm/contacts").param("eventId", eventId).param("format", "csv").header("Authorization", admin))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertTrue(csv.startsWith("nombre,apellido,correo,celular"));
		assertTrue(csv.contains("ana@mail.com"));
		assertFalse(csv.contains("luis@mail.com"));
		mvc.perform(post("/crm/sync").param("eventId", eventId).header("Authorization", admin))
				.andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.sent").value(1));

		// encuesta pública con el código del QR
		String code = JsonPath.read(ana, "$.qrCode");
		mvc.perform(get("/public/survey/" + code)).andExpect(status().isOk()).andExpect(jsonPath("$.attended").value(true))
				.andExpect(jsonPath("$.answered").value(false)).andExpect(jsonPath("$.participantName").value("Ana"));
		String answers = "{\"organization\":5,\"service\":4,\"experiences\":5,\"products\":4,\"overall\":5,\"nps\":10}";
		mvc.perform(post("/public/survey/" + code).contentType(MediaType.APPLICATION_JSON).content(answers)).andExpect(status().isOk());
		mvc.perform(post("/public/survey/" + code).contentType(MediaType.APPLICATION_JSON).content(answers)).andExpect(status().isConflict());
		mvc.perform(get("/public/survey/NO-EXISTE")).andExpect(status().isNotFound());

		// sin canal configurado no se envía nada
		reset(email);
		when(email.isConfigured()).thenReturn(false);
		String r3 = json(mvc, "/communications/send", "{\"eventId\":\"" + eventId + "\",\"type\":\"REMINDER\",\"channel\":\"EMAIL\"}", admin);
		assertEquals(0, (int) JsonPath.read(r3, "$.sent"));
		assertTrue(((String) JsonPath.read(r3, "$.note")).contains("no está configurado"));
		verify(email, never()).send(any());
		verify(whatsapp, times(2)).sendText(any(), any());
		verify(crm, times(2)).push(any());
	}
}
