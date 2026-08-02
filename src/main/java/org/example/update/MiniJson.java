package org.example.update;

import java.util.*;

/** Minimal JSON parser used for GitHub's public Releases response without adding a runtime dependency. */
final class MiniJson {
    private final String text;
    private int index;

    private MiniJson(String text) { this.text = Objects.requireNonNull(text); }

    static Object parse(String text) { return new MiniJson(text).readValue(); }

    private Object readValue() {
        skipWhitespace();
        if (index >= text.length()) throw error("Unexpected end of JSON");
        return switch (text.charAt(index)) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> readString();
            case 't' -> { expect("true"); yield Boolean.TRUE; }
            case 'f' -> { expect("false"); yield Boolean.FALSE; }
            case 'n' -> { expect("null"); yield null; }
            default -> readNumber();
        };
    }

    private Map<String,Object> readObject() {
        Map<String,Object> result = new LinkedHashMap<>();
        index++;
        skipWhitespace();
        if (peek('}')) { index++; return result; }
        while (true) {
            skipWhitespace();
            String key = readString();
            skipWhitespace();
            require(':');
            result.put(key, readValue());
            skipWhitespace();
            if (peek('}')) { index++; return result; }
            require(',');
        }
    }

    private List<Object> readArray() {
        List<Object> result = new ArrayList<>();
        index++;
        skipWhitespace();
        if (peek(']')) { index++; return result; }
        while (true) {
            result.add(readValue());
            skipWhitespace();
            if (peek(']')) { index++; return result; }
            require(',');
        }
    }

    private String readString() {
        require('"');
        StringBuilder out = new StringBuilder();
        while (index < text.length()) {
            char c = text.charAt(index++);
            if (c == '"') return out.toString();
            if (c != '\\') { out.append(c); continue; }
            if (index >= text.length()) throw error("Invalid escape");
            char e = text.charAt(index++);
            switch (e) {
                case '"','\\','/' -> out.append(e);
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (index + 4 > text.length()) throw error("Invalid unicode escape");
                    out.append((char) Integer.parseInt(text.substring(index, index + 4), 16));
                    index += 4;
                }
                default -> throw error("Unsupported escape: " + e);
            }
        }
        throw error("Unterminated string");
    }

    private Number readNumber() {
        int start = index;
        if (peek('-')) index++;
        while (index < text.length() && Character.isDigit(text.charAt(index))) index++;
        if (peek('.')) { index++; while (index < text.length() && Character.isDigit(text.charAt(index))) index++; }
        if (peek('e') || peek('E')) {
            index++;
            if (peek('+') || peek('-')) index++;
            while (index < text.length() && Character.isDigit(text.charAt(index))) index++;
        }
        String value = text.substring(start, index);
        try { return value.contains(".") || value.contains("e") || value.contains("E") ? Double.parseDouble(value) : Long.parseLong(value); }
        catch (NumberFormatException ex) { throw error("Invalid number: " + value); }
    }

    private void expect(String literal) {
        if (!text.startsWith(literal, index)) throw error("Expected " + literal);
        index += literal.length();
    }

    private void require(char expected) {
        skipWhitespace();
        if (index >= text.length() || text.charAt(index) != expected) throw error("Expected '" + expected + "'");
        index++;
    }

    private boolean peek(char c) { return index < text.length() && text.charAt(index) == c; }
    private void skipWhitespace() { while (index < text.length() && Character.isWhitespace(text.charAt(index))) index++; }
    private IllegalArgumentException error(String message) { return new IllegalArgumentException(message + " at position " + index); }
}
