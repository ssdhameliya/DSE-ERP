package org.example.documentstudio.service;

import org.apache.poi.ss.usermodel.Workbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;

/** In-memory workbook snapshot history, independent of JavaFX. */
public final class ExcelWorkbookHistory {
    private final int limit;
    private final Deque<byte[]> undo = new ArrayDeque<>();
    private final Deque<byte[]> redo = new ArrayDeque<>();

    public ExcelWorkbookHistory(int limit) {
        if (limit < 1) throw new IllegalArgumentException("History limit must be positive");
        this.limit = limit;
    }

    public void checkpoint(Workbook workbook) throws IOException {
        undo.addLast(snapshot(workbook));
        while (undo.size() > limit) undo.removeFirst();
        redo.clear();
    }

    public byte[] undo(Workbook current) throws IOException {
        if (undo.isEmpty()) return null;
        byte[] currentSnapshot = snapshot(current);
        byte[] previous = undo.removeLast();
        redo.addLast(currentSnapshot);
        return previous;
    }

    public byte[] redo(Workbook current) throws IOException {
        if (redo.isEmpty()) return null;
        byte[] currentSnapshot = snapshot(current);
        byte[] next = redo.removeLast();
        undo.addLast(currentSnapshot);
        while (undo.size() > limit) undo.removeFirst();
        return next;
    }

    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }
    public int undoSize() { return undo.size(); }
    public int redoSize() { return redo.size(); }
    public void clear() { undo.clear(); redo.clear(); }

    public static byte[] snapshot(Workbook workbook) throws IOException {
        if (workbook == null) throw new IOException("The Excel workbook is not open. Reopen the template before saving.");
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            return out.toByteArray();
        } catch (IllegalStateException error) {
            throw new IOException("The Excel workbook is no longer writable. Reopen the template and try again.", error);
        }
    }
}
