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
    private final PasswordEncoder encoder;

    @Override
    public void run(ApplicationArguments args) {
        // Catalogo: se actualiza en cada arranque para mantener los nombres corregidos
        {
            List.of(
                    new Product("p1", "Coca-Cola Original", "Cola"),
                    new Product("p2", "Coca-Cola Sin Azúcar", "Cola"),
                    new Product("p3", "Coca-Cola Zero", "Cola"),
                    new Product("p4", "Sprite", "Lima-limón"),
                    new Product("p5", "Fanta Naranja", "Frutas"),
                    new Product("p6", "Powerade", "Isotónicos"),
                    new Product("p7", "Dasani", "Agua")).forEach(products::save);
            log.info("Catálogo de productos sincronizado");
        }
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

    private User user(String id, String name, String email, String password, Role role) {
        return new User(id, name, email, encoder.encode(password), role, true);
    }
}
