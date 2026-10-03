package com.cocacola.domain.service;

import com.cocacola.domain.helpers.TokenIssuer;
import com.cocacola.domain.helpers.UnauthorizedException;
import com.cocacola.domain.model.User;
import com.cocacola.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final TokenIssuer tokens;

    public record Session(String token, User user) {
    }

    public Session login(String email, String password) {
        User user = users.findByEmail(email.trim())
                .filter(u -> u.isActive() && encoder.matches(password, u.getPassword()))
                .orElseThrow(() -> new UnauthorizedException("Correo o contraseña incorrectos"));
        return new Session(tokens.issue(user), user);
    }
}
