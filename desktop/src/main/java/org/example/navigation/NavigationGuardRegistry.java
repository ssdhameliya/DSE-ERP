package org.example.navigation;

import javafx.scene.Node;
import java.lang.ref.WeakReference;

/**
 * Single application-wide navigation guard owner. Screens with destructive or
 * in-progress workflows may register one guard while visible. The weak owner
 * reference prevents cached/closed pages from keeping the guard alive.
 */
public final class NavigationGuardRegistry {
    @FunctionalInterface public interface Guard { boolean allow(String destination); }
    private static WeakReference<Node> owner = new WeakReference<>(null);
    private static Guard guard;
    private NavigationGuardRegistry() {}
    public static synchronized void install(Node node, Guard value) { owner=new WeakReference<>(node); guard=value; }
    public static synchronized void clear(Node node) { if (node==null || owner.get()==node) { owner.clear(); guard=null; } }
    public static synchronized boolean allow(String destination) {
        Node node=owner.get(); Guard current=guard;
        if (current==null || node==null || node.getScene()==null || node.getScene().getWindow()==null || !node.getScene().getWindow().isShowing()) {
            owner.clear(); guard=null; return true;
        }
        return current.allow(destination);
    }
}
