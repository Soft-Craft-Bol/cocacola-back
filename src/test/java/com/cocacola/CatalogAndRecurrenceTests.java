package com.cocacola;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/** Catálogo de productos y experiencias, indicadores por sabor/presentación/categoría y regla de participante recurrente. */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:catalog;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"app.seed.demo=false"
})
class CatalogAndRecurrenceTests {

	@Autowired
	WebApplicationContext context;

	@Autowired
	@Qualifier("springSecurityFilterChain")
	Filter securityFilter;

	private MockMvc mvc() {
		return MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();
	}

	private String json(MockMvc mvc, String path, String body, String auth) throws Exception {
		var req = post(path).contentType(MediaType.APPLICATION_JSON).content(body);
		if (auth != null) req.header("Authorization", auth);
		return mvc.perform(req).andReturn().getResponse().getContentAsString();
	}

	private String token(MockMvc mvc, String email, String password) throws Exception {
		return "Bearer " + JsonPath.read(json(mvc, "/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}", null), "$.token");
	}

	private String event(MockMvc mvc, String auth, String name, String date, String extra) throws Exception {
		return JsonPath.read(json(mvc, "/events", "{\"name\":\"" + name + "\",\"type\":\"Festival o concierto\",\"date\":\"" + date
				+ "\",\"location\":\"Cali\"" + extra + "}", auth), "$.id");
	}

	private String register(MockMvc mvc, String eventId, String email, String phone, String prefs) throws Exception {
		return json(mvc, "/participants", "{\"eventId\":\"" + eventId + "\",\"firstName\":\"Persona\",\"lastName\":\"Prueba\",\"phone\":\"" + phone
				+ "\",\"email\":\"" + email + "\",\"preferences\":" + prefs + "}", null);
	}

	@Test
	void productsAndExperiencesCatalog() throws Exception {
		MockMvc mvc = mvc();
		String admin = token(mvc, "admin@cocacola.com", "admin123");
		String marketing = token(mvc, "marketing@cocacola.com", "mkt123");

		// el catálogo inicial ya trae sabor y presentación por separado
		mvc.perform(get("/products")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(7))
				.andExpect(jsonPath("$[?(@.id=='p1')].flavor").value("Original"))
				.andExpect(jsonPath("$[?(@.id=='p1')].presentation").value("Lata 330 ml"));

		// solo el administrador administra el catálogo
		String body = "{\"name\":\"Fanta\",\"category\":\"Frutas\",\"flavor\":\"Uva\",\"presentation\":\"Botella 1,5 L\",\"archived\":false}";
		mvc.perform(post("/products").header("Authorization", marketing).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
		String created = json(mvc, "/products", body, admin);
		String productId = JsonPath.read(created, "$.id");
		assertEquals("Uva", JsonPath.read(created, "$.flavor"));

		// no se repite el mismo producto con igual sabor y presentación (sí con otra presentación)
		mvc.perform(post("/products").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
		mvc.perform(post("/products").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
				.content(body.replace("Botella 1,5 L", "Lata 330 ml"))).andExpect(status().isOk());

		// editar y archivar; un producto archivado no se puede agregar a un evento nuevo
		mvc.perform(put("/products/" + productId).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
				.content(body.replace("\"archived\":false", "\"archived\":true"))).andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(true));
		mvc.perform(post("/events").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\",\"type\":\"Festival o concierto\","
				+ "\"date\":\"2026-12-01T15:00:00Z\",\"location\":\"Cali\",\"productIds\":[\"" + productId + "\"]}")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("archivados")));

		// experiencias: catálogo propio
		mvc.perform(get("/experiences")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(8));
		String exBody = "{\"name\":\"Laboratorio de sabores\",\"category\":\"Experiencia de producto\",\"description\":\"Mezcla tu sabor\",\"archived\":false}";
		mvc.perform(post("/experiences").header("Authorization", marketing).contentType(MediaType.APPLICATION_JSON).content(exBody)).andExpect(status().isForbidden());
		String experienceId = JsonPath.read(json(mvc, "/experiences", exBody, admin), "$.id");
		mvc.perform(post("/experiences").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(exBody)).andExpect(status().isConflict());

