package com.cocacola;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cocacola.domain.model.EmailMessage;
import com.cocacola.domain.repository.EmailGateway;
import com.cocacola.utils.SmtpEmailGateway;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.Filter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Correo (Gmail): notificaciones del equipo, correo de prueba y reglas de configuración. */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:gmail;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"app.seed.demo=false",
		"notifications.email-to=equipo@mail.com"
})
class GmailNotificationTests {

	@Autowired
	WebApplicationContext context;

	@Autowired
	@Qualifier("springSecurityFilterChain")
	Filter securityFilter;

	@MockitoBean
	EmailGateway email;

	private String json(MockMvc mvc, String path, String body, String auth) throws Exception {
		var req = post(path).contentType(MediaType.APPLICATION_JSON).content(body);
		if (auth != null) req.header("Authorization", auth);
		return mvc.perform(req).andReturn().getResponse().getContentAsString();
	}

	@Test
	void teamNotificationsAndTestEmail() throws Exception {
		reset(email);
		when(email.isConfigured()).thenReturn(true);
		MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();
		String auth = "Bearer " + JsonPath.read(json(mvc, "/auth/login", "{\"email\":\"admin@cocacola.com\",\"password\":\"admin123\"}", null), "$.token");

		String eventId = JsonPath.read(json(mvc, "/events", "{\"name\":\"Evento Gmail\",\"type\":\"Festival o concierto\",\"date\":\"2026-11-01T15:00:00Z\","
				+ "\"location\":\"Cali\",\"expected\":2}", auth), "$.id");
		for (String n : new String[] {"a", "b"}) {
			json(mvc, "/participants", "{\"eventId\":\"" + eventId + "\",\"firstName\":\"" + n + "\",\"lastName\":\"X\",\"phone\":\"300\",\"email\":\"" + n + "@mail.com\"}", null);
		}

		// al llegar a la meta de registro (success) se avisa al equipo; la "mitad de la meta" (info) no genera correo
		ArgumentCaptor<EmailMessage> sent = ArgumentCaptor.forClass(EmailMessage.class);
		verify(email, timeout(5000).atLeast(3)).send(sent.capture());   // 2 confirmaciones + 1 aviso de meta
		List<EmailMessage> team = sent.getAllValues().stream().filter(m -> m.to().equals("equipo@mail.com")).toList();
		assertEquals(1, team.size());
		assertTrue(team.get(0).subject().contains("Meta de registro alcanzada"));
		assertTrue(team.get(0).html().contains("/eventos/" + eventId));

		// correo de prueba: al destinatario indicado o al del equipo
		mvc.perform(post("/integrations/email/test").param("to", "yo@gmail.com").header("Authorization", auth))
				.andExpect(status().isOk()).andExpect(jsonPath("$.to").value("yo@gmail.com"));
		mvc.perform(post("/integrations/email/test").header("Authorization", auth)).andExpect(jsonPath("$.to").value("equipo@mail.com"));

		// sin correo configurado responde con un mensaje claro
		reset(email);
		when(email.isConfigured()).thenReturn(false);
		mvc.perform(post("/integrations/email/test").header("Authorization", auth)).andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("MAIL_USERNAME")));
		verify(email, never()).send(any());
	}

	@Test
	void gatewayNeedsCredentials() {
		@SuppressWarnings("unchecked")
		ObjectProvider<JavaMailSender> present = org.mockito.Mockito.mock(ObjectProvider.class);
		when(present.getIfAvailable()).thenReturn(org.mockito.Mockito.mock(JavaMailSender.class));
		// host sin usuario o sin contraseña: no está configurado
		assertEquals(false, new SmtpEmailGateway(present, "smtp.gmail.com", "", "", "").isConfigured());
		assertEquals(false, new SmtpEmailGateway(present, "smtp.gmail.com", "yo@gmail.com", "", "").isConfigured());
		assertEquals(true, new SmtpEmailGateway(present, "smtp.gmail.com", "yo@gmail.com", "abcd efgh ijkl mnop", "").isConfigured());
		verify(present, times(1)).getIfAvailable();
	}
}
