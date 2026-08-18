package org.example.service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.Locale;

/** Upload-time validation policy for the four user-managed image roles. */
public final class BrandAssetPolicy {
    private static final long MB = 1024L * 1024L;
    private static final long HARD_MAX_BYTES = 10L * MB;
    private static final long WARN_BYTES = 5L * MB;

    private BrandAssetPolicy() { }

    public enum Role {
        APPLICATION_BANNER,
        COMPANY_LOGO,
        SIGNATURE,
        PAYMENT_QR
    }

    public record Inspection(int width, int height, long bytes, double ratio, List<String> warnings) {
        public String dimensions() { return width + " × " + height; }
        public String ratioLabel() { return String.format(Locale.ROOT, "%.2f:1", ratio); }
        public boolean hasWarnings() { return warnings != null && !warnings.isEmpty(); }
    }

    public static Inspection inspect(Path path, Role role) throws Exception {
        if (path == null || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("The selected image file does not exist.");
        }

        long bytes = Files.size(path);
        if (bytes <= 0) throw new IllegalArgumentException("The selected image file is empty.");
        if (bytes > HARD_MAX_BYTES) {
            throw new IllegalArgumentException("The image is larger than 10 MB. Please optimize the image before uploading it.");
        }

        int width;
        int height;
        try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
            if (input == null) {
                throw new IllegalArgumentException("The selected file could not be opened as an image.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("The selected file is not a supported PNG or JPEG image.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                width = reader.getWidth(0);
                height = reader.getHeight(0);
            } finally {
                reader.dispose();
            }
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("The selected file has invalid image dimensions.");
        }

        double ratio = (double) width / (double) height;
        List<String> warnings = new ArrayList<>();

        if (bytes > WARN_BYTES) {
            warnings.add("The image is larger than 5 MB and may use unnecessary memory during startup.");
        }

        switch (role) {
            case APPLICATION_BANNER -> {
                if (width < 1000) warnings.add("Application branding works best at 1000 px or more in width (1600 × 320 recommended).");
                if (height < 180) warnings.add("Application branding works best at 180 px or more in height.");
                double delta = Math.abs(ratio - BrandImagePresenter.APPLICATION_BANNER_RATIO)
                        / BrandImagePresenter.APPLICATION_BANNER_RATIO;
                if (delta > 0.12) {
                    warnings.add("The source ratio is " + String.format(Locale.ROOT, "%.2f:1", ratio)
                            + ". The application banner uses 5:1 and will center-crop the source to fill the complete width without stretching.");
                }
            }
            case COMPANY_LOGO -> {
                if (width < 500) warnings.add("A company logo of at least 500 px width is recommended for crisp PDFs and previews.");
            }
            case SIGNATURE -> {
                if (width < 750 || height < 200) warnings.add("For a large, crisp PDF signature, 750 × 200 px or higher is recommended.");
            }
            case PAYMENT_QR -> {
                if (width < 300 || height < 300) warnings.add("A QR image of at least 300 × 300 px is recommended.");
                if (Math.abs(ratio - 1.0) > 0.08) warnings.add("Payment QR images should be square (1:1) to avoid excessive empty space.");
            }
        }

        return new Inspection(width, height, bytes, ratio, List.copyOf(warnings));
    }

    public static String recommendation(Role role) {
        return switch (role) {
            case APPLICATION_BANNER -> "Recommended: 1600 × 320 (5:1). The preview is the same center-cropped, full-width presentation used on Splash/Login/Registration/Email screens.";
            case COMPANY_LOGO -> "Recommended: transparent PNG, at least 500 px wide. The full logo is always preserved and never cropped.";
            case SIGNATURE -> "Recommended: transparent PNG, 750 × 200 px or higher. Blank outer canvas is trimmed only while rendering the PDF; the stored signature file is never changed.";
            case PAYMENT_QR -> "Recommended: square PNG, at least 300 × 300 px. QR images are always displayed with contain semantics.";
        };
    }
}
