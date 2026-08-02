# DSE ERP 2.1.3 Final Cross-Platform Stabilization

This full IntelliJ project consolidates the navigation, window restore/maximize,
responsive display, dialog/Add-Edit popup, toast/notification, theme and interaction
changes discussed for Windows and macOS.

## Included areas
- Controlled LRU navigation cache with stateful transaction screens excluded.
- No duplicate controller refresh immediately after initial FXML load.
- Inherited refresh methods supported for cached controllers.
- Screen-aware primary window minimums and saved-bound clamping.
- Dialog ownership, modality, responsive classes and monitor-boundary fitting.
- Responsive shell controls for compact and small displays.
- Adaptive toast width, wrapping, positioning and stack limit.
- Global theme/responsive handling for legacy JavaFX alerts/dialogs.
- Responsive CSS for buttons, icons, tables and dialog content.

## Required local verification before merge
Run on Windows and macOS. Verify normal/maximized startup, navigation, all Add/Edit
forms, confirmations, validations, notifications, 125%-200% Windows scaling,
Mac default/More Space modes, and multi-monitor movement.
