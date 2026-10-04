package com.cocacola.domain.helpers;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/** Limitador simple en memoria: como máximo `max` intentos por clave dentro de una ventana de tiempo. */
public class RateLimiter {

    private final int max;
    private final Duration window;
    private final ConcurrentHashMap<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    public RateLimiter(int max, Duration window) {
        this.max = max;
        this.window = window;
    }

    /** Registra un intento; lanza TooManyRequestsException si se superó el límite. */
    public void check(String key) {
        Instant now = Instant.now();
        Deque<Instant> deque = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst().isBefore(now.minus(window))) deque.pollFirst();
            if (deque.size() >= max) throw new TooManyRequestsException("Demasiados intentos. Espera unos minutos e inténtalo de nuevo.");
            deque.addLast(now);
        }
    }
}
