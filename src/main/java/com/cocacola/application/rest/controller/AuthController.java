package com.cocacola.application.rest.controller;

import com.cocacola.application.request.LoginRequest;
import com.cocacola.application.response.LoginResponse;
import com.cocacola.application.response.UserResponse;
import com.cocacola.domain.service.AuthService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService auth;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AuthService.Session session = auth.login(request.email(), request.password());
        return new LoginResponse(session.token(), UserResponse.from(session.user()));
    }
}
