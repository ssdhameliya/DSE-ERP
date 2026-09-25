package org.example.documentstudio.service;

import org.example.config.ConfigManager;
import org.example.util.ResourceLocator;
import org.example.update.BuildInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Provides the bundled, versioned PDF Studio mapping guide to end users. */
public final class PdfStudioHelpService {
    public static final String GUIDE_RESOURCE = "/help/pdf-studio-mapping-guide.pdf";
    public static final String GUIDE_FILE_NAME = "DSE-ERP-PDF-Studio-Mapping-Guide-" + BuildInfo.version() + ".pdf";

    private PdfStudioHelpService() { }

    /** Copies the bundled guide into the requested directory, replacing an older copy. */
    public static Path exportGuide(Path directory) throws IOException {
        if (directory == null) throw new IOException("A destination folder is required.");
        Files.createDirectories(directory);
        Path output = directory.resolve(GUIDE_FILE_NAME).toAbsolutePath().normalize();
        try (InputStream input = ResourceLocator.open(GUIDE_RESOURCE)) {
            if (input == null) throw new IOException("The PDF Studio mapping guide is missing from this application build.");
            Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
        }
        if (!Files.isRegularFile(output) || Files.size(output) < 100)
            throw new IOException("The PDF Studio mapping guide could not be written.");
        return output;
    }

    /** Uses the user's Downloads folder when available, otherwise the ERP Documents folder. */
    public static Path exportToUserDownloadLocation() throws IOException {
        Path downloads = Path.of(System.getProperty("user.home", "."), "Downloads").toAbsolutePath().normalize();
        try {
            return exportGuide(downloads);
        } catch (Exception ignored) {
            Path fallback = ConfigManager.getConfigFolder().resolve("Documents");
            return exportGuide(fallback);
        }
    }
}
