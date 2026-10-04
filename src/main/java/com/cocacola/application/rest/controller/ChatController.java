package com.cocacola.application.rest.controller;

import com.cocacola.domain.service.ChatService;
import com.cocacola.domain.service.ChatService.Answer;
import com.cocacola.domain.service.ChatService.Turn;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Asistente de consulta que solo responde con los datos de la plataforma. */
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chat;

    public record ChatRequest(String message, List<Turn> history) {
    }

    @PostMapping
    public Answer ask(@RequestBody ChatRequest request) {
        return chat.ask(request.message(), request.history());
    }
}
