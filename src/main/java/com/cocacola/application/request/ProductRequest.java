package com.cocacola.application.request;

import jakarta.validation.constraints.*;

public record ProductRequest(@NotBlank @Size(max=120) String name, @NotBlank @Size(max=100) String category,
        @Size(max=100) String flavor, @Size(max=100) String presentation, boolean archived) {}
