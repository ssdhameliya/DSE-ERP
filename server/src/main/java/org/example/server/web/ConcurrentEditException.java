package org.example.server.web;

public class ConcurrentEditException extends IllegalStateException {
    public ConcurrentEditException(String recordLabel) {
        super((recordLabel == null || recordLabel.isBlank() ? "This record" : recordLabel)
            + " was changed by another user. Reload the latest version before saving again.");
    }
}
