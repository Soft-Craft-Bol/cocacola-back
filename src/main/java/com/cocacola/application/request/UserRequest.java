package com.cocacola.application.request;

import com.cocacola.commons.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** En la creacion la contraseña es obligatoria; en la edicion, si viene vacia no cambia. */
public record UserRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotNull Role role,
        @Size(min = 6) String password,
        Boolean active) {
}
