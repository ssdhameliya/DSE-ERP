# DSE ERP 2.1.3 Cross-platform display audit

This pass removes conflicting fixed primary-window minimums and uses the usable visual bounds of the active monitor. It preserves Windows behavior while adding the same responsive rules on macOS, Windows, and other JavaFX platforms.

Validated profiles in code: small display (<1050x650 scene), compact (<1500x850), ultra compact (<1220x720), normal/large, HiDPI/Retina logical coordinates, and multi-monitor restoration. Dialogs are clamped to the owner monitor.

Runtime acceptance checks remain required on physical Windows and macOS machines at 100%, 125%, 150%, 175%, and 200% scaling.
