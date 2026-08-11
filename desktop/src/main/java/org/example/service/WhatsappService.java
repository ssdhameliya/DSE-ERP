package org.example.service;

import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper to build WhatsApp wa.me links and open them in the default browser.
 * Also opens the containing folder for the provided PDF so user can attach it quickly.
 */
public final class WhatsappService {

    private WhatsappService() {}

    public static void openWhatsappWithMessage(String phoneNumber, String message, Path pdfPath) throws IOException {
        openWhatsappWithMessage(phoneNumber, message, pdfPath, null);
    }

    /**
     * Opens a prepared WhatsApp chat and places the generated document plus
     * optional Settings QR image on the clipboard for a quick paste/attach.
     */
    public static void openWhatsappWithMessage(
        String phoneNumber,
        String message,
        Path documentPath,
        Path qrImagePath
    ) throws IOException {
        // phoneNumber in international format without +, e.g. 919999888777
        String encoded = URLEncoder.encode(message, StandardCharsets.UTF_8);
        String waUrl = "https://wa.me/" + phoneNumber + "?text=" + encoded;
        if (Desktop.isDesktopSupported()) {
            Desktop dt = Desktop.getDesktop();
            copyAttachmentsToClipboard(documentPath, qrImagePath);
            try {
                dt.browse(URI.create(waUrl));
            } catch (Exception e) {
                // ignore browse failures
            }
            if (documentPath != null) {
                File f = documentPath.toFile();
                File parent = f.getParentFile();
                if (parent != null && parent.exists()) {
                    try { dt.open(parent); } catch (Exception ignore) {}
                }
            }
        } else {
            throw new IOException("Desktop is not supported on this platform");
        }
    }

    private static void copyAttachmentsToClipboard(Path documentPath, Path qrImagePath) {
        List<File> files = new ArrayList<>();
        addReadableFile(files, documentPath);
        addReadableFile(files, qrImagePath);
        if (files.isEmpty()) return;
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new FileListTransferable(files), null);
        } catch (Exception ignored) {
            // Clipboard availability must never prevent the WhatsApp handoff.
        }
    }

    private static void addReadableFile(List<File> files, Path path) {
        if (path == null) return;
        File file = path.toFile();
        if (file.isFile() && file.canRead()) files.add(file);
    }

    private record FileListTransferable(List<File> files) implements Transferable {
        @Override public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.javaFileListFlavor};
        }

        @Override public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.javaFileListFlavor.equals(flavor);
        }

        @Override public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
            return files;
        }
    }

}
