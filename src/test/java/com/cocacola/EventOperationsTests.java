package com.cocacola;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

import com.jayway.jsonpath.JsonPath;
import com.cocacola.domain.repository.EmailGateway;
import com.cocacola.domain.repository.WhatsAppGateway;
import com.cocacola.domain.repository.CrmGateway;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:event_operations;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop", "app.seed.enabled=true", "app.seed.demo=false",
    "app.automation.enabled=false", "notifications.email-enabled=false"
})
class EventOperationsTests {
    @Autowired WebApplicationContext context;
    @Autowired @Qualifier("springSecurityFilterChain") Filter security;
    @MockitoBean EmailGateway email;
    @MockitoBean WhatsAppGateway whatsapp;
    @MockitoBean CrmGateway crm;

    String token(MockMvc mvc, String email, String password) throws Exception {
        String body = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }
    String createEvent(MockMvc mvc, String admin, String name) throws Exception {
        String body = mvc.perform(post("/events").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"type\":\"Festival\",\"date\":\"2027-01-01T15:00:00Z\",\"location\":\"Cochabamba\",\"expected\":100}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }
    String settings(String organizer, int goal) {
        return "{\"organizerUserId\":\"" + organizer + "\",\"managerUserId\":\"u1\",\"registrationGoal\":" + goal
                + ",\"attendanceGoal\":2,\"conversionGoal\":1,\"lowNpsThreshold\":4}";
    }
    String register(MockMvc mvc, String event, String address) throws Exception {
        return mvc.perform(post("/participants").contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"" + event + "\",\"firstName\":\"Ana\",\"lastName\":\"Test\",\"phone\":\"70000000\",\"email\":\"" + address + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test void assignmentScopesOperationsAndNotesAndCanBeRevoked() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        String admin = token(mvc, "admin@cocacola.com", "admin123");
        String org = token(mvc, "organizador@cocacola.com", "org123");
        String marketing = token(mvc, "marketing@cocacola.com", "mkt123");
        String own = createEvent(mvc, admin, "Asignado");
        String other = createEvent(mvc, admin, "Privado de otro equipo");
        String url = "/events/" + own;
        mvc.perform(get(url + "/operations")).andExpect(status().isUnauthorized());
        mvc.perform(get(url + "/operations").header("Authorization", org)).andExpect(status().isForbidden());
        mvc.perform(put(url + "/operations").header("Authorization", org).contentType(MediaType.APPLICATION_JSON).content(settings("u2", 2))).andExpect(status().isForbidden());
        mvc.perform(put(url + "/operations").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(settings("u3", 2))).andExpect(status().isBadRequest());
        mvc.perform(put(url + "/operations").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(settings("u2", -1))).andExpect(status().isBadRequest());
        mvc.perform(put(url + "/operations").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(settings("u2", 2)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(true)).andExpect(jsonPath("$.organizerName").value("Oscar Organizador"));
        mvc.perform(get("/events").header("Authorization", org)).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(own))).andExpect(jsonPath("$[*].id", not(hasItem(other))));
        mvc.perform(get("/events/" + other).header("Authorization", org)).andExpect(status().isForbidden());
        mvc.perform(get(url).header("Authorization", org)).andExpect(status().isOk());
        mvc.perform(get(url + "/operations").header("Authorization", marketing)).andExpect(status().isOk());

        String person = register(mvc, own, "scope-own@example.com");
        String outsider = register(mvc, other, "scope-other@example.com");
        String pid = JsonPath.read(person, "$.id");
        String outsiderId = JsonPath.read(outsider, "$.id");
        mvc.perform(get("/participants").header("Authorization", org)).andExpect(jsonPath("$[*].id", not(hasItem(outsiderId))));
        mvc.perform(get("/participants/page").param("eventId", other).header("Authorization", org)).andExpect(status().isForbidden());
        mvc.perform(get("/participants/" + outsiderId).header("Authorization", org)).andExpect(status().isForbidden());
        mvc.perform(get("/participants/by-code/" + JsonPath.read(outsider, "$.qrCode")).header("Authorization", org)).andExpect(status().isForbidden());
        mvc.perform(post("/participants/" + outsiderId + "/checkin").header("Authorization", org)).andExpect(status().isForbidden());
        mvc.perform(post("/participants/" + pid + "/checkin").header("Authorization", org)).andExpect(status().isOk());
        mvc.perform(get("/metrics/events/" + other).header("Authorization", org)).andExpect(status().isForbidden());
        mvc.perform(get("/metrics/overview").header("Authorization", org)).andExpect(jsonPath("$.perEvent[*].id", not(hasItem(other))));
        for (String path : new String[]{"/activities", "/interactions", "/surveys"}) {
            mvc.perform(get(path).param("eventId", other).header("Authorization", org)).andExpect(status().isForbidden());
        }
        mvc.perform(post("/interactions").header("Authorization", org).contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"" + own + "\",\"participantId\":\"" + outsiderId + "\",\"type\":\"activity\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/surveys").header("Authorization", org).contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"" + own + "\",\"participantId\":\"" + outsiderId + "\",\"organization\":4,\"service\":4,\"experiences\":4,\"products\":4,\"overall\":4,\"nps\":8}"))
                .andExpect(status().isForbidden());

        mvc.perform(post(url + "/notes").header("Authorization", org).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Reponer muestras en el acceso\",\"authorName\":\"Falso\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authorName").value("Oscar Organizador")).andExpect(jsonPath("$.createdAt").isNotEmpty());
        mvc.perform(post(url + "/notes").header("Authorization", marketing).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"No permitido\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get(url + "/notes").header("Authorization", marketing)).andExpect(jsonPath("$[0].text").value("Reponer muestras en el acceso"));
        mvc.perform(post(url + "/notes").header("Authorization", org).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(url + "/operations").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(settings("u1", 2)))
                .andExpect(status().isOk());
        mvc.perform(get(url + "/notes").header("Authorization", org)).andExpect(status().isForbidden());
        mvc.perform(post("/participants/" + pid + "/checkout").header("Authorization", org)).andExpect(status().isForbidden());
        mvc.perform(get("/notifications").header("Authorization", org)).andExpect(jsonPath("$[*].eventId", not(hasItem(own))));
        mvc.perform(delete(url).header("Authorization", admin)).andExpect(status().isOk());
    }

