package org.example;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** Builds one compact sign-off board per theme from the full-size UI captures. */
public final class ReviewContactSheet {
    private ReviewContactSheet() {}

    public static void main(String[] args) throws Exception {
        Path folder = Path.of(args.length == 0 ? "target/final-ui-review" : args[0]);
        for (String theme : List.of("light", "dark")) create(folder, theme);
        System.out.println("REVIEW_CONTACT_SHEETS_OK " + folder.toAbsolutePath());
    }

    private static void create(Path folder, String theme) throws Exception {
        List<Path> files;
        try (var stream = Files.list(folder)) {
            files = stream.filter(path -> path.getFileName().toString().startsWith(theme + "-"))
                .filter(path -> path.toString().endsWith(".png"))
                .sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
        }
        int columns = 4, cellWidth = 400, imageHeight = 225, labelHeight = 28;
        int rows = (files.size() + columns - 1) / columns;
        BufferedImage board = new BufferedImage(columns * cellWidth, rows * (imageHeight + labelHeight), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = board.createGraphics();
        graphics.setColor(theme.equals("dark") ? new Color(9, 22, 39) : new Color(242, 246, 251));
        graphics.fillRect(0, 0, board.getWidth(), board.getHeight());
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setFont(new Font("Segoe UI", Font.BOLD, 16));
        for (int index = 0; index < files.size(); index++) {
            int x = (index % columns) * cellWidth, y = (index / columns) * (imageHeight + labelHeight);
            BufferedImage screen = ImageIO.read(files.get(index).toFile());
            graphics.drawImage(screen, x, y, cellWidth, imageHeight, null);
            graphics.setColor(theme.equals("dark") ? Color.WHITE : new Color(15, 30, 52));
            String name = files.get(index).getFileName().toString().replace(theme + "-", "").replace(".png", "");
            graphics.drawString(name, x + 10, y + imageHeight + 20);
        }
        graphics.dispose();
        ImageIO.write(board, "png", folder.resolve(theme + "-all-screens-contact-sheet.png").toFile());
    }
}
