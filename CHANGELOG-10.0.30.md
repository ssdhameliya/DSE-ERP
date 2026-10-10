# DSE ERP 10.0.30 Release Notes

### UI & Workspace Enhancements
- **Auto-Hide Left Navigation Sidebar (Max Screen View):** Introduced Option A collapsible navigation panel with quick toggle (`Ctrl + [` or sidebar toggle button). Main workspace and data tables automatically expand to full screen width. Smooth hover-peek drawer allows browsing menus from the left edge without disrupting active screens.
- **Saved Reports Navigation:** Fixed button glow issue where "Saved Reports" was misclassified as a primary action. Dynamic tab selection now accurately highlights either Report Center or Saved Reports.
- **Report Filter Panel Full Visibility:** Fixed vertical clipping in the unified Report Viewer filter panel across all financial, sales, purchase, item, and GST registers.

### Scheduling & Date/Time Uniformity
- **Scheduled Reports Uniformity:** KPI icon binding restored for scheduled reports. Formatted next run times through centralized `BusinessClock` adhering to company date/time format configurations.
- **Backend Timestamp Service:** Added unified timestamp formatting to server runtime clock, ensuring resilient backend execution.

### GST & Calculation Precision
- **Statutory Tax Calculations:** Canonical shared calculation engine enforces exact `BigDecimal` arithmetic. Intra-state supplies guarantee zero paise drift with remainder splitting ($CGST + SGST \equiv Total Tax$).
- **GST Compliance:** Validated Section 49 / Rule 88A ITC set-off hierarchy and GSTR-2B reconciliation within $\pm ₹ 1.00$ statutory tolerance.
