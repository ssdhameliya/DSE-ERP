package org.example.navigation;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import org.example.util.AppDialogService;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Application-wide owner of unsaved-work and in-progress navigation protection.
 * There is exactly one active workflow because the desktop shell exposes one active page.
 */
public final class UnsavedChangesManager {
    @FunctionalInterface public interface Guard { boolean allow(String destination); }

    private static WeakReference<Node> owner = new WeakReference<>(null);
    private static Guard guard;
    private static Tracker tracker;

    private UnsavedChangesManager() {}

    public static synchronized void installGuard(Node node, Guard value) {
        owner = new WeakReference<>(node);
        guard = value;
        tracker = null;
    }

    public static synchronized Tracker track(Node root, String screenName) {
        Tracker value = new Tracker(root, screenName);
        owner = new WeakReference<>(root);
        tracker = value;
        guard = null;
        return value;
    }

    public static synchronized void clear(Node node) {
        Node activeOwner = owner.get();
        if (belongsToActiveWorkflow(node, activeOwner)) clearInternal();
    }

    public static synchronized void clearAll() {
        clearInternal();
    }

    public static synchronized void markClean(Node node) {
        Node activeOwner = owner.get();
        if (tracker != null && belongsToActiveWorkflow(node, activeOwner)) {
            tracker.resetBaseline();
        }
    }

    public static synchronized void touch(Node node) {
        Node activeOwner = owner.get();
        if (tracker != null && belongsToActiveWorkflow(node, activeOwner)) {
            tracker.touch();
        }
    }

    static boolean belongsToActiveWorkflow(Node node, Node activeOwner) {
        if (node == null || activeOwner == node) return true;
        if (activeOwner == null) return false;
        for (Node current = node.getParent(); current != null; current = current.getParent()) {
            if (current == activeOwner) return true;
        }
        return false;
    }

    public static synchronized boolean allowNavigation(String destination) {
        Node node = owner.get();
        if (!active(node)) {
            clearInternal();
            return true;
        }
        if (guard != null) return guard.allow(destination);
        if (tracker == null || !tracker.isDirty()) return true;
        String target = friendlyDestination(destination);
        boolean leave = AppDialogService.unsaved(node, tracker.screenName(), tracker.summary(), target);
        if (leave) clearInternal();
        return leave;
    }

    public static synchronized boolean allowExit(String destination) {
        return allowNavigation(destination == null || destination.isBlank() ? "exit the application" : destination);
    }

    public static synchronized boolean hasUnsavedChanges() {
        Node node = owner.get();
        if (!active(node)) return false;
        return tracker != null && tracker.isDirty();
    }

    private static boolean active(Node node) {
        return node != null && node.getScene() != null && node.getScene().getWindow() != null && node.getScene().getWindow().isShowing();
    }

    private static void clearInternal() {
        owner.clear();
        guard = null;
        tracker = null;
    }

    private static String friendlyDestination(String destination) {
        String value = destination == null ? "" : destination.trim();
        if (value.isBlank()) return "another screen";
        if (!value.contains("/") && !value.endsWith(".fxml")) return value;
        int slash = value.lastIndexOf('/');
        String name = slash >= 0 ? value.substring(slash + 1) : value;
        name = name.replaceFirst("(?i)\\.fxml$", "").replaceAll("([a-z])([A-Z])", "$1 $2").replace('-', ' ').replace('_', ' ').trim();
        return name.isBlank() ? "another screen" : name;
    }

    /**
     * Generic form-state snapshot that covers header fields, toggles, dates, combo values,
     * table/list contents and JavaFX property-backed line rows. Controllers reset the baseline
     * after a successful save or after an existing record has finished loading.
     */
    public static final class Tracker {
        private final Node root;
        private final String screenName;
        private Map<String,String> baseline;
        private long manualVersion;
        private long baselineManualVersion;
        private boolean userTouched;

        private Tracker(Node root, String screenName) {
            this.root = Objects.requireNonNull(root, "root");
            this.screenName = screenName == null || screenName.isBlank() ? "current screen" : screenName.trim();
            this.baseline = snapshot(root);
            installUserTouchDetection();
            settleBaseline(350);
            settleBaseline(1100);
            settleBaseline(2600);
        }

        public String screenName() { return screenName; }

        public void resetBaseline() {
            baseline = snapshot(root);
            baselineManualVersion = manualVersion;
            userTouched = false;
        }

        private void touch() {
            manualVersion++;
            userTouched = true;
        }

        public boolean isDirty() {
            return manualVersion != baselineManualVersion || !baseline.equals(snapshot(root));
        }

        private void installUserTouchDetection() {
            root.addEventFilter(javafx.scene.input.KeyEvent.KEY_TYPED, event -> userTouched = true);
            root.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_CLICKED, event -> {
                Object target = event.getTarget();
                if (target instanceof TextInputControl || target instanceof ComboBoxBase<?> || target instanceof ChoiceBox<?>
                        || target instanceof CheckBox || target instanceof ToggleButton || target instanceof Spinner<?>
                        || target instanceof TableView<?>) userTouched = true;
            });
        }

