package com.cocacola;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cocacola.domain.model.StoredImage;
import com.cocacola.domain.repository.ImageStorage;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Imagen del evento: subir, reemplazar, quitar y borrar el evento (Cloudinary simulado). */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:images;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"app.seed.demo=false"
})
class EventImageTests {

	@Autowired
	WebApplicationContext context;

	@Autowired
	@Qualifier("springSecurityFilterChain")
	Filter securityFilter;

	@MockitoBean
	ImageStorage storage;

	private String json(MockMvc mvc, String path, String body, String auth) throws Exception {
		return mvc.perform(post(path).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body))
				.andReturn().getResponse().getContentAsString();
	}

	@Test
	void imageLifecycle() throws Exception {
		reset(storage);
		MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilter).build();

		String login = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"admin@cocacola.com\",\"password\":\"admin123\"}")).andReturn().getResponse().getContentAsString();
		String auth = "Bearer " + JsonPath.read(login, "$.token");
		String orgLogin = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"organizador@cocacola.com\",\"password\":\"org123\"}")).andReturn().getResponse().getContentAsString();
		String orgAuth = "Bearer " + JsonPath.read(orgLogin, "$.token");

		String event = json(mvc, "/events", "{\"name\":\"Con imagen\",\"type\":\"Festival o concierto\","
				+ "\"date\":\"2026-11-01T15:00:00Z\",\"location\":\"Medellin\"}", auth);
		String id = JsonPath.read(event, "$.id");

		MockMultipartFile png = new MockMultipartFile("file", "foto.png", "image/png", new byte[] {1, 2, 3});

		// sin permiso y con formato invalido
		mvc.perform(multipart("/events/" + id + "/image").file(png).header("Authorization", orgAuth)).andExpect(status().isForbidden());
		mvc.perform(multipart("/events/" + id + "/image")
				.file(new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[] {1}))
				.header("Authorization", auth)).andExpect(status().isBadRequest());
		verify(storage, never()).upload(any(), any());

		// subir
		when(storage.upload(any(), eq("cocacola/events"))).thenReturn(new StoredImage("https://img/1.png", "cocacola/events/uno"));
		mvc.perform(multipart("/events/" + id + "/image").file(png).header("Authorization", auth))
				.andExpect(status().isOk()).andExpect(jsonPath("$.imageUrl").value("https://img/1.png"));

		// editar los datos conserva la imagen
		mvc.perform(put("/events/" + id).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Renombrado\",\"type\":\"Festival o concierto\",\"date\":\"2026-11-01T15:00:00Z\",\"location\":\"Cali\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.imageUrl").value("https://img/1.png"));

		// reemplazar: sube la nueva y elimina la anterior
		when(storage.upload(any(), eq("cocacola/events"))).thenReturn(new StoredImage("https://img/2.png", "cocacola/events/dos"));
		mvc.perform(multipart("/events/" + id + "/image").file(png).header("Authorization", auth))
				.andExpect(status().isOk()).andExpect(jsonPath("$.imageUrl").value("https://img/2.png"));
		verify(storage).delete("cocacola/events/uno");

		// quitar
		mvc.perform(delete("/events/" + id + "/image").header("Authorization", auth))
				.andExpect(status().isOk()).andExpect(jsonPath("$.imageUrl").doesNotExist());
		verify(storage).delete("cocacola/events/dos");

		// borrar evento con imagen: tambien elimina el archivo
		when(storage.upload(any(), eq("cocacola/events"))).thenReturn(new StoredImage("https://img/3.png", "cocacola/events/tres"));
		mvc.perform(multipart("/events/" + id + "/image").file(png).header("Authorization", auth)).andExpect(status().isOk());
		mvc.perform(delete("/events/" + id).header("Authorization", auth)).andExpect(status().isOk());
		verify(storage).delete("cocacola/events/tres");
	}
}