    @Test void customGoalsDriveNotificationsAndPersist() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        String admin = token(mvc, "admin@cocacola.com", "admin123");
        String event = createEvent(mvc, admin, "Metas personalizadas");
        String url = "/events/" + event + "/operations";
        mvc.perform(get(url).header("Authorization", admin)).andExpect(jsonPath("$.settings.registrationGoal").value(100));
        mvc.perform(put(url).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(settings("u2", 2))).andExpect(status().isOk());
        String first = register(mvc, event, "goal-first@example.com");
        register(mvc, event, "goal-second@example.com");
        String pid = JsonPath.read(first, "$.id");
        mvc.perform(post("/participants/" + pid + "/checkin").header("Authorization", admin)).andExpect(status().isOk());
        String conversion = "{\"eventId\":\"" + event + "\",\"participantId\":\"" + pid + "\",\"type\":\"conversion\"}";
        mvc.perform(post("/interactions").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(conversion)).andExpect(status().isOk());
        mvc.perform(post("/interactions").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(conversion)).andExpect(status().isOk());
        mvc.perform(get(url).header("Authorization", admin)).andExpect(jsonPath("$.registered").value(2))
                .andExpect(jsonPath("$.attended").value(1)).andExpect(jsonPath("$.conversions").value(1));
        mvc.perform(get("/notifications").header("Authorization", admin).param("limit", "100"))
                .andExpect(jsonPath("$[?(@.eventId == '" + event + "' && @.type == 'META_REGISTRO')]", hasSize(2)))
                .andExpect(jsonPath("$[?(@.eventId == '" + event + "' && @.type == 'CONVERSIONES')]", hasSize(1)));
        mvc.perform(post("/surveys").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"" + event + "\",\"participantId\":\"" + pid + "\",\"organization\":4,\"service\":4,\"experiences\":4,\"products\":4,\"overall\":4,\"nps\":5}"))
                .andExpect(status().isOk());
        mvc.perform(get("/notifications").header("Authorization", admin).param("limit", "100"))
                .andExpect(jsonPath("$[?(@.eventId == '" + event + "' && @.type == 'NPS_BAJO')]", hasSize(0)));
        mvc.perform(put(url).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(settings("u2", 0))).andExpect(status().isOk());
        register(mvc, event, "goal-disabled@example.com");
        mvc.perform(get(url).header("Authorization", admin)).andExpect(jsonPath("$.settings.registrationGoal").value(0));
    }
}
