package org.example.documentstudio.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.example.documentstudio.engine.UniversalBlockDefinition;
import org.example.documentstudio.model.DocumentTemplate;
import org.example.documentstudio.model.ElementType;
import org.example.documentstudio.model.TemplateElement;
import org.example.util.AppDialogService;
import org.example.util.SemanticIconManager;

import java.util.List;

/**
 * Block-Wise Mapping Hub.
 * Shows distinct, modular block cards.
 * Clicking a block seamlessly switches into that block's focused mapping view
 * within the same workspace dialog (preventing modal-dialog collisions and lockups).
 */
public final class UniversalBlockWorkspace extends BorderPane {

    private final Node parentNode;
    private final DocumentTemplate template;
    private final Runnable onTemplateChanged;
    private final FlowPane cardsPane = new FlowPane(16, 16);
    private final ScrollPane cardsScroll;
    private final VBox banner;

    public static void show(Node parentNode, DocumentTemplate template, Runnable onTemplateChanged) {
        showInternal(parentNode, template, null, onTemplateChanged);
    }

    public static void showSingleBlock(Node parentNode, DocumentTemplate template, UniversalBlockDefinition block, Runnable onTemplateChanged) {
        showInternal(parentNode, template, block, onTemplateChanged);
    }

    private static void showInternal(Node parentNode, DocumentTemplate template, UniversalBlockDefinition initialBlock, Runnable onTemplateChanged) {
        UniversalBlockWorkspace workspace = new UniversalBlockWorkspace(parentNode, template, onTemplateChanged);
        ButtonType closeBtn = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        if (initialBlock != null) {
            workspace.openBlockView(initialBlock);
        }
        AppDialogService.workspace(
                parentNode,
                "block-hub",
                "PDF Studio — Block-Wise Mapping",
                "Map PDF Template Block-By-Block",
                "Configure template fields one logical block at a time without seeing unrelated fields.",
                workspace,
                960,
                640,
                closeBtn
        );
    }

    public UniversalBlockWorkspace(Node parentNode, DocumentTemplate template, Runnable onTemplateChanged) {
        this.parentNode = parentNode;
        this.template = template;
        this.onTemplateChanged = onTemplateChanged;

        setMinSize(900, 560);
        setPrefSize(940, 600);
        getStyleClass().add("dse-block-workspace-hub");

        // Top banner
        banner = buildBanner();
        setTop(banner);
        BorderPane.setMargin(banner, new Insets(0, 0, 16, 0));

        // Center flow of cards
        cardsPane.setPadding(new Insets(16));
        cardsPane.setAlignment(Pos.TOP_LEFT);

        cardsScroll = new ScrollPane(cardsPane);
        cardsScroll.setFitToWidth(true);
        cardsScroll.getStyleClass().add("dse-workspace-scroll");
        setCenter(cardsScroll);

        refreshCards();
    }

    private VBox buildBanner() {
        Label title = new Label("BLOCK-WISE TEMPLATE MAPPING");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");

        Label subtitle = new Label("Configure mappings block by block. Each block only displays its relevant fields, eliminating confusion.");
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569;");

        VBox bannerBox = new VBox(6, title, subtitle);
        bannerBox.setPadding(new Insets(16));
        bannerBox.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-width: 0 0 1 0;");
        return bannerBox;
    }

    public void refreshCards() {
        cardsPane.getChildren().clear();

        for (UniversalBlockDefinition block : UniversalBlockDefinition.standardBlocks()) {
            cardsPane.getChildren().add(buildBlockCard(block));
        }
    }

    private Node buildBlockCard(UniversalBlockDefinition block) {
        VBox card = new VBox(10);
        card.setPrefSize(270, 165);
        card.setMaxSize(270, 165);
        card.setPadding(new Insets(14));
        card.setStyle("-fx-background-color: #ffffff; -fx-border-color: #cbd5e1; -fx-border-radius: 8; -fx-background-radius: 8;");

        Node iconNode = SemanticIconManager.compact(block.icon(), 20);
        Label title = new Label(block.displayName());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #1e293b;");
        title.setWrapText(true);

        HBox titleRow = new HBox(8, iconNode, title);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label desc = new Label(block.description());
        desc.setWrapText(true);
        desc.setMaxHeight(45);
        desc.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        long mappedCount = countMappedFields(block);
        Label status = new Label(mappedCount > 0 ? (mappedCount + " fields mapped ✓") : "Not mapped");
        status.setStyle(mappedCount > 0
                ? "-fx-font-size: 11px; -fx-text-fill: #166534; -fx-font-weight: bold;"
                : "-fx-font-size: 11px; -fx-text-fill: #94a3b8;");

        Button btnOpen = new Button("Map Block →");
        btnOpen.setStyle("-fx-background-color: #2563eb; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-font-size: 11px; -fx-background-radius: 4; -fx-cursor: hand;");
        btnOpen.setOnAction(e -> openBlockView(block));

        HBox footer = new HBox(8, status, spacer, btnOpen);
        footer.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(titleRow, desc, spacer, footer);
        return card;
    }

    public void openBlockView(UniversalBlockDefinition block) {
        setTop(null);
        PdfBlockMappingDialog blockPane = new PdfBlockMappingDialog(
                template,
                block,
                () -> {
                    if (onTemplateChanged != null) onTemplateChanged.run();
                },
                this::showCardsView
        );
        setCenter(blockPane);
    }

    public void showCardsView() {
        setTop(banner);
        BorderPane.setMargin(banner, new Insets(0, 0, 16, 0));
        setCenter(cardsScroll);
        refreshCards();
    }

    private long countMappedFields(UniversalBlockDefinition block) {
        if (template == null || template.getElements() == null) return 0;
        if (block.isTableGrid()) {
            for (TemplateElement el : template.getElements()) {
                if (el != null && el.getType() == ElementType.ITEM_TABLE && el.getTableColumnBindings() != null) {
                    return el.getTableColumnBindings().stream().filter(c -> c.getFieldKey() != null && !c.getFieldKey().isBlank()).count();
                }
            }
            return 0;
        }

        return template.getElements().stream()
                .filter(el -> el != null && el.getType() != ElementType.ITEM_TABLE)
                .filter(el -> block.candidateFieldKeys().contains(el.getFieldKey()))
                .count();
    }
}
