package com.cocacola.application.request;

import jakarta.validation.constraints.NotBlank;

public record TicketLookupRequest(@NotBlank String contact) {
}
