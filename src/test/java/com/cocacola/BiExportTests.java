package com.cocacola;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.Filter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Datos para Power BI: acceso (JWT o clave de API), catálogo, CSV y ausencia de datos personales. */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:bi;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"app.seed.demo=true",
		"bi.api-key=test-bi-key"
})
class BiExportTests {

	@Autowired
	WebApplicationContext context;

	@Autowired
	@Qualifier("springSecurityFilterChain")
	Filter securityFilter;

	private String token(MockMvc mvc, String email, String password) throws Exception {
		String login = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")).andReturn().getResponse().getContentAsString();
		return "Bearer " + JsonPath.read(login, "$.token");
	}

	@Test
	void biEndpoints() throws Exception {
		MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();

		// sin credenciales, clave incorrecta u organizador: no pasa
		mvc.perform(get("/bi")).andExpect(status().isUnauthorized());
		mvc.perform(get("/bi/eventos").header("X-API-Key", "otra")).andExpect(status().isUnauthorized());
		mvc.perform(get("/bi").header("Authorization", token(mvc, "organizador@cocacola.com", "org123"))).andExpect(status().isForbidden());

		// marketing (JWT) y clave de API (cabecera o parametro)
		String marketing = token(mvc, "marketing@cocacola.com", "mkt123");
		mvc.perform(get("/bi").header("Authorization", marketing)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(8));
		mvc.perform(get("/bi/eventos").header("X-API-Key", "test-bi-key")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
		mvc.perform(get("/bi/productos").param("key", "test-bi-key")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(7));
		mvc.perform(get("/bi/tabla-inexistente").header("X-API-Key", "test-bi-key")).andExpect(status().isNotFound());
		mvc.perform(get("/ruta-que-no-existe").header("Authorization", marketing)).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Ruta no encontrada"));

		// las columnas del catálogo coinciden con las filas de cada tabla
		String catalog = mvc.perform(get("/bi").header("Authorization", marketing)).andReturn().getResponse().getContentAsString();
		List<String> names = JsonPath.read(catalog, "$[*].name");
		for (String table : names) {
			List<String> expected = JsonPath.read(catalog, "$[?(@.name=='" + table + "')].columns[*].name");
			String body = mvc.perform(get("/bi/" + table).header("Authorization", marketing)).andReturn().getResponse().getContentAsString();
			List<Map<String, Object>> rows = JsonPath.read(body, "$");
			assertFalse(rows.isEmpty(), table);
			assertEquals(expected, List.copyOf(rows.get(0).keySet()), table);
		}

		// participantes: sin datos personales, con persona anónima y nivel de interacción
		String people = mvc.perform(get("/bi/participantes").header("Authorization", marketing)).andReturn().getResponse().getContentAsString();
		assertFalse(people.contains("@mail.com"));
		assertFalse(people.contains("\"email\""));
		assertEquals(183, ((List<?>) JsonPath.read(people, "$")).size());
		mvc.perform(get("/bi/participantes").header("Authorization", marketing))
				.andExpect(jsonPath("$[0].persona_id").isString())
				.andExpect(jsonPath("$[0].nivel_interaccion").isNumber());

		// CSV
		String csv = mvc.perform(get("/bi/eventos").param("format", "csv").header("X-API-Key", "test-bi-key"))
				.andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/csv"))
				.andReturn().getResponse().getContentAsString();
		assertTrue(csv.startsWith("evento_id,nombre,tipo,fecha,lugar"));
		assertEquals(4, csv.trim().split("\\r?\\n").length);
	}
}
