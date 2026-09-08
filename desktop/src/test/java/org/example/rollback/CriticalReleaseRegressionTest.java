package org.example.rollback;

import org.example.update.BuildInfo;
import org.example.util.UiSemanticRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CriticalReleaseRegressionTest {
    @Test void buildIdentityIsGeneratedFor988() {
        assertEquals(org.example.shared.RuntimeContract.appVersion(), BuildInfo.version());
        assertEquals(org.example.shared.RuntimeContract.buildRevision(), BuildInfo.buildRevision());
        assertEquals(1, BuildInfo.databaseMigrationVersion());
        assertEquals(1, BuildInfo.workspaceSchemaVersion());
    }

    @Test void previousSameGenerationVersionUsesSchemaOne() {
        RollbackService service = new RollbackService();
        assertEquals(1, service.schemaForVersion("9.0.79"));
        RollbackService.Compatibility compatibility = service.compatibilityFor(1);
        assertTrue(compatibility.safe());
        assertEquals("Safe", compatibility.label());
    }

    @Test void retainedRollbackPackagesCanResolveCurrentCompatibilityGeneration() throws Exception {
        String service = Files.readString(Path.of("src/main/java/org/example/rollback/RollbackService.java"));
        assertTrue(service.contains("isManagedRollbackPackage(installer)"));
        assertTrue(service.contains("generationSchema > 0"));
    }

    @Test void unknownOrFutureSchemaGenerationStaysBlocked() {
        RollbackService service = new RollbackService();
        assertEquals(-1, service.schemaForVersion("7.2.1"));
        assertEquals(-1, service.schemaForVersion("99.0.0"));
        assertFalse(service.compatibilityFor(-1).safe());
        assertEquals("Unknown", service.compatibilityFor(-1).label());
    }

    @Test void reminderAndNotificationHaveDistinctIcons() {
        assertEquals("fas-clock", UiSemanticRegistry.iconLiteral("reminder"));
        assertEquals("fas-bell", UiSemanticRegistry.iconLiteral("notification"));
        assertNotEquals(UiSemanticRegistry.iconLiteral("reminder"), UiSemanticRegistry.iconLiteral("notification"));
    }

    @Test void workspaceLabelsHaveExplicitSemantics() {
        assertEquals("storage-usage", UiSemanticRegistry.fieldSemantic("Storage Usage"));
        assertEquals("storage-retention", UiSemanticRegistry.fieldSemantic("Storage Retention"));
        assertEquals("diagnostic", UiSemanticRegistry.fieldSemantic("Diagnostic ZIP retention"));
        assertEquals("temporary", UiSemanticRegistry.fieldSemantic("Temporary files retention"));
    }

    @Test void rollbackNoLongerUsesHardcodedVersionPromptOrKnownSchemaMap() throws Exception {
        String controller = Files.readString(Path.of("src/main/java/org/example/controller/SafeRollbackController.java"));
        String service = Files.readString(Path.of("src/main/java/org/example/rollback/RollbackService.java"));
        assertFalse(controller.contains("OwnedTextInputDialog(\"9.0.7\")"));
        assertTrue(controller.contains("publishedPreviousVersions"));
        assertFalse(service.contains("KNOWN_SCHEMA"));
    }

    @Test void workspacePaddingAndFinalSidebarSelectionAreOwnedByBothThemes() throws Exception {
        for (String theme : new String[]{"light-theme.css", "dark-theme.css"}) {
            String css = Files.readString(Path.of("src/main/resources/css", theme));
            assertTrue(css.contains(".workspace-settings-panel"));
            assertTrue(css.contains("-fx-padding: 22px 24px 24px 24px"));
            assertTrue(css.contains(".erp-ui-standard .erp-sidebar .button.menu-selected"));
            assertTrue(css.contains("-fx-background-color: #5b21b6"));
        }
    }
    @Test void semanticAuditRegressionFor988() throws Exception {
        var semantic = org.example.util.IconFactory.class.getDeclaredMethod("semantic", String.class);
        semantic.setAccessible(true);
        assertEquals("import", semantic.invoke(null, "Import Excel"));
        assertEquals("export", semantic.invoke(null, "Export Excel"));
        assertEquals("reject", semantic.invoke(null, "Reject Return"));
        assertEquals("refund", semantic.invoke(null, "View / Record Refund"));
        assertEquals("mark-all-read", semantic.invoke(null, "Mark All Read"));
        assertEquals("open-record", semantic.invoke(null, "Open Record"));
        assertNotEquals(UiSemanticRegistry.iconLiteral("permission"), UiSemanticRegistry.iconLiteral("role"));
        assertNotEquals(UiSemanticRegistry.iconLiteral("size"), UiSemanticRegistry.iconLiteral("unit"));
        assertNotEquals(UiSemanticRegistry.iconLiteral("frequency"), UiSemanticRegistry.iconLiteral("time"));
        assertNotEquals(UiSemanticRegistry.iconLiteral("compatibility"), UiSemanticRegistry.iconLiteral("reference"));
        assertEquals("update", UiSemanticRegistry.fieldSemantic("Latest Available"));
        assertEquals("outstanding", UiSemanticRegistry.headerSemantic("Outstanding"));
    }

    @Test void ordinaryValuesUseSharedSemanticDecorator() throws Exception {
        String source = Files.readString(Path.of("src/main/java/org/example/util/IconFactory.java"));
        assertTrue(source.contains("decorateOrdinaryValueLabel(label)"));
        assertTrue(source.contains("erp-value-colour-"));
        for (String theme : new String[]{"light-theme.css", "dark-theme.css"}) {
            String css = Files.readString(Path.of("src/main/resources/css", theme));
            assertTrue(css.contains(".erp-value-colour-blue"));
            assertTrue(css.contains(".erp-value-colour-green"));
        }
    }

    @Test void latestLiveFilterIsReplayedAfterServerPageApply() {
        org.example.util.RegisterPageState state = new org.example.util.RegisterPageState();
        java.util.concurrent.atomic.AtomicInteger value = new java.util.concurrent.atomic.AtomicInteger();
        state.runApplying(() -> {
            state.runWhenIdle(() -> value.set(1));
            state.runWhenIdle(() -> value.set(2));
            assertEquals(0, value.get());
        });
        assertEquals(2, value.get());
    }

    @Test void tableSelectionNoLongerRefreshesWholeTableAndLayoutIsCoalesced() throws Exception {
        String enhancer = Files.readString(Path.of("src/main/java/org/example/util/ProfessionalUiEnhancer.java"));
        String layout = Files.readString(Path.of("src/main/java/org/example/util/DynamicTableLayoutManager.java"));
        assertFalse(enhancer.contains("table.refresh();"));
        assertTrue(enhancer.contains("updateSelectionVisual()"));
        assertTrue(layout.contains("Always coalesce width/item/skin changes into one next-pulse pass"));
        assertTrue(layout.contains("RENDERED_ACTION_WIDTH"));
        assertFalse(layout.contains("region.applyCss();"));
    }

    @Test void genericSectionsAreNotAutomaticallyPromotedToShadowedSurfaces() throws Exception {
        String designSystem = Files.readString(Path.of("src/main/java/org/example/util/UiDesignSystem.java"));
        assertTrue(designSystem.contains("a generic word such as section/panel/workspace/detail must not"));
        assertFalse(designSystem.contains("return containsAny(s, \"card\", \"panel\", \"drawer\", \"workspace\", \"section\""));
    }

    @Test void finalThemeAuthorityOwnsCleanControlsSearchRailAndSettingsWorkspace() throws Exception {
        for (String theme : new String[]{"light-theme.css", "dark-theme.css"}) {
            String css = Files.readString(Path.of("src/main/resources/css", theme));
            assertTrue(css.contains("DSE ERP — FINAL UI STABILITY AUTHORITY"));
            assertTrue(css.contains(".workspace-deployment-grid"));
            assertTrue(css.contains(".dse-global-search-v3-module-list .scroll-bar:horizontal"));
            assertTrue(css.contains("-fx-max-height: 0"));
            assertTrue(css.contains(".erp-control-input:focus-within"));
            assertTrue(css.contains("-fx-effect: null"));
        }
        String workspace = Files.readString(Path.of("src/main/resources/fxml/pages/settings/WorkspaceSettingsPanel.fxml"));
        assertTrue(workspace.contains("Connection mode"));
        assertTrue(workspace.contains("Company server URL"));
        assertTrue(workspace.contains("GridPane.rowIndex=\"1\"><Label text=\"Reports / Exports\""));
    }

    @Test void managedSharedClientCannotBeDowngradedByStaleLocalEnvironmentOverride() throws Exception {
        String config = Files.readString(Path.of("src/main/java/org/example/config/ConfigManager.java"));
        assertTrue(config.contains("WorkspaceManager.isManagedSharedClientWorkspace()) return DeploymentMode.SHARED_CLIENT"));
        assertTrue(config.contains("The verified managed profile owns the company-server endpoint"));
        String settings = Files.readString(Path.of("src/main/java/org/example/controller/SettingsController.java"));
        assertTrue(settings.contains("effectiveLoadedDeploymentMode()"));
        assertTrue(settings.contains("WorkspaceManager.updateManagedSharedClientConnection"));
        assertTrue(settings.contains("Settings could not be saved:"));
        assertTrue(settings.contains("deploymentRestartRequired = true"));
    }

    @Test void sharedClientConnectionActivationIsIdempotentAndRollbackSafe() throws Exception {
        String workspace = Files.readString(Path.of("src/main/java/org/example/config/WorkspaceManager.java"));
        assertTrue(workspace.contains("updateManagedSharedClientConnection(serverUrl, environment)"));
        assertTrue(workspace.contains("restore the persistent pointer to LOCAL"));
        assertTrue(workspace.contains("The new managed Shared Client profile could not be verified"));
    }

    @Test void brokenManagedSharedClientCanRepairServerConnectionFromStartupUi() throws Exception {
        String main = Files.readString(Path.of("src/main/java/org/example/app/Main.java"));
        assertTrue(main.contains("Configure Server"));
        assertTrue(main.contains("configureSharedClientConnectionAtStartup"));
        assertTrue(main.contains("DeploymentConnectionService.test(candidate, expectedEnvironment)"));
        assertTrue(main.contains("WorkspaceManager.updateManagedSharedClientConnection(normalized, selectedEnvironment)"));
        assertTrue(main.contains("previous LOCAL workspace was not modified"));
        assertTrue(main.contains("Save & Restart"));
    }

    @Test void everyCentralDialogHasVisibleCloseControlAndEscapePath() throws Exception {
        String presentation = Files.readString(Path.of("src/main/java/org/example/util/DialogPresentation.java"));
        assertTrue(presentation.contains("IconFactory.compactIcon(\"close\", 14)"));
        assertTrue(presentation.contains("closeButton.setCancelButton(true)"));
        for (String theme : new String[]{"light-theme.css", "dark-theme.css"}) {
            String css = Files.readString(Path.of("src/main/resources/css", theme));
            assertTrue(css.contains("dialog close control must remain visible"));
            assertTrue(css.contains(".modern-dialog-close .erp-action-glyph"));
        }
    }

    @Test void realUpgradeQueuesWhatsNewAndCurrentReleaseHasOfflineHighlights() throws Exception {
        String lifecycle = Files.readString(Path.of("src/main/java/org/example/update/UpdateLifecycle.java"));
        String dialogs = Files.readString(Path.of("src/main/java/org/example/update/UpdateDialogs.java"));
        assertTrue(lifecycle.contains("update.releaseNotesPending"));
        assertTrue(dialogs.contains("update.releaseNotesPending"));
        assertTrue(dialogs.contains("notes.setMinHeight(360)"));
        assertTrue(dialogs.contains("VBox.setVgrow(notes, Priority.ALWAYS)"));
        String presentation = Files.readString(Path.of("src/main/java/org/example/util/DialogPresentation.java"));
        assertTrue(presentation.contains("VBox.setVgrow(customContent, Priority.ALWAYS)"));
        assertFalse(org.example.update.ReleaseHighlights.forVersion(org.example.update.BuildInfo.version())
                .contains("unavailable offline"));
    }


    @Test void finalRuntimeAuthorityFor990OwnsRowsInputsEmptyStateDrawersAndReports() throws Exception {
        for (String theme : new String[]{"light-theme.css", "dark-theme.css"}) {
            String css = Files.readString(Path.of("src/main/resources/css", theme));
            assertTrue(css.contains("DSE ERP — FINAL RUNTIME VISUAL AUTHORITY"));
            assertTrue(css.contains(".erp-table-standard .table-row-cell .table-cell .label"));
            assertTrue(css.contains(".erp-table-standard .row-actions"));
            assertTrue(css.contains(".date-picker.approved-input > .text-field"));
            assertTrue(css.contains(".erp-table-standard .placeholder"));
            assertTrue(css.contains(".erp-detail-drawer-card { -fx-padding: 18; }"));
            assertTrue(css.contains(".report-filter-actions"));
            assertTrue(css.contains(".security-settings-panel"));
        }
    }

    @Test void themeNavigationAndDynamicEnhancementHaveSingleStableRuntimeOwnership() throws Exception {
        String enhancer = Files.readString(Path.of("src/main/java/org/example/util/ProfessionalUiEnhancer.java"));
        String theme = Files.readString(Path.of("src/main/java/org/example/theme/ThemeManager.java"));
        String navigation = Files.readString(Path.of("src/main/java/org/example/navigation/NavigationManager.java"));
        assertTrue(enhancer.contains("installDynamicChildEnhancement(parent)"));
        assertTrue(enhancer.contains("walk(added);"));
        assertFalse(enhancer.contains("enhance(added);"));
        assertTrue(theme.contains("APPLIED_THEME_KEY"));
        assertTrue(theme.contains("!themeUrl.equals(alreadyApplied)"));
        assertTrue(navigation.contains("PENDING_NAVIGATION"));
        assertTrue(navigation.contains("latest destination retained"));
    }

    @Test void salesAndPurchaseRegistersExposeReturnStatusWithoutReturnReloadLoops() throws Exception {
        String salesFxml = Files.readString(Path.of("src/main/resources/fxml/pages/SalesList.fxml"));
        String purchaseFxml = Files.readString(Path.of("src/main/resources/fxml/pages/PurchaseList.fxml"));
        String sales = Files.readString(Path.of("src/main/java/org/example/controller/SalesListController.java"));
        String purchase = Files.readString(Path.of("src/main/java/org/example/controller/PurchaseListController.java"));
        String salesReturns = Files.readString(Path.of("src/main/java/org/example/controller/SalesReturnsController.java"));
        String purchaseReturns = Files.readString(Path.of("src/main/java/org/example/controller/PurchaseReturnsController.java"));
        assertTrue(salesFxml.contains("fx:id=\"cmbReturnStatus\""));
        assertTrue(purchaseFxml.contains("fx:id=\"cmbReturnStatus\""));
        assertTrue(sales.contains("cmbReturnStatus"));
        assertTrue(purchase.contains("cmbReturnStatus"));
        assertFalse(salesReturns.contains("loadedOnce"));
        assertFalse(purchaseReturns.contains("loadedOnce"));
        assertTrue(salesReturns.contains("all.isEmpty()||(reusedFromCache&&ScreenRefreshPolicy.shouldRefresh"));
        assertTrue(purchaseReturns.contains("all.isEmpty()||(reusedFromCache&&ScreenRefreshPolicy.shouldRefresh"));
        assertFalse(salesReturns.contains("reusedFromCache||all.isEmpty()"));
        assertFalse(purchaseReturns.contains("reusedFromCache||all.isEmpty()"));
    }

    @Test void reportsAndRecoveryUseExplicitSemanticsFor990() throws Exception {
        String icons = Files.readString(Path.of("src/main/java/org/example/util/IconFactory.java"));
        String reports = Files.readString(Path.of("src/main/java/org/example/controller/ReportsController.java"));
        String recovery = Files.readString(Path.of("src/main/resources/fxml/pages/BackupRestore.fxml"));
        assertTrue(icons.contains("case \"favorite\" -> \"fas-star\""));
        assertTrue(icons.contains("case \"invoice\" -> \"fas-file-invoice-dollar\""));
        assertTrue(reports.contains("IconFactory.compactIcon(\"favorite\",15)"));
        assertFalse(reports.contains("★ Favorites"));
        assertTrue(recovery.contains("Recover Server to LOCAL"));
    }

    @Test void reportingDashboardKpisAndFilterActionsStaySeparatedFor991() throws Exception {
        String reportsFxml = Files.readString(Path.of("src/main/resources/fxml/pages/Reports.fxml"));
        String reportsController = Files.readString(Path.of("src/main/java/org/example/controller/ReportsController.java"));
        assertTrue(reportsFxml.contains("styleClass=\"report-filter-actions\""));
        assertTrue(reportsFxml.contains("fx:id=\"reportDashboardKpiGrid\""));
        assertEquals(6, count(reportsFxml, "percentWidth=\"16.666\""),
                "Only the Dashboard filter grid should keep six static columns; KPI widths are runtime-owned.");
        assertTrue(reportsController.contains("ResponsiveKpiLayoutManager.install(reportDashboardKpiGrid)"));
        assertTrue(reportsController.contains("ProfessionalUiEnhancer.refreshTableDecorations(tblSales)"));
        assertTrue(reportsController.contains("ProfessionalUiEnhancer.refreshTableDecorations(tblPurchases)"));
        for (String theme : new String[]{"light-theme.css", "dark-theme.css"}) {
            String css = Files.readString(Path.of("src/main/resources/css", theme));
            assertTrue(css.contains(".report-filter-actions { -fx-padding: 10 0 4 0; }"));
        }
    }

    @Test void semanticTableValuesFollowHeaderColourFamilyFor991() throws Exception {
        String enhancer = Files.readString(Path.of("src/main/java/org/example/util/ProfessionalUiEnhancer.java"));
        assertTrue(enhancer.contains("new SemanticValueCell(valueSemantic)"));
        assertTrue(enhancer.contains("erp-table-value-colour-"));
        assertTrue(enhancer.contains("refreshTableDecorations(TableView<?> table)"));
        for (String theme : new String[]{"light-theme.css", "dark-theme.css"}) {
            String css = Files.readString(Path.of("src/main/resources/css", theme));
            assertTrue(css.contains("table-cell.erp-table-value-colour-blue"));
            assertTrue(css.contains("table-cell.erp-table-value-colour-green"));
            assertTrue(css.contains("table-cell.erp-table-value-colour-orange"));
            assertTrue(css.contains("table-cell.erp-table-value-colour-purple"));
            assertTrue(css.contains("table-cell.erp-table-value-colour-pink"));
            assertTrue(css.contains("table-cell.erp-table-value-colour-teal"));
            assertTrue(css.contains("table-cell.erp-table-value-colour-indigo"));
        }
    }

    @Test void performanceAndResponsiveAuthoritiesAreCentralizedFor992() throws Exception {
        String sales = Files.readString(Path.of("src/main/java/org/example/controller/SalesListController.java"));
        String purchase = Files.readString(Path.of("src/main/java/org/example/controller/PurchaseListController.java"));
        String salesService = Files.readString(Path.of("src/main/java/org/example/service/SalesService.java"));
        String purchaseService = Files.readString(Path.of("src/main/java/org/example/service/PurchaseService.java"));
        String tableLayout = Files.readString(Path.of("src/main/java/org/example/util/DynamicTableLayoutManager.java"));
        String kpiLayout = Files.readString(Path.of("src/main/java/org/example/util/ResponsiveKpiLayoutManager.java"));
        String navigation = Files.readString(Path.of("src/main/java/org/example/navigation/NavigationManager.java"));
        String enhancer = Files.readString(Path.of("src/main/java/org/example/util/ProfessionalUiEnhancer.java"));

        assertTrue(sales.contains("reloadPage(false)"), "Sales pagination must be able to skip summary work.");
        assertTrue(purchase.contains("reloadPage(false)"), "Purchase pagination must be able to skip summary work.");
        assertTrue(sales.contains("service.page(requestedPage,size") && sales.contains("includeSummary"));
        assertTrue(purchase.contains("service.page(requested,size") && purchase.contains("includeSummary"));
        assertTrue(salesService.contains("includeSummary&&!PlatformUiSupport.isMac()"), "macOS must not request Sales chart aggregates that it does not render.");
        assertTrue(purchaseService.contains("includeSummary,false"));

        assertFalse(tableLayout.contains("TABLE_CHROME_ALLOWANCE"), "Dynamic table width must use live JavaFX viewport geometry, not fixed OS chrome.");
        assertTrue(tableLayout.contains("contentViewportWidth(table)"));
        assertTrue(tableLayout.contains("fitDenseViewport"));
        assertTrue(tableLayout.contains("closeResidual"));
        assertTrue(tableLayout.contains("SAMPLED_CONTENT_WIDTH"));

        assertTrue(kpiLayout.contains("region.setPrefWidth(0)"), "Grid KPI cards must not keep a competing preferred width.");
        assertTrue(kpiLayout.contains("column.setPercentWidth(100.0d / columns)"));
        assertTrue(navigation.contains("ProfessionalUiEnhancer.enhance(page)"), "Prepared pages must not bypass shared UI governance.");
        assertTrue(enhancer.contains("erp-semantic-header-guard"), "Semantic table headers must be reasserted for recreated/tabbed skins.");
    }

    @Test void backendRegisterPagingAvoidsWholeChargeTableScansFor992() throws Exception {
        String operations = Files.readString(Path.of("../server/src/main/java/org/example/server/operations/BusinessOperationsService.java"));
        String salesCharges = Files.readString(Path.of("../server/src/main/java/org/example/server/persistence/repository/SalesChargeRepository.java"));
        String purchaseCharges = Files.readString(Path.of("../server/src/main/java/org/example/server/persistence/repository/PurchaseChargeRepository.java"));
        String controller = Files.readString(Path.of("../server/src/main/java/org/example/server/operations/BusinessOperationsController.java"));

        assertTrue(salesCharges.contains("findBySalesIdInOrderBySalesIdAscSequenceNoAscIdAsc"));
        assertTrue(purchaseCharges.contains("findByPurchaseIdInOrderByPurchaseIdAscSequenceNoAscIdAsc"));
        assertTrue(operations.contains("salesCharges.findBySalesIdInOrderBySalesIdAscSequenceNoAscIdAsc(ids)"));
        assertTrue(operations.contains("purchaseCharges.findByPurchaseIdInOrderByPurchaseIdAscSequenceNoAscIdAsc(ids)"));
        assertFalse(operations.contains("for(var e:salesCharges.findAll())"));
        assertFalse(operations.contains("for(var e:purchaseCharges.findAll())"));
        assertTrue(controller.contains("includeSummary"));
        assertTrue(controller.contains("includeCharts"));
        assertTrue(controller.contains("includeOptions"));
    }

    @Test void cachedScreensDoNotBlindlyReloadFor992() throws Exception {
        String sales = Files.readString(Path.of("src/main/java/org/example/controller/SalesListController.java"));
        String dashboard = Files.readString(Path.of("src/main/java/org/example/controller/DashboardHomeController.java"));
        String notifications = Files.readString(Path.of("src/main/java/org/example/controller/NotificationCenterController.java"));
        String reports = Files.readString(Path.of("src/main/java/org/example/controller/ReportsController.java"));

        assertFalse(sales.contains("if(reusedFromCache || allSales.isEmpty()"));
        assertTrue(sales.contains("allSales.isEmpty() || (reusedFromCache && ScreenRefreshPolicy.shouldRefresh"));
        assertTrue(dashboard.contains("reusedFromCache && ScreenRefreshPolicy.shouldRefresh"));
        assertTrue(notifications.contains("reused&&ScreenRefreshPolicy.shouldRefresh"));
        assertTrue(reports.contains("if(!filtersLoaded)loadFiltersAsync()"));
        assertTrue(reports.contains("if(index == 2 && !savedReportsLoaded) loadSavedReports()"));
        assertTrue(reports.contains("if(index == 3 && !schedulesLoaded) loadSchedules()"));
    }

    private static int count(String value, String needle) {
        int count = 0, start = 0;
        while ((start = value.indexOf(needle, start)) >= 0) {
            count++;
            start += needle.length();
        }
        return count;
    }

}
