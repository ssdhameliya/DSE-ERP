# DSE ERP 2.1.3 Cross-Platform Interaction Audit

## Scope
Full static audit of FXML screens, controller event bindings, buttons/icons, native JavaFX alerts/dialogs, custom ModernDialog, toast notifications, Add/Edit stages, responsive classes, and monitor fitting.

## Results
- FXML files parsed: **36/36**
- FXML event bindings inspected: **283**
- Native `new Alert(...)` occurrences: **85**
- Native `new Dialog(...)` occurrences: **0**
- Secondary `new Stage()` occurrences: **8**
- Toast calls: **7**

## Central corrections in this build
- Global theme/window hook now installs responsive size classes on every scene, including legacy Alerts and Dialogs.
- Every shown Stage is clamped to its owner monitor usable bounds.
- ModernDialog is explicitly responsive and monitor-fitted when shown.
- Toast width is derived from owner-window width, messages wrap, vertical offset adapts, and visible stack is capped at four.
- Compact/small-display CSS now adjusts button height/padding, icon scale, dialog width, dialog buttons, native dialog wrapping, and toast dimensions.

## Secondary-stage audit
- `src/main/java/org/example/controller/OperationsController.java`: Stage creations=1, shared configuration calls=1
- `src/main/java/org/example/controller/UserAccessController.java`: Stage creations=1, shared configuration calls=1
- `src/main/java/org/example/controller/InventoryController.java`: Stage creations=1, shared configuration calls=1
- `src/main/java/org/example/controller/PurchaseController.java`: Stage creations=1, shared configuration calls=1
- `src/main/java/org/example/controller/PartyMasterController.java`: Stage creations=1, shared configuration calls=1
- `src/main/java/org/example/controller/SalesController.java`: Stage creations=1, shared configuration calls=1
- `src/main/java/org/example/controller/ItemMasterController.java`: Stage creations=1, shared configuration calls=1
- `src/main/java/org/example/controller/MasterDataController.java`: Stage creations=1, shared configuration calls=1

## Event-binding notes
- `src/main/resources/fxml/pages/Customer.fxml`: handler newParty not directly declared in org.example.controller.CustomerController
- `src/main/resources/fxml/pages/Customer.fxml`: handler editParty not directly declared in org.example.controller.CustomerController
- `src/main/resources/fxml/pages/Customer.fxml`: handler deleteParty not directly declared in org.example.controller.CustomerController
- `src/main/resources/fxml/pages/Customer.fxml`: handler refresh not directly declared in org.example.controller.CustomerController
- `src/main/resources/fxml/pages/Customer.fxml`: handler importParties not directly declared in org.example.controller.CustomerController
- `src/main/resources/fxml/pages/Customer.fxml`: handler exportparties not directly declared in org.example.controller.CustomerController
- `src/main/resources/fxml/pages/Suppliers.fxml`: handler newParty not directly declared in org.example.controller.SupplierController
- `src/main/resources/fxml/pages/Suppliers.fxml`: handler editParty not directly declared in org.example.controller.SupplierController
- `src/main/resources/fxml/pages/Suppliers.fxml`: handler deleteParty not directly declared in org.example.controller.SupplierController
- `src/main/resources/fxml/pages/Suppliers.fxml`: handler refresh not directly declared in org.example.controller.SupplierController
- `src/main/resources/fxml/pages/Suppliers.fxml`: handler importParties not directly declared in org.example.controller.SupplierController
- `src/main/resources/fxml/pages/Suppliers.fxml`: handler exportparties not directly declared in org.example.controller.SupplierController

## Runtime acceptance matrix
Validate Windows 1366x768/100%, 1920x1080 at 100/125/150%, 4K at 150/200%; macOS default and More Space logical resolutions; maximized/restored; multi-monitor; all Add/Edit flows; confirmation, error, warning and success dialogs; notification stack; keyboard activation and close/cancel behavior.

## Limitation
This is a static source and structural verification. Pixel-perfect equality requires runtime screenshots on representative Windows and macOS hardware because native font metrics and rendering differ. The implementation targets equivalent visibility, hierarchy and interaction rather than identical physical pixels.
