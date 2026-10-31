package org.example.ai;

import io.github.glaforge.antigravity.Agent;
import io.github.glaforge.antigravity.AgentConfig;
import io.github.glaforge.antigravity.AgentResponse;
import javafx.application.Platform;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import org.example.theme.ThemeManager;
import org.example.util.SystemDiagnosticsDialog.DiagnosticRow;
import org.example.util.UiTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Enterprise Antigravity AI Agent & Defect Diagnostic Service.
 *
 * <p>Integrates the Google Antigravity Java SDK ecosystem directly into the
 * DSE ERP JavaFX desktop client. Handles non-blocking, asynchronous agent
 * interactions, automatic defect severity categorization, and dynamic glassmorphic
 * UI row highlights.</p>
 */
public final class ErpAgentService {

    private static final Logger log = LoggerFactory.getLogger(ErpAgentService.class);
    private static volatile ErpAgentService instance;

    public enum DefectSeverity {
        NONE,
        INFO,
        WARNING,
        CRITICAL
    }

    public record DefectDiagnosis(
            DefectSeverity severity,
            String summary,
            String rootCause,
            String recommendedFix,
            boolean isAiGenerated
    ) {}

    private Agent agent;
    private boolean initialized = false;
    private boolean aiAvailable = false;

    private ErpAgentService() {
        initAgent();
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    public static ErpAgentService getInstance() {
        if (instance == null) {
            synchronized (ErpAgentService.class) {
                if (instance == null) {
                    instance = new ErpAgentService();
                }
            }
        }
        return instance;
    }

    private synchronized void initAgent() {
        if (initialized) return;
        try {
            String apiKey = System.getenv("GEMINI_API_KEY");
            if (apiKey == null || apiKey.isBlank()) {
                apiKey = System.getProperty("gemini.api.key", "");
            }

            AgentConfig.Builder builder = AgentConfig.builder()
                    .instructions("""
                            You are the authoritative DSE ERP Enterprise Diagnostic & Defect Assistant.
                            You analyze technical Java stack traces, database exceptions, transaction lockups,
                            and business logic defects in ERP documents (Sales, Purchases, Quotations, Returns, Tax allocations).
                            Return concise, highly accurate, and actionable guidance without fluff.
                            """);

            AgentConfig config = builder.build();
            this.agent = new Agent(config);
            this.aiAvailable = true;
            log.info("ErpAgentService: Google Antigravity Agent initialized successfully.");
        } catch (Throwable t) {
            this.aiAvailable = false;
            log.warn("ErpAgentService: Antigravity agent harness not active or missing API key. Falling back to deterministic heuristic analyzer. ({})", t.getMessage());
        } finally {
            this.initialized = true;
        }
    }

    public boolean isAiAvailable() {
        return aiAvailable && agent != null;
    }

    /**
     * Asynchronously analyzes a diagnostic row or exception using the Antigravity Agent,
     * safely dispatching to background executor and never blocking the JavaFX thread.
     */
    public CompletableFuture<DefectDiagnosis> diagnoseDiagnosticRow(DiagnosticRow row) {
        if (row == null) {
            return CompletableFuture.completedFuture(new DefectDiagnosis(
                    DefectSeverity.NONE, "No event data", "N/A", "N/A", false
            ));
        }

        DefectSeverity severity = "ERROR".equalsIgnoreCase(row.level()) || "FATAL".equalsIgnoreCase(row.level())
                ? DefectSeverity.CRITICAL
                : "WARN".equalsIgnoreCase(row.level()) ? DefectSeverity.WARNING : DefectSeverity.INFO;

        if (!isAiAvailable()) {
            return CompletableFuture.completedFuture(buildDeterministicDiagnosis(row, severity));
        }

        String prompt = String.format("""
                Analyze this enterprise ERP diagnostic event:
                - Level: %s
                - Source: %s
                - Component: %s
                - Event: %s
                - User-Facing Title: %s
                - Technical Detail: %s
                - Stack Trace: %s

                Provide:
                1. Concise Root Cause (1-2 sentences)
                2. Recommended Solution / Fix (1-2 bullet points)
                """,
                row.level(), row.source(), row.component(), row.event(),
                row.friendlyTitle(), row.technicalDetail(), row.rawFailure()
        );

        CompletableFuture<DefectDiagnosis> future = new CompletableFuture<>();

        try {
            agent.chat(prompt)
                    .orTimeout(20, TimeUnit.SECONDS)
                    .thenAccept(response -> {
                        String text = response != null ? response.text() : "";
                        String cause = extractSection(text, "Root Cause", row.friendlyExplanation());
                        String fix = extractSection(text, "Recommended Solution", row.actionableGuidance());
                        future.complete(new DefectDiagnosis(severity, row.friendlyTitle(), cause, fix, true));
                    })
                    .exceptionally(ex -> {
                        log.warn("Agent chat timed out or failed, using heuristic fallback: {}", ex.getMessage());
                        future.complete(buildDeterministicDiagnosis(row, severity));
                        return null;
                    });
        } catch (Throwable t) {
            future.complete(buildDeterministicDiagnosis(row, severity));
        }

        return future;
    }

    private DefectDiagnosis buildDeterministicDiagnosis(DiagnosticRow row, DefectSeverity severity) {
        String detail = row.technicalDetail();
        String failure = row.rawFailure();

        String cause = row.friendlyExplanation();
        String fix = row.actionableGuidance();

        if (failure.contains("ClosedChannelException") || detail.contains("ClosedChannelException")) {
            cause = "Backend server socket closed unexpectedly while handling the HTTP request.";
            fix = "Verify server port 58080 is active. The desktop client will automatically retry with exponential backoff.";
        } else if (failure.contains("ConnectException") || detail.contains("Connection refused")) {
            cause = "Desktop client could not establish TCP connection with Spring REST backend on localhost:58080.";
            fix = "Ensure the DSE ERP Spring server process is running and PostgreSQL port 55432 is healthy.";
        } else if (failure.contains("OptimisticLockingFailureException")) {
            cause = "Concurrent update detected: another workstation modified this document simultaneously.";
            fix = "Refresh the table view to load the latest revision before re-submitting your changes.";
        }

        return new DefectDiagnosis(severity, row.friendlyTitle(), cause, fix, false);
    }

    private String extractSection(String fullText, String header, String defaultVal) {
        if (fullText == null || fullText.isBlank()) return defaultVal;
        try {
            int idx = fullText.indexOf(header);
            if (idx != -1) {
                int start = fullText.indexOf(":", idx);
                if (start == -1) start = idx + header.length();
                else start += 1;
                int end = fullText.indexOf("\n\n", start);
                if (end == -1) end = fullText.length();
                String extracted = fullText.substring(start, end).trim();
                if (!extracted.isBlank()) return extracted;
            }
        } catch (Exception ignored) {}
        return defaultVal;
    }

    /**
     * Binds dynamic glassmorphic styling to any JavaFX TableView.
     * Highlights critical defects with translucent glass-red tint and warning anomalies
     * with translucent glass-amber tint without interfering with standard row selection.
     */
    public static <T> void installGlassDefectRowFactory(TableView<T> table, Function<T, DefectSeverity> severityFunction) {
        if (table == null || severityFunction == null) return;

        table.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setStyle("");
                    getStyleClass().removeAll("dse-glass-row-critical", "dse-glass-row-warning", "dse-glass-row-info");
                    return;
                }

                DefectSeverity severity = severityFunction.apply(item);
                boolean isDark = ThemeManager.isDark();

                getStyleClass().removeAll("dse-glass-row-critical", "dse-glass-row-warning", "dse-glass-row-info");

                if (severity == DefectSeverity.CRITICAL) {
                    getStyleClass().add("dse-glass-row-critical");
                    if (isDark) {
                        setStyle("-fx-background-color: rgba(239, 68, 68, 0.16); -fx-border-color: rgba(239, 68, 68, 0.50); -fx-border-width: 0 0 0 3.5;");
                    } else {
                        setStyle("-fx-background-color: rgba(254, 226, 226, 0.70); -fx-border-color: #ef4444; -fx-border-width: 0 0 0 3.5;");
                    }
                } else if (severity == DefectSeverity.WARNING) {
                    getStyleClass().add("dse-glass-row-warning");
                    if (isDark) {
                        setStyle("-fx-background-color: rgba(245, 158, 11, 0.14); -fx-border-color: rgba(245, 158, 11, 0.50); -fx-border-width: 0 0 0 3.5;");
                    } else {
                        setStyle("-fx-background-color: rgba(254, 243, 199, 0.70); -fx-border-color: #f59e0b; -fx-border-width: 0 0 0 3.5;");
                    }
                } else if (severity == DefectSeverity.INFO) {
                    getStyleClass().add("dse-glass-row-info");
                    if (isDark) {
                        setStyle("-fx-background-color: rgba(56, 189, 248, 0.08); -fx-border-color: rgba(56, 189, 248, 0.40); -fx-border-width: 0 0 0 3;");
                    } else {
                        setStyle("-fx-background-color: rgba(224, 242, 254, 0.60); -fx-border-color: #0284c7; -fx-border-width: 0 0 0 3;");
                    }
                } else {
                    setStyle("");
                }
            }
        });
    }

    public synchronized void shutdown() {
        if (agent != null) {
            try {
                agent.close();
                log.info("ErpAgentService: Antigravity agent safely closed.");
            } catch (Throwable t) {
                log.warn("ErpAgentService: Error closing agent: {}", t.getMessage());
            } finally {
                agent = null;
                initialized = false;
            }
        }
    }
}
