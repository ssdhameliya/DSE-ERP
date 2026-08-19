package org.example.util;

/**
 * Marker/contract for the single DSE ERP modal presentation. Business
 * controllers choose intent; ModernDialog/OwnedAlert/OwnedDialog own rendering.
 */
public final class DialogPresentation {
    public static final String CUSTOM = "erp-dialog-custom";
    public static final String SHELL_CLASS = "modern-dialog";
    private DialogPresentation() {}
}
