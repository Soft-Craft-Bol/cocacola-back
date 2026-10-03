package com.cocacola.application.rest.controller;

import com.cocacola.domain.service.BiExportService;
import com.cocacola.domain.service.BiExportService.TableInfo;
import com.cocacola.utils.CsvWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Datos para Power BI. Autenticación: sesión JWT (admin o marketing) o clave de API
 * (cabecera X-API-Key o parámetro ?key=, ver bi.api-key).
 */
@RestController
@RequestMapping("/bi")
@RequiredArgsConstructor
public class BiController {

    private final BiExportService bi;

    /** Catálogo de tablas con sus columnas y tipos. */
    @GetMapping
    public List<TableInfo> catalog() {
        return bi.catalog();
    }

    /** Una tabla en JSON (por defecto) o CSV (?format=csv). */
    @GetMapping("/{table}")
    public ResponseEntity<Object> table(@PathVariable String table, @RequestParam(defaultValue = "json") String format) {
        TableInfo info = bi.table(table);
        List<Map<String, Object>> rows = bi.rows(info.name());
        if ("csv".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + info.name() + ".csv\"")
                    .body(CsvWriter.write(info.columnNames(), rows));
        }
        return ResponseEntity.ok(rows);
    }
}