		// un evento destaca experiencias del catálogo y las devuelve
		String eventId = event(mvc, admin, "Con experiencias", "2026-12-01T15:00:00Z", ",\"experienceIds\":[\"x1\",\"" + experienceId + "\"]");
		mvc.perform(get("/events/" + eventId)).andExpect(jsonPath("$.experienceIds.length()").value(2));
		mvc.perform(put("/experiences/" + experienceId).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
				.content(exBody.replace("\"archived\":false", "\"archived\":true"))).andExpect(status().isOk());
		// ya estaba en el evento: se conserva; en un evento nuevo no se puede usar
		mvc.perform(post("/events").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Y\",\"type\":\"Festival o concierto\","
				+ "\"date\":\"2026-12-01T15:00:00Z\",\"location\":\"Cali\",\"experienceIds\":[\"" + experienceId + "\"]}")).andExpect(status().isBadRequest());
	}

	@Test
	void interestByFlavorPresentationAndCategory() throws Exception {
		MockMvc mvc = mvc();
		String admin = token(mvc, "admin@cocacola.com", "admin123");
		String eventId = event(mvc, admin, "Interes", "2026-12-02T15:00:00Z", ",\"productIds\":[\"p1\",\"p2\",\"p4\"]");

		register(mvc, eventId, "a@mail.com", "3001000001", "[\"p1\",\"p2\"]");   // Cola, Original + Sin azúcar, Lata
		register(mvc, eventId, "b@mail.com", "3001000002", "[\"p1\"]");          // Cola, Original, Lata
		register(mvc, eventId, "c@mail.com", "3001000003", "[\"p4\"]");          // Lima-limón, Botella 500 ml

		String m = mvc.perform(get("/metrics/events/" + eventId).header("Authorization", admin)).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		List<Map<String, Object>> category = JsonPath.read(m, "$.interestByCategory");
		List<Map<String, Object>> flavor = JsonPath.read(m, "$.interestByFlavor");
		List<Map<String, Object>> presentation = JsonPath.read(m, "$.interestByPresentation");
		assertEquals(2, ((Number) category.stream().filter(x -> x.get("name").equals("Cola")).findFirst().orElseThrow().get("value")).intValue());
		assertEquals(1, ((Number) category.stream().filter(x -> x.get("name").equals("Lima-limón")).findFirst().orElseThrow().get("value")).intValue());
		assertEquals(2, ((Number) flavor.stream().filter(x -> x.get("name").equals("Original")).findFirst().orElseThrow().get("value")).intValue());
		assertEquals(1, ((Number) flavor.stream().filter(x -> x.get("name").equals("Sin azúcar")).findFirst().orElseThrow().get("value")).intValue());
		assertEquals(2, ((Number) presentation.stream().filter(x -> x.get("name").equals("Lata 330 ml")).findFirst().orElseThrow().get("value")).intValue());
		assertTrue(presentation.stream().anyMatch(x -> x.get("name").equals("Botella 500 ml")));
		mvc.perform(get("/metrics/overview").header("Authorization", admin)).andExpect(jsonPath("$.interestByFlavor").isArray());
	}

	@Test
	void returningMeansAttendedBefore() throws Exception {
		MockMvc mvc = mvc();
		String admin = token(mvc, "admin@cocacola.com", "admin123");
		String e1 = event(mvc, admin, "Uno", "2026-12-03T15:00:00Z", "");
		String e2 = event(mvc, admin, "Dos", "2026-12-04T15:00:00Z", "");
		String e3 = event(mvc, admin, "Tres", "2026-12-05T15:00:00Z", "");

		// inscribirse a varios eventos sin asistir NO vuelve recurrente a nadie
		String a1 = register(mvc, e1, "ana@mail.com", "3001112233", "[]");
		String a2 = register(mvc, e2, "ana@mail.com", "3001112233", "[]");
		assertEquals(false, JsonPath.read(a1, "$.isReturning"));
		assertEquals(false, JsonPath.read(a2, "$.isReturning"));

		// asiste al primero; en el segundo ya es recurrente al ingresar (se recalcula en el check-in)
		mvc.perform(post("/participants/" + JsonPath.read(a1, "$.id") + "/checkin").header("Authorization", admin))
				.andExpect(status().isOk()).andExpect(jsonPath("$.isReturning").value(false));
		mvc.perform(post("/participants/" + JsonPath.read(a2, "$.id") + "/checkin").header("Authorization", admin))
				.andExpect(status().isOk()).andExpect(jsonPath("$.isReturning").value(true));

		// un tercer evento: la reconoce por correo, y también por celular aunque use otro correo
		assertEquals(true, JsonPath.read(register(mvc, e3, "ANA@mail.com", "000", "[]"), "$.isReturning"));
		assertEquals(true, JsonPath.read(register(mvc, e3, "otro@mail.com", "+57 300-111-2233", "[]"), "$.isReturning"));
		assertEquals(false, JsonPath.read(register(mvc, e3, "nueva@mail.com", "3109998877", "[]"), "$.isReturning"));
	}
}
