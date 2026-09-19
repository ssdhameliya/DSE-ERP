package org.example.server.web;

/** Returned when a historical ERP attachment reference exists but its file is unavailable. */
public final class AttachmentUnavailableException extends RuntimeException {
    public AttachmentUnavailableException(String message) { super(message); }
}
