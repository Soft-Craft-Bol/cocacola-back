package com.cocacola.application.rest.controller;

import com.cocacola.application.request.UserRequest;
import com.cocacola.application.response.OkResponse;
import com.cocacola.application.response.UserResponse;
import com.cocacola.domain.model.User;
import com.cocacola.domain.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService users;

    @GetMapping
    public List<UserResponse> list() {
        return users.list().stream().map(UserResponse::from).toList();
    }

    @PostMapping
    public UserResponse create(@Valid @RequestBody UserRequest r) {
        return UserResponse.from(users.create(toDomain(r), r.password()));
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable String id, @Valid @RequestBody UserRequest r) {
        return UserResponse.from(users.update(id, toDomain(r), r.password()));
    }

    @DeleteMapping("/{id}")
    public OkResponse delete(@PathVariable String id) {
        users.delete(id);
        return OkResponse.done();
    }

    private static User toDomain(UserRequest r) {
        return User.builder().name(r.name()).email(r.email().trim()).role(r.role())
                .active(r.active() == null || r.active()).build();
    }
}
