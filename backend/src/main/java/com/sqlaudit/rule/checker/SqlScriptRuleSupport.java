package com.sqlaudit.rule.checker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SqlScriptRuleSupport {

    private static final Pattern CREATE_OR_ALTER_TABLE = Pattern.compile(
            "(?is)^\\s*(CREATE\\s+TABLE|ALTER\\s+TABLE)\\b");
    private static final Pattern CREATE_TABLE_NAME = Pattern.compile(
            "(?is)^\\s*CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([\"\\w.]+)");
    private static final Pattern ALTER_TABLE_NAME = Pattern.compile(
            "(?is)^\\s*ALTER\\s+TABLE\\s+(?:ONLY\\s+)?([\"\\w.]+)");
    private static final Pattern CREATE_INDEX_COLUMNS = Pattern.compile(
            "(?is)^\\s*CREATE\\s+(?:UNIQUE\\s+)?INDEX\\s+[\"\\w.]+\\s+ON\\s+[\"\\w.]+\\s*\\((.*)\\)");
    private static final Pattern PRIMARY_OBJECT_NAME = Pattern.compile(
            "(?is)^\\s*(?:CREATE|ALTER|DROP)\\s+"
                    + "(?:OR\\s+REPLACE\\s+)?"
                    + "(?:UNIQUE\\s+)?"
                    + "(?:FOREIGN\\s+)?"
                    + "(?:TEMP(?:ORARY)?\\s+)?"
                    + "(?:MATERIALIZED\\s+)?"
                    + "(?:TABLE|VIEW|INDEX|SEQUENCE|SCHEMA|DATABASE|FUNCTION|PROCEDURE|TRIGGER)\\s+"
                    + "(?:IF\\s+(?:NOT\\s+)?EXISTS\\s+)?"
                    + "(?:ONLY\\s+)?"
                    + "([\"\\w.]+)");

    private SqlScriptRuleSupport() {
    }

    static boolean isCreateTableLike(String sql) {
        return CREATE_OR_ALTER_TABLE.matcher(sql).find();
    }

    static Optional<String> extractPrimaryObjectName(String sql) {
        Matcher matcher = PRIMARY_OBJECT_NAME.matcher(sql);
        if (matcher.find()) {
            return Optional.ofNullable(matcher.group(1));
        }
        return Optional.empty();
    }

    static Optional<String> extractCreateTableName(String sql) {
        Matcher matcher = CREATE_TABLE_NAME.matcher(sql);
        if (matcher.find()) {
            return Optional.ofNullable(matcher.group(1));
        }
        return Optional.empty();
    }

    static Optional<String> extractAlterTableName(String sql) {
        Matcher matcher = ALTER_TABLE_NAME.matcher(sql);
        if (matcher.find()) {
            return Optional.ofNullable(matcher.group(1));
        }
        return Optional.empty();
    }

    static String normalizeIdentifier(String identifier) {
        if (identifier == null) {
            return null;
        }
        String[] parts = identifier.trim().split("\\.");
        List<String> normalized = new ArrayList<>();
        for (String part : parts) {
            String candidate = part.trim();
            if (candidate.startsWith("\"") && candidate.endsWith("\"") && candidate.length() >= 2) {
                candidate = candidate.substring(1, candidate.length() - 1);
            }
            normalized.add(candidate.toLowerCase(Locale.ROOT));
        }
        return String.join(".", normalized);
    }

    static List<String> splitIdentifierParts(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return List.of();
        }
        String[] parts = identifier.trim().split("\\.");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String candidate = part.trim();
            if (candidate.startsWith("\"") && candidate.endsWith("\"") && candidate.length() >= 2) {
                candidate = candidate.substring(1, candidate.length() - 1);
            }
            result.add(candidate);
        }
        return result;
    }

    static Optional<String> extractCreateTableBody(String sql) {
        int createIndex = sql.toUpperCase(Locale.ROOT).indexOf("CREATE TABLE");
        if (createIndex < 0) {
            return Optional.empty();
        }
        int bodyStart = sql.indexOf('(', createIndex);
        if (bodyStart < 0) {
            return Optional.empty();
        }
        int depth = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = bodyStart; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (!inDoubleQuote && c == '\'') {
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (!inSingleQuote && c == '"') {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }
            if (inSingleQuote || inDoubleQuote) {
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return Optional.of(sql.substring(bodyStart + 1, i));
                }
            }
        }
        return Optional.empty();
    }

    static List<String> splitTopLevelCommaSections(String body) {
        List<String> sections = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (!inDoubleQuote && c == '\'') {
                inSingleQuote = !inSingleQuote;
            } else if (!inSingleQuote && c == '"') {
                inDoubleQuote = !inDoubleQuote;
            } else if (!inSingleQuote && !inDoubleQuote) {
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth = Math.max(0, depth - 1);
                } else if (c == ',' && depth == 0) {
                    sections.add(current.toString().trim());
                    current.setLength(0);
                    continue;
                }
            }
            current.append(c);
        }
        if (!current.isEmpty()) {
            sections.add(current.toString().trim());
        }
        return sections;
    }

    static List<String> extractCreateTableColumns(String sql) {
        Optional<String> bodyOpt = extractCreateTableBody(sql);
        if (bodyOpt.isEmpty()) {
            return List.of();
        }
        List<String> columns = new ArrayList<>();
        for (String section : splitTopLevelCommaSections(bodyOpt.get())) {
            String trimmed = section.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            String upper = trimmed.toUpperCase(Locale.ROOT);
            if (upper.startsWith("CONSTRAINT ")
                    || upper.startsWith("PRIMARY KEY")
                    || upper.startsWith("UNIQUE ")
                    || upper.startsWith("CHECK ")
                    || upper.startsWith("FOREIGN KEY")
                    || upper.startsWith("LIKE ")
                    || upper.startsWith("PARTITION ")) {
                continue;
            }

            Matcher matcher = Pattern.compile("^(\"[^\"]+\"|[A-Za-z_][A-Za-z0-9_$]*)").matcher(trimmed);
            if (matcher.find()) {
                columns.add(normalizeIdentifier(matcher.group(1)));
            }
        }
        return columns;
    }

    static int extractIndexColumnCount(String sql) {
        Matcher matcher = CREATE_INDEX_COLUMNS.matcher(sql);
        if (!matcher.find()) {
            return 0;
        }
        String body = matcher.group(1);
        return splitTopLevelCommaSections(body).size();
    }
}