        private void settleBaseline(double millis) {
            javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.millis(millis));
            pause.setOnFinished(event -> { if (!userTouched) resetBaseline(); });
            pause.play();
        }

        public String summary() {
            Map<String,String> current = snapshot(root);
            int fields = 0;
            int tables = 0;
            for (Map.Entry<String,String> entry : current.entrySet()) {
                String before = baseline.get(entry.getKey());
                if (!Objects.equals(before, entry.getValue())) {
                    if (entry.getKey().contains(":table:") || entry.getKey().contains(":list:")) tables++;
                    else fields++;
                }
            }
            for (String key : baseline.keySet()) if (!current.containsKey(key)) {
                if (key.contains(":table:") || key.contains(":list:")) tables++;
                else fields++;
            }
            List<String> parts = new ArrayList<>();
            if (fields > 0) parts.add(fields + (fields == 1 ? " field value changed" : " field values changed"));
            if (tables > 0) parts.add(tables + (tables == 1 ? " line/list section changed" : " line/list sections changed"));
            if (manualVersion != baselineManualVersion) parts.add("attachments or workflow details changed");
            if (parts.isEmpty()) parts.add("Unsaved work is present");
            return String.join("  •  ", parts);
        }
    }

    private static Map<String,String> snapshot(Node root) {
        LinkedHashMap<String,String> values = new LinkedHashMap<>();
        walk(root, "0", values, new IdentityHashMap<>());
        return values;
    }

    private static void walk(Node node, String path, Map<String,String> out, IdentityHashMap<Node,Boolean> seen) {
        if (node == null || seen.put(node, Boolean.TRUE) != null) return;
        String id = node.getId() == null || node.getId().isBlank() ? path : node.getId();
        if (node instanceof TextInputControl c) out.put(id + ":text", safe(c.getText()));
        else if (node instanceof ComboBoxBase<?> c) out.put(id + ":combo", fingerprint(c.getValue()));
        else if (node instanceof ChoiceBox<?> c) out.put(id + ":choice", fingerprint(c.getValue()));
        else if (node instanceof CheckBox c) out.put(id + ":check", Boolean.toString(c.isSelected()));
        else if (node instanceof ToggleButton c) out.put(id + ":toggle", Boolean.toString(c.isSelected()));
        else if (node instanceof Spinner<?> c) out.put(id + ":spinner", fingerprint(c.getValue()));
        else if (node instanceof TableView<?> table && (table.isEditable() || "tableLines".equals(table.getId())
                || Boolean.TRUE.equals(table.getProperties().get("dse.track.unsaved"))))
            out.put(id + ":table:" + table.getItems().size(), fingerprintCollection(table.getItems()));
        else if (node instanceof ListView<?> list && Boolean.TRUE.equals(list.getProperties().get("dse.track.unsaved")))
            out.put(id + ":list:" + list.getItems().size(), fingerprintCollection(list.getItems()));

        int index = 0;
        if (node instanceof ScrollPane scroll) {
            walk(scroll.getContent(), path + ".s", out, seen);
        } else if (node instanceof TabPane tabs) {
            for (Tab tab : tabs.getTabs()) walk(tab.getContent(), path + ".t" + index++, out, seen);
        } else if (node instanceof TitledPane titled) {
            walk(titled.getContent(), path + ".p", out, seen);
        } else if (node instanceof Accordion accordion) {
            for (TitledPane pane : accordion.getPanes()) walk(pane.getContent(), path + ".a" + index++, out, seen);
        } else if (node instanceof SplitPane split) {
            for (Node item : split.getItems()) walk(item, path + ".sp" + index++, out, seen);
        }
        if (node instanceof Parent parent) {
            index = 0;
            for (Node child : parent.getChildrenUnmodifiable()) walk(child, path + "." + index++, out, seen);
        }
    }

    private static String fingerprintCollection(Collection<?> items) {
        StringBuilder b = new StringBuilder();
        int count = 0;
        for (Object item : items) {
            if (count++ > 250) { b.append("…"); break; }
            b.append(fingerprint(item)).append('|');
        }
        return b.toString();
    }

    private static String fingerprint(Object value) {
        if (value == null) return "";
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean
                || value instanceof Enum<?> || value instanceof TemporalAccessor || value.getClass().isPrimitive()) return value.toString();
        if (value instanceof javafx.beans.value.ObservableValue<?> observable) return fingerprint(observable.getValue());
        if (value instanceof Collection<?> collection) return fingerprintCollection(collection);
        Class<?> type = value.getClass();
        String pkg = type.getPackageName();
        if (pkg.startsWith("java.")) return value.toString();
        StringBuilder b = new StringBuilder(type.getSimpleName()).append('{');
        int captured = 0;
        for (Class<?> c = type; c != null && c != Object.class && captured < 40; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                try {
                    field.setAccessible(true);
                    Object fieldValue = field.get(value);
                    if (fieldValue instanceof javafx.beans.value.ObservableValue<?> observable) fieldValue = observable.getValue();
                    if (fieldValue == null || fieldValue instanceof CharSequence || fieldValue instanceof Number
                            || fieldValue instanceof Boolean || fieldValue instanceof Enum<?> || fieldValue instanceof TemporalAccessor) {
                        b.append(field.getName()).append('=').append(fieldValue).append(';');
                        captured++;
                    }
                } catch (RuntimeException | IllegalAccessException ignored) {
                    // A row fingerprint is best-effort; inaccessible implementation fields are irrelevant.
                }
            }
        }
        if (captured == 0) return value.toString();
        return b.append('}').toString();
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
