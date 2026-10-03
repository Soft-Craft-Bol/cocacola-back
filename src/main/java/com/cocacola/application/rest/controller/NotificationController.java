package com.cocacola.application.rest.controller;

import com.cocacola.application.response.OkResponse;
import com.cocacola.domain.model.Notification;
import com.cocacola.domain.service.NotificationService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notifications;

    @GetMapping
    public List<Notification> list(@RequestParam(defaultValue = "false") boolean unread, @RequestParam(defaultValue = "30") int limit) {
        return notifications.list(unread, limit);
    }

    @GetMapping("/count")
    public Map<String, Long> count() {
        return Map.of("unread", notifications.unread());
    }

    @PostMapping("/{id}/read")
    public OkResponse read(@PathVariable String id) {
        notifications.markRead(id);
        return OkResponse.done();
    }

    @PostMapping("/read-all")
    public OkResponse readAll() {
        notifications.markAllRead();
        return OkResponse.done();
    }
}
