package com.cocacola;

import com.cocacola.domain.repository.ParticipantRepository;
import com.cocacola.persistence.entity.ParticipantEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:performance;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop", "app.seed.demo=false",
    "spring.jpa.properties.hibernate.generate_statistics=true"
})
@Transactional
class ParticipantPerformanceTests {
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory emf;
    @Autowired ParticipantRepository participants;
    @Autowired com.cocacola.domain.repository.EventRepository events;

    @Test
    void eventsAndTheirProductsUseOneQueryIncludingEventsWithoutProducts() {
        for (int i = 0; i < 30; i++) {
            em.persist(com.cocacola.persistence.entity.EventEntity.builder().id("event-" + i)
                .name("Event " + i).date(Instant.now())
                .productIds(i == 0 ? List.of() : List.of("product-a", "product-b")).build());
        }
        em.flush();
        em.clear();
        var stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        var result = events.findAll();
        assertEquals(30, result.size());
        assertEquals(58, result.stream().mapToInt(e -> e.getProductIds().size()).sum());
        result.forEach(e -> e.getExperienceIds().size());
        // eventos + productos en una consulta y experiencias en una sola subselect, sin importar cuántos eventos haya
        assertEquals(2, stats.getPrepareStatementCount());
        em.clear();
        stats.clear();
        assertEquals(2, events.findById("event-1").orElseThrow().getProductIds().size());
        assertEquals(2, stats.getPrepareStatementCount());
    }

    private void seed() {
        for (int i = 0; i < 65; i++) {
            em.persist(ParticipantEntity.builder().id("perf-" + i).eventId(i < 60 ? "event-a" : "event-b")
                .firstName(i == 0 ? "Ana%" : "Ana").lastName("Perez").email("ana" + i + "@example.com")
                .qrCode("PERF-" + i).registeredAt(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i))
                .preferences(List.of("product-a", "product-b")).build());
        }
        em.flush();
        em.clear();
    }

    @Test
    void pageFetchesOnlyRequestedParticipantsAndPreferencesWithConstantQueries() {
        seed();
        var stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        var page = participants.search("event-a", "ANA PEREZ", 1, 20);
        assertEquals(59, page.getTotalElements());
        assertEquals(20, page.getContent().size());
        assertEquals("perf-39", page.getContent().getFirst().getId());
        assertTrue(page.getContent().stream().allMatch(p -> p.getPreferences().size() == 2));
        assertEquals(3, stats.getPrepareStatementCount(), "page + count + preferences for page IDs");
        // No other participants should be materialized by the preference query.
        assertEquals(20, stats.getEntityLoadCount());
    }

    @Test
    void fullEventReadDoesNotQueryPreferencesPerParticipant() {
        seed();
        var stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        var result = participants.findByEventId("event-a");
        assertEquals(60, result.size());
        assertTrue(result.stream().allMatch(p -> p.getPreferences().size() == 2));
        assertEquals(2, stats.getPrepareStatementCount(), "participants + one collection subselect");
    }

    @Test
    void searchTreatsWildcardsAsLiteralAndKeepsEventScope() {
        seed();
        assertEquals(1, participants.search("event-a", "%", 0, 20).getTotalElements());
        assertEquals(5, participants.search("event-b", "", 0, 20).getTotalElements());
        assertEquals(0, participants.search("missing", "", 0, 20).getTotalElements());
    }
}
