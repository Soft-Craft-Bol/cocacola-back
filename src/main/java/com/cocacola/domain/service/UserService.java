package com.cocacola.domain.service;

import com.cocacola.domain.helpers.ConflictException;
import com.cocacola.domain.helpers.IdGenerator;
import com.cocacola.domain.helpers.NotFoundException;
import com.cocacola.domain.model.User;
import com.cocacola.domain.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final com.cocacola.persistence.crud.EventOperationsRepository operations;

    public List<User> list() {
        return users.findAll();
    }

    public User create(User data, String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("La contraseña es obligatoria");
        }
        if (users.existsByEmail(data.getEmail())) {
            throw new ConflictException("Ya existe un usuario con ese correo");
        }
        data.setId(IdGenerator.newId());
        data.setPassword(encoder.encode(rawPassword));
        return users.save(data);
    }

    public User update(String id, User data, String rawPassword) {
        User user = users.findById(id).orElseThrow(() -> new NotFoundException("Usuario"));
        users.findByEmail(data.getEmail())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new ConflictException("Ya existe un usuario con ese correo");
                });
        user.setName(data.getName());
        user.setEmail(data.getEmail());
        user.setRole(data.getRole());
        user.setActive(data.isActive());
        if (rawPassword != null && !rawPassword.isBlank()) {
            user.setPassword(encoder.encode(rawPassword));
        }
        return users.save(user);
    }

    public void delete(String id) {
        if (operations.existsByOrganizerUserIdOrManagerUserId(id, id)) {
            throw new ConflictException("Reasigna los eventos de este usuario antes de eliminarlo");
        }
        users.deleteById(id);
    }
}
