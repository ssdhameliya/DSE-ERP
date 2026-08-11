package org.example.service;

import java.util.List;
import java.util.Map;

public record ImportResult(int imported, List<String> errors, Map<Integer,String> generatedCodes) {
    public boolean hasErrors() { return errors != null && !errors.isEmpty(); }
    public boolean hasGeneratedCodes() { return generatedCodes != null && !generatedCodes.isEmpty(); }
}
