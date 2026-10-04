package com.cocacola.application.rest.controller;

import com.cocacola.application.request.TicketLookupRequest;
import com.cocacola.domain.helpers.RateLimiter;
import com.cocacola.domain.service.PublicService;
import com.cocacola.domain.service.PublicService.PublicEvent;
import com.cocacola.domain.service.PublicService.Ticket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints públicos: eventos abiertos a inscripción y consulta de la propia entrada. */
@RestController
@RequestMapping("/public")
public class PublicController {

    private final PublicService service;
    private final RateLimiter lookupLimiter;

    public PublicController(PublicService service,
                            @Value("${public.lookup.max-attempts:10}") int maxAttempts,
                            @Value("${public.lookup.window-minutes:10}") int windowMinutes) {
        this.service = service;
        this.lookupLimiter = new RateLimiter(maxAttempts, Duration.ofMinutes(windowMinutes));
    }

    @GetMapping("/events")
    public List<PublicEvent> events() {
        return service.upcomingEvents();
    }

    /** Recupera las entradas de una persona por correo o celular (limitado por IP para evitar abusos). */
    @PostMapping("/tickets/lookup")
    public List<Ticket> lookup(@Valid @RequestBody TicketLookupRequest request, HttpServletRequest http) {
        lookupLimiter.check(clientIp(http));
        return service.lookup(request.contact());
    }

    /** Entrada por su código (enlace del correo de confirmación). */
    @GetMapping("/tickets/{code}")
    public Ticket byCode(@PathVariable String code, HttpServletRequest http) {
        lookupLimiter.check(clientIp(http));
        return service.byCode(code);
    }

    private static String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        return forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : http.getRemoteAddr();
    }
}
