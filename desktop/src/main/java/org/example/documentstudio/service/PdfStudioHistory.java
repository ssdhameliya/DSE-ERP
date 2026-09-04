package org.example.documentstudio.service;

import org.example.documentstudio.model.TemplateElement;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Deep-copy undo/redo history for PDF Studio template elements. */
public final class PdfStudioHistory {
    private final int limit;
    private final Deque<List<TemplateElement>> undo = new ArrayDeque<>();
    private final Deque<List<TemplateElement>> redo = new ArrayDeque<>();

    public PdfStudioHistory(int limit) {
        if (limit < 1) throw new IllegalArgumentException("History limit must be positive");
        this.limit = limit;
    }

    public void checkpoint(List<TemplateElement> current) {
        undo.push(snapshot(current));
        while (undo.size() > limit) undo.removeLast();
        redo.clear();
    }

    public List<TemplateElement> undo(List<TemplateElement> current) {
        if (undo.isEmpty()) return null;
        redo.push(snapshot(current));
        return undo.pop();
    }

    public List<TemplateElement> redo(List<TemplateElement> current) {
        if (redo.isEmpty()) return null;
        undo.push(snapshot(current));
        while (undo.size() > limit) undo.removeLast();
        return redo.pop();
    }

    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }
    public void clear() { undo.clear(); redo.clear(); }

    public static List<TemplateElement> snapshot(List<TemplateElement> source) {
        List<TemplateElement> copy = new ArrayList<>();
        if (source != null) for (TemplateElement element : source) copy.add(element.snapshotCopy());
        return copy;
    }
}
