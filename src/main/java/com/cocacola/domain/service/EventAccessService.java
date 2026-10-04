package com.cocacola.domain.service;

import com.cocacola.config.security.AuthUser;
import com.cocacola.persistence.crud.EventOperationsRepository;
import com.cocacola.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** El acceso de los organizadores se verifica también en las operaciones del backend. */
@Service @RequiredArgsConstructor
public class EventAccessService {
    private final EventOperationsRepository operations;
    private final UserRepository users;

    public AuthUser currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthUser user ? user : null;
    }

    public boolean allowed(String eventId) {
        AuthUser user = currentUser();
        // Los endpoints públicos y los procesos internos conservan sus permisos de Spring Security.
        if (user == null) return true;
        var account = users.findById(user.id());
        if (account.isEmpty() || !account.get().isActive() || !account.get().getRole().name().equals(user.role())) return false;
        if (!"ORGANIZER".equals(user.role())) return true;
        if (eventId == null) return false;
        return operations.findById(eventId).map(o -> user.id().equals(o.getOrganizerUserId())
                || user.id().equals(o.getManagerUserId())).orElse(false);
    }

    public void require(String eventId) {
        if (!allowed(eventId)) throw new AccessDeniedException("No tienes asignado este evento");
    }

    public java.util.Set<String> assignedEventIds() {
        AuthUser user = currentUser();
        if (user == null) return null;
        if (users.findById(user.id()).filter(u -> u.isActive() && u.getRole().name().equals(user.role())).isEmpty())
            throw new AccessDeniedException("La cuenta cambió o está inactiva; inicia sesión nuevamente");
        if (!"ORGANIZER".equals(user.role())) return null;
        return operations.findByOrganizerUserIdOrManagerUserId(user.id(), user.id()).stream()
                .map(o -> o.getEventId()).collect(java.util.stream.Collectors.toSet());
    }

    public void requireRole(String... roles) {
        AuthUser user = currentUser();
        if (user == null || users.findById(user.id()).filter(u -> u.isActive() && u.getRole().name().equals(user.role())).isEmpty()
                || java.util.Arrays.stream(roles).noneMatch(r -> r.equals(user.role()))) {
            throw new AccessDeniedException("No tienes permiso para esta acción");
        }
    }
}
