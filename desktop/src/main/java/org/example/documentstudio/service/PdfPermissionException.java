package org.example.documentstudio.service;

import java.io.IOException;

/** Raised when a PDF can be opened but the supplied credentials do not grant editable access. */
public class PdfPermissionException extends IOException {
    public PdfPermissionException(String message) { super(message); }
}
