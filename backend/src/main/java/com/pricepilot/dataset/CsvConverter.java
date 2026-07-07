package com.pricepilot.dataset;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class CsvConverter {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public static <T> String toCsv(List<T> data, Class<T> clazz) {
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(getHeader(fields)).append("\n");

        if (data != null) {
            for (T item : data) {
                String row = List.of(fields).stream().map(field -> {
                    try {
                        Object val = field.get(item);
                        return formatValue(val);
                    } catch (IllegalAccessException e) {
                        return "";
                    }
                }).collect(Collectors.joining(","));
                sb.append(row).append("\n");
            }
        }

        return sb.toString();
    }

    private static String getHeader(Field[] fields) {
        return List.of(fields).stream()
                .map(Field::getName)
                .collect(Collectors.joining(","));
    }

    private static String formatValue(Object val) {
        if (val == null) {
            return "";
        }
        if (val instanceof LocalDateTime) {
            return DATE_FORMATTER.format((LocalDateTime) val);
        }
        String str = val.toString();
        if (str.contains(",") || str.contains("\"") || str.contains("\n") || str.contains("\r")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }
}
