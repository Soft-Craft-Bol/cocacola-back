package com.cocacola.application.rest.controller;

import com.cocacola.domain.service.CrmService;
import com.cocacola.domain.service.CrmService.SyncResult;
import com.cocacola.utils.CsvWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Integración con CRM: contactos con consentimiento (solo admin y marketing). */
@RestController
@RequestMapping("/crm")
@RequiredArgsConstructor
public class CrmController {

    private final CrmService crm;

    @GetMapping("/contacts")
    public ResponseEntity<Object> contacts(@RequestParam(required = false) String eventId, @RequestParam(defaultValue = "json") String format) {
        List<Map<String, Object>> rows = crm.contacts(eventId);
        if ("csv".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"contactos-crm.csv\"")
                    .body(CsvWriter.write(CrmService.COLUMNS, rows));
        }
        return ResponseEntity.ok(rows);
    }

    @PostMapping("/sync")
    public SyncResult sync(@RequestParam(required = false) String eventId) {
        return crm.sync(eventId);
    }
}
