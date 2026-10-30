package com.msp.csvschema;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 대용량 CSV 파일을 행 단위로 스트리밍 검증하며
 * 스키마 규칙(타입, 필수값, 범위, 정규식) 위반을 상세 리포트로 산출하는 검증기.
 */
public final class CsvSchemaValidator {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    private CsvSchemaValidator() {}

    public static ValidationReport validate(Reader in, CsvSchema schema) throws IOException {
        Objects.requireNonNull(in, "in must not be null");
        Objects.requireNonNull(schema, "schema must not be null");

        BufferedReader reader = (in instanceof BufferedReader br) ? br : new BufferedReader(in);
        String headerLine = reader.readLine();
        if (headerLine == null) {
            return new ValidationReport(0, Collections.emptyList());
        }

        List<String> headers = parseCsvLine(headerLine);
        Map<String, Integer> headerIndexMap = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            headerIndexMap.put(headers.get(i).trim().toLowerCase(Locale.ROOT), i);
        }

        List<ValidationError> errors = new ArrayList<>();
        long rowNumber = 1;
        String line;

        while ((line = reader.readLine()) != null) {
            rowNumber++;
            if (line.isBlank()) continue;

            List<String> row = parseCsvLine(line);

            for (ColumnRule rule : schema.columns()) {
                String colKey = rule.name().toLowerCase(Locale.ROOT);
                Integer colIdx = headerIndexMap.get(colKey);

                if (colIdx == null || colIdx >= row.size()) {
                    if (rule.required()) {
                        errors.add(new ValidationError(rowNumber, rule.name(), null, "Missing required column"));
                    }
                    continue;
                }

                String rawValue = row.get(colIdx).trim();

                // Required check
                if (rawValue.isEmpty()) {
                    if (rule.required()) {
                        errors.add(new ValidationError(rowNumber, rule.name(), rawValue, "Value is required"));
                    }
                    continue; // Skip further type checks if empty
                }

                // Type check
                switch (rule.dataType()) {
                    case INTEGER -> {
                        try {
                            long val = Long.parseLong(rawValue);
                            if (rule.min() != null && val < rule.min()) {
                                errors.add(new ValidationError(rowNumber, rule.name(), rawValue, "Value " + val + " is less than minimum " + rule.min()));
                            }
                            if (rule.max() != null && val > rule.max()) {
                                errors.add(new ValidationError(rowNumber, rule.name(), rawValue, "Value " + val + " is greater than maximum " + rule.max()));
                            }
                        } catch (NumberFormatException e) {
                            errors.add(new ValidationError(rowNumber, rule.name(), rawValue, "Invalid integer format"));
                        }
                    }
                    case DECIMAL -> {
                        try {
                            double val = Double.parseDouble(rawValue);
                            if (rule.min() != null && val < rule.min()) {
                                errors.add(new ValidationError(rowNumber, rule.name(), rawValue, "Value " + val + " is less than minimum " + rule.min()));
                            }
                            if (rule.max() != null && val > rule.max()) {
                                errors.add(new ValidationError(rowNumber, rule.name(), rawValue, "Value " + val + " is greater than maximum " + rule.max()));
                            }
                        } catch (NumberFormatException e) {
                            errors.add(new ValidationError(rowNumber, rule.name(), rawValue, "Invalid decimal format"));
                        }
                    }
                    case DATE -> {
                        try {
                            LocalDate.parse(rawValue);
                        } catch (DateTimeParseException e) {
                            errors.add(new ValidationError(rowNumber, rule.name(), rawValue, "Invalid date format (expected yyyy-MM-dd)"));
                        }
                    }
                    case EMAIL -> {
                        if (!EMAIL_PATTERN.matcher(rawValue).matches()) {
                            errors.add(new ValidationError(rowNumber, rule.name(), rawValue, "Invalid email format"));
                        }
                    }
                    case STRING -> {
                        // pass
                    }
                }

                // Regex check
                if (rule.regex() != null && !rule.regex().matcher(rawValue).matches()) {
                    errors.add(new ValidationError(rowNumber, rule.name(), rawValue, "Pattern mismatch: " + rule.regex().pattern()));
                }
            }
        }

        return new ValidationReport(rowNumber - 1, errors);
    }

    private static List<String> parseCsvLine(String line) {
        List<String> list = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                list.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        list.add(sb.toString());
        return list;
    }
}
