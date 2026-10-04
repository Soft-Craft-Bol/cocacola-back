package com.cocacola.config;

import com.cocacola.commons.enums.Role;
import com.cocacola.domain.model.Product;
import com.cocacola.domain.model.User;
import com.cocacola.domain.repository.ProductRepository;
import com.cocacola.domain.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Carga inicial: catalogo de productos y usuarios demo (solo si las tablas estan vacias). */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(1)
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataSeeder implements ApplicationRunner {

    private final UserRepository users;
    private final ProductRepository products;
    private final com.cocacola.persistence.crud.ExperienceRepository experiences;
    private final PasswordEncoder encoder;

    @Override
    public void run(ApplicationArguments args) {
        seedProducts();
        seedExperiences();
        // Corrige el nombre del usuario demo si quedó sin tilde de una carga anterior
        users.findById("u3").filter(u -> "Monica Marketing".equals(u.getName())).ifPresent(u -> {
            u.setName("Mónica Marketing");
            users.save(u);
        });
        if (users.count() == 0) {
            users.save(user("u1", "Ana Administradora", "admin@cocacola.com", "admin123", Role.ADMIN));
            users.save(user("u2", "Oscar Organizador", "organizador@cocacola.com", "org123", Role.ORGANIZER));
            users.save(user("u3", "Mónica Marketing", "marketing@cocacola.com", "mkt123", Role.MARKETING));
            log.info("Usuarios demo creados (cambia sus contraseñas en produccion)");
        }
    }

    /** Catálogo inicial. Los productos que aún no tienen sabor ni presentación (cargas anteriores) se completan; los editados no se tocan. */
    private void seedProducts() {
        record Seed(String id, String name, String category, String flavor, String presentation) { }
        for (Seed seed : List.of(
                new Seed("p1", "Coca-Cola", "Cola", "Original", "Lata 330 ml"),
                new Seed("p2", "Coca-Cola", "Cola", "Sin azúcar", "Lata 330 ml"),
                new Seed("p3", "Coca-Cola", "Cola", "Zero", "Lata 330 ml"),
                new Seed("p4", "Sprite", "Lima-limón", "Lima-limón", "Botella 500 ml"),
                new Seed("p5", "Fanta", "Frutas", "Naranja", "Botella 500 ml"),
                new Seed("p6", "Powerade", "Isotónicos", "Mora azul", "Botella 600 ml"),
                new Seed("p7", "Dasani", "Agua", "Natural", "Botella 600 ml"))) {
            var existing = products.findById(seed.id());
            boolean legacy = existing.isPresent() && isBlank(existing.get().getFlavor()) && isBlank(existing.get().getPresentation());
            if (existing.isEmpty() || legacy) {
                Product p = existing.orElseGet(Product::new);
                p.setId(seed.id());
                p.setName(seed.name());
                p.setCategory(seed.category());
                p.setFlavor(seed.flavor());
                p.setPresentation(seed.presentation());
                if (p.getArchived() == null) p.setArchived(false);
                products.save(p);
            }
        }
        log.info("Catálogo de productos sincronizado");
    }

    /** Catálogo inicial de experiencias destacadas (solo si está vacío). */
    private void seedExperiences() {
        if (experiences.count() > 0) return;
        String[][] seed = {
                {"x1", "Zona de degustación", "Degustación o muestra", "Prueba de productos con calificación y encuesta rápida."},
                {"x2", "Cabina de fotos", "Fotografía / experiencia digital", "Fotografías con marco de la marca para compartir en redes."},
                {"x3", "Karaoke Coca-Cola", "Concurso o dinámica", "Concurso de karaoke con premios."},
                {"x4", "Canje de cupón", "Canje de beneficio o cupón", "Punto de canje de cupones y beneficios."},
                {"x5", "Estación de hidratación", "Degustación o muestra", "Entrega de bebidas para los participantes."},
                {"x6", "Reto deportivo 5K", "Concurso o dinámica", "Carrera con premios y entrega de kits."},
                {"x7", "Ruleta de premios", "Activación promocional", "Ruleta con premios y cupones."},
                {"x8", "Experiencia de producto", "Experiencia de producto", "Demostración guiada del producto."}};
        for (String[] s : seed) {
            var e = new com.cocacola.persistence.entity.ExperienceEntity();
            e.setId(s[0]);
            e.setName(s[1]);
            e.setCategory(s[2]);
            e.setDescription(s[3]);
            e.setArchived(false);
            experiences.save(e);
        }
        log.info("Catálogo de experiencias cargado");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private User user(String id, String name, String email, String password, Role role) {
        return new User(id, name, email, encoder.encode(password), role, true);
    }
}
