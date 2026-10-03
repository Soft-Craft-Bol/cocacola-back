package com.cocacola;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Verifica que los datos de demostracion alimentan los indicadores. */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:demo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"app.seed.demo=true"
})
class DemoSeedTests {

	@Autowired
	WebApplicationContext context;

	@Autowired
	@Qualifier("springSecurityFilterChain")
	Filter securityFilter;

	@Test
	void demoDataFeedsDashboard() throws Exception {
		MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();
		String login = mvc.perform(MockMvcRequestBuilders.post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"marketing@cocacola.com\",\"password\":\"mkt123\"}"))
				.andReturn().getResponse().getContentAsString();
		String auth = "Bearer " + JsonPath.read(login, "$.token");

		mvc.perform(get("/metrics/overview").header("Authorization", auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.events").value(3))
				.andExpect(jsonPath("$.registered").value(183))
				.andExpect(jsonPath("$.perEvent.length()").value(3))
				.andExpect(jsonPath("$.attended").isNumber())
				.andExpect(jsonPath("$.returning").isNumber());
		mvc.perform(get("/metrics/events/e1").header("Authorization", auth))
				.andExpect(status().isOk()).andExpect(jsonPath("$.registered").value(90))
				.andExpect(jsonPath("$.funnel.length()").value(6));
	}
}
