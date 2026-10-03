package com.cocacola.application.rest.controller;

import com.cocacola.application.request.SendCommunicationRequest;
import com.cocacola.domain.model.MessageLog;
import com.cocacola.domain.service.CommunicationService;
import com.cocacola.domain.service.CommunicationService.SendResult;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Comunicaciones por correo y WhatsApp (solo admin y marketing). */
@RestController
@RequestMapping("/communications")
@RequiredArgsConstructor
public class CommunicationController {

    private final CommunicationService communications;

    @PostMapping("/send")
    public SendResult send(@Valid @RequestBody SendCommunicationRequest r) {
        return communications.send(r.eventId(), r.type(), r.channel(), r.audience(), r.targetEventId());
    }

    @GetMapping("/log")
    public List<MessageLog> log(@RequestParam String eventId, @RequestParam(defaultValue = "50") int limit) {
        return communications.history(eventId, limit);
    }
}
