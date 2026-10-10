package org.example.shared.barcode;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;

/**
 * Pure Java Code-128B barcode encoder and renderer.
 * Complies with ISO/IEC 15417 with zero third-party dependencies.
 */
public final class Code128Encoder {

    // 107 patterns: each has 6 digits representing widths of 3 bars and 3 spaces
    private static final String[] PATTERNS = {
        "212222", "222122", "222221", "121223", "121322", "131222", "122213", "122312", "132212", "221213", // 0-9
        "221312", "231212", "112232", "122132", "122231", "113222", "123122", "123221", "223211", "221132", // 10-19
        "221231", "213212", "223112", "312131", "311222", "321122", "321221", "312212", "322112", "322211", // 20-29
        "212123", "212321", "232121", "111323", "131123", "131321", "112313", "132113", "132311", "211313", // 30-39
        "231113", "231311", "112133", "112331", "132131", "113123", "113321", "133121", "313121", "211331", // 40-49
        "231131", "213113", "213311", "213131", "311123", "311321", "331121", "312113", "312311", "332111", // 50-59
        "314111", "221411", "431111", "111224", "111422", "121124", "121421", "141122", "141221", "112214", // 60-69
        "112412", "122114", "122411", "142112", "142211", "241211", "221114", "413111", "241112", "134111", // 70-79
        "111242", "121142", "121241", "114212", "124112", "124211", "411212", "421112", "421211", "212141", // 80-89
        "214121", "412121", "111143", "111341", "131141", "114113", "114311", "411113", "411311", "113141", // 90-99
        "114131", "311141", "411131", "211412", "211214", "211232", "2331112" // 100-106 (106 is STOP pattern: 7 elements)
    };

    private static final int START_CODE_B = 104;

    private Code128Encoder() {}

    /**
     * Converts raw text into a binary boolean array representing barcode bars and spaces.
     */
    public static boolean[] encode(String text) {
        if (text == null || text.isEmpty()) text = "ITEM";

        // Calculate checksum
        int checksum = START_CODE_B;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int code = (c >= 32 && c <= 126) ? (c - 32) : 0;
            checksum += code * (i + 1);
        }
        int checkDigit = checksum % 103;

        StringBuilder modules = new StringBuilder();
        appendPattern(modules, PATTERNS[START_CODE_B]);

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int code = (c >= 32 && c <= 126) ? (c - 32) : 0;
            appendPattern(modules, PATTERNS[code]);
        }

        appendPattern(modules, PATTERNS[checkDigit]);
        appendPattern(modules, PATTERNS[106]); // STOP pattern

        boolean[] result = new boolean[modules.length()];
        for (int i = 0; i < modules.length(); i++) {
            result[i] = modules.charAt(i) == '1';
        }
        return result;
    }

    private static void appendPattern(StringBuilder sb, String pattern) {
        boolean bar = true;
        for (int i = 0; i < pattern.length(); i++) {
            int width = pattern.charAt(i) - '0';
            for (int w = 0; w < width; w++) {
                sb.append(bar ? '1' : '0');
            }
            bar = !bar;
        }
    }

    /**
     * Renders a Code-128 barcode as a BufferedImage.
     */
    public static BufferedImage renderImage(String text, int width, int height, boolean showText) {
        boolean[] bars = encode(text);
        BufferedImage img = new BufferedImage(Math.max(width, 150), Math.max(height, 50), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());

        int quietZone = 12;
        int usableWidth = img.getWidth() - (2 * quietZone);
        float moduleWidth = (float) usableWidth / bars.length;
        int barHeight = showText ? img.getHeight() - 22 : img.getHeight() - 8;

        g.setColor(Color.BLACK);
        for (int i = 0; i < bars.length; i++) {
            if (bars[i]) {
                int x1 = Math.round(quietZone + (i * moduleWidth));
                int x2 = Math.round(quietZone + ((i + 1) * moduleWidth));
                int w = Math.max(1, x2 - x1);
                g.fillRect(x1, 6, w, barHeight);
            }
        }

        if (showText) {
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
            int textWidth = g.getFontMetrics().stringWidth(text);
            int textX = Math.max(quietZone, (img.getWidth() - textWidth) / 2);
            g.drawString(text, textX, img.getHeight() - 5);
        }

        g.dispose();
        return img;
    }

    /**
     * Returns Base64-encoded PNG data URI for web/PDF display.
     */
    public static String toBase64Png(String text, int width, int height) {
        try {
            BufferedImage img = renderImage(text, width, height, true);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }
}
