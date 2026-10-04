package com.cocacola;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cocacola.config.DemoDataSeeder;
import com.cocacola.domain.repository.EventRepository;
import com.cocacola.domain.repository.ParticipantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Los datos demo nuevos son de Cochabamba, y los cargados antes con ciudades de Colombia se migran sin perder identidades. */
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:bolivia;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"app.seed.demo=true"
})
class BoliviaLocalizationTests {

	@Autowired DemoDataSeeder seeder;
	@Autowired EventRepository events;
	@Autowired ParticipantRepository participants;

	@Test
	void demoDataIsFromCochabambaAndLegacyDataIsMigrated() {
		assertTrue(events.findById("e1").orElseThrow().getLocation().contains("Cochabamba"));
		assertTrue(participants.findAll().stream().allMatch(p -> p.getPhone().matches("7[0-9]{7}")));
		assertTrue(participants.findAll().stream().anyMatch(p -> "Cochabamba".equals(p.getCity())));

		// datos antiguos (Colombia): se vuelven a dejar así y se migran al volver a arrancar
		var e1 = events.findById("e1").orElseThrow();
		e1.setLocation("Parque Norte, Medellín");
		events.save(e1);
		var legacy = participants.findAll().stream().filter(p -> p.getId().startsWith("dp")).toList();
		legacy.forEach(p -> { p.setCity("Cali"); p.setPhone("3001234567"); });
		participants.saveAll(legacy);
		long distinctEmails = legacy.stream().map(p -> p.getEmail()).distinct().count();

		seeder.run(null);

		assertEquals("Parque Lincoln, Cochabamba", events.findById("e1").orElseThrow().getLocation());
		var migrated = participants.findAll().stream().filter(p -> p.getId().startsWith("dp")).toList();
		assertTrue(migrated.stream().allMatch(p -> p.getPhone().matches("7[0-9]{7}") && !"Cali".equals(p.getCity())));
		assertEquals(distinctEmails, migrated.stream().map(p -> p.getEmail()).distinct().count());
	}
}
