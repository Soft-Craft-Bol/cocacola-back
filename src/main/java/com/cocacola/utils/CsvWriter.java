package com.cocacola.utils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Serializa filas a CSV (UTF-8, separador coma, comillas dobles escapadas). */
public final class CsvWriter {

    private CsvWriter() {
    }

    public static String write(List<String> columns, List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder(String.join(",", columns)).append("\r\n");
        for (Map<String, Object> row : rows) {
            sb.append(columns.stream().map(c -> escape(row.get(c))).collect(Collectors.joining(","))).append("\r\n");
        }
        return sb.toString();
    }

    private static String escape(Object value) {
        if (value == null) return "";
        String s = String.valueOf(value);
        return s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")
                ? "\"" + s.replace("\"", "\"\"") + "\""
                : s;
    }
}
