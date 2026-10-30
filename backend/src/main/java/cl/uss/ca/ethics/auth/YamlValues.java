package cl.uss.ca.ethics.auth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lee el subconjunto de YAML que usan los dos archivos de ética.
 * No es un intérprete YAML general: una estructura inesperada se rechaza.
 */
public final class YamlValues {
    private YamlValues() {
    }

    public static Map<String, Object> parseMap(String yaml) {
        Object value = new Parser(yaml).parse();
        if (!(value instanceof Map<?, ?> map)) {
            throw new EthicsAuthException("ETHICS_YAML_ROOT", "Ethics YAML root must be a map");
        }
        Map<String, Object> typed = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            typed.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return typed;
    }

    public static Map<String, Object> map(Object value, String path) {
        if (!(value instanceof Map<?, ?> raw)) {
            throw new EthicsAuthException("ETHICS_YAML_TYPE", path + " must be a map");
        }
        Map<String, Object> typed = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            typed.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return typed;
    }

    public static List<Object> list(Object value, String path) {
        if (!(value instanceof List<?> raw)) {
            throw new EthicsAuthException("ETHICS_YAML_TYPE", path + " must be a list");
        }
        return new ArrayList<>(raw);
    }

    public static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static final class Parser {
        private final List<Line> lines = new ArrayList<>();
        private int cursor;

        Parser(String yaml) {
            if (yaml == null) {
                throw new EthicsAuthException("ETHICS_YAML_EMPTY", "Ethics YAML is empty");
            }
            String[] rawLines = yaml.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
            for (int i = 0; i < rawLines.length; i++) {
                String raw = stripComment(rawLines[i]);
                if (raw.isBlank()) {
                    continue;
                }
                if (raw.indexOf('\t') >= 0) {
                    throw new EthicsAuthException("ETHICS_YAML_TAB", "Tabs are not allowed in ethics YAML at line " + (i + 1));
                }
                int indent = 0;
                while (indent < raw.length() && raw.charAt(indent) == ' ') {
                    indent++;
                }
                lines.add(new Line(indent, raw.substring(indent), i + 1));
            }
        }

        Object parse() {
            if (lines.isEmpty()) {
                throw new EthicsAuthException("ETHICS_YAML_EMPTY", "Ethics YAML is empty");
            }
            Object value = parseBlock();
            if (peek() != null) {
                throw error(peek(), "Unexpected content");
            }
            return value;
        }

        private Object parseBlock() {
            Line line = peek();
            if (line == null) {
                return new LinkedHashMap<String, Object>();
            }
            if (line.text.startsWith("-")) {
                return parseList(line.indent);
            }
            return parseMap(line.indent);
        }

        private Map<String, Object> parseMap(int indent) {
            Map<String, Object> map = new LinkedHashMap<>();
            while (true) {
                Line line = peek();
                if (line == null || line.indent < indent || line.text.startsWith("-")) {
                    break;
                }
                if (line.indent > indent) {
                    throw error(line, "Unexpected indentation");
                }
                consume();
                KeyValue key = key(line);
                Object value = valueAfter(key.rest, indent, line);
                if (map.containsKey(key.key)) {
                    throw error(line, "Duplicate key " + key.key);
                }
                map.put(key.key, value);
            }
            return map;
        }

        private List<Object> parseList(int indent) {
            List<Object> list = new ArrayList<>();
            while (true) {
                Line line = peek();
                if (line == null || line.indent < indent || !line.text.startsWith("-")) {
                    break;
                }
                if (line.indent > indent) {
                    throw error(line, "Unexpected list indentation");
                }
                consume();
                String rest = line.text.substring(1).trim();
                if (rest.isEmpty()) {
                    list.add(parseBlock());
                    continue;
                }
                if (rest.contains(":") && !quoted(rest)) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    KeyValue first = key(new Line(line.indent, rest, line.number));
                    item.put(first.key, valueAfter(first.rest, line.indent, line));
                    while (true) {
                        Line next = peek();
                        if (next == null || next.indent <= indent || next.text.startsWith("-")) {
                            break;
                        }
                        consume();
                        KeyValue field = key(next);
                        if (item.containsKey(field.key)) {
                            throw error(next, "Duplicate key " + field.key);
                        }
                        item.put(field.key, valueAfter(field.rest, next.indent, next));
                    }
                    list.add(item);
                    continue;
                }
                list.add(scalar(rest, line));
            }
            return list;
        }

        private Object valueAfter(String rest, int parentIndent, Line line) {
            if (rest != null && !rest.isEmpty()) {
                return scalar(rest, line);
            }
            Line next = peek();
            if (next == null || next.indent <= parentIndent) {
                return "";
            }
            return parseBlock();
        }

        private Object scalar(String rest, Line line) {
            if ("null".equals(rest) || "~".equals(rest)) {
                return null;
            }
            if ("[]".equals(rest)) {
                return List.of();
            }
            if ("{}".equals(rest)) {
                return new LinkedHashMap<String, Object>();
            }
            if (quoted(rest)) {
                return rest.substring(1, rest.length() - 1);
            }
            if (rest.startsWith("*") || rest.startsWith("&") || rest.startsWith("!")) {
                throw error(line, "Unsupported YAML syntax");
            }
            return rest;
        }

        private static KeyValue key(Line line) {
            int colon = line.text.indexOf(':');
            if (colon <= 0) {
                throw error(line, "Expected key: value");
            }
            return new KeyValue(line.text.substring(0, colon).trim(), line.text.substring(colon + 1).trim());
        }

        private static boolean quoted(String rest) {
            return rest.length() >= 2
                    && ((rest.startsWith("\"") && rest.endsWith("\""))
                    || (rest.startsWith("'") && rest.endsWith("'")));
        }

        private Line peek() {
            return cursor < lines.size() ? lines.get(cursor) : null;
        }

        private void consume() {
            cursor++;
        }

        private static EthicsAuthException error(Line line, String message) {
            int number = line == null ? 0 : line.number;
            return new EthicsAuthException("ETHICS_YAML_SYNTAX", message + " at line " + number);
        }
    }

    private static String stripComment(String raw) {
        boolean quoted = false;
        char quote = 0;
        for (int i = 0; i < raw.length(); i++) {
            char current = raw.charAt(i);
            if (quoted) {
                if (current == quote) {
                    quoted = false;
                }
                continue;
            }
            if (current == '"' || current == '\'') {
                quoted = true;
                quote = current;
                continue;
            }
            if (current == '#') {
                return raw.substring(0, i);
            }
        }
        return raw;
    }

    private record Line(int indent, String text, int number) {
    }

    private record KeyValue(String key, String rest) {
    }
}
