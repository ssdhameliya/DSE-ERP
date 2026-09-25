package org.example.documentstudio.service;

import org.example.documentstudio.model.ElementType;
import org.example.documentstudio.model.PdfTextRegion;
import org.example.documentstudio.model.TemplateColumnBinding;
import org.example.documentstudio.model.TemplateElement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Physical source-table detector shared by PDF Studio bootstrap/import paths.
 *
 * <p>The source PDF decides the table rectangle, physical columns and paint. ERP semantics are
 * suggestions layered onto those physical columns afterwards. No customer/template coordinates
 * belong in Java code.</p>
 */
public final class PdfSourceTableDetectionService {
    private PdfSourceTableDetectionService() { }

    public record Detection(PdfAutoMappingService.ItemHeaderLayout header,
                            PdfImageExtractionService.VectorRegion grid,
                            TemplateElement table) { }

    public static Optional<Detection> detectItemTable(Path sourcePdf, int pageIndex) throws IOException {
        if (sourcePdf == null || !Files.isRegularFile(sourcePdf)) return Optional.empty();
        List<PdfTextRegion> text = PdfTextExtractionService.extract(sourcePdf, pageIndex);
        Optional<PdfAutoMappingService.ItemHeaderLayout> headerOpt = PdfAutoMappingService.detectItemHeaderLayout(text);
        if (headerOpt.isEmpty()) return Optional.empty();
        PdfAutoMappingService.ItemHeaderLayout header = headerOpt.get();
        List<PdfImageExtractionService.VectorRegion> vectors = PdfImageExtractionService.extractVectors(sourcePdf, pageIndex);
        PdfImageExtractionService.VectorRegion grid = sourceGridFor(header, vectors).orElse(null);
        if (grid != null) header = constrainHeaderToGrid(header, grid);

        double x = grid == null ? header.x() : grid.x();
        double y = grid == null ? header.y() : grid.y();
        double width = grid == null ? header.width() : grid.width();
        double height = grid == null
                ? Math.max(header.height() + inferSourceRowHeight(null, header, text) * 8.0, 160.0)
                : grid.height();
        double headerHeight = Math.max(8.0, (header.y() + header.height()) - y + 1.0);
        double rowHeight = inferSourceRowHeight(grid, header, text);

        TemplateElement table = TemplateElement.of(ElementType.ITEM_TABLE, pageIndex, x, y, width, height);
        table.setUseSourceTableDesign(true);
        table.setHeaderHeight(headerHeight);
        table.setRowHeight(rowHeight);
        table.setFillEnabled(false);
        table.setStrokeEnabled(false);

        if (grid != null) applySourceTableGeometry(table, grid, header);
        List<TemplateColumnBinding> bindings = sourceColumnBindings(header, table, grid);
        table.setTableColumnBindings(bindings);
        ManualTemplateMappingService.syncLegacyColumns(table);
        captureSourceStyle(table, grid);
        return Optional.of(new Detection(header, grid, table));
    }

    /**
     * Text extraction can place unrelated labels on the same visual Y-band as a table header.
     * When a real source grid is available, the grid owns the physical table envelope. Keep every
     * header cell inside that envelope (including unknown/unmapped columns) and discard only text
     * that is physically outside the source table. This preserves REVIEW_REQUIRED columns without
     * letting neighbouring invoice labels become fake table columns.
     */
    static PdfAutoMappingService.ItemHeaderLayout constrainHeaderToGrid(
            PdfAutoMappingService.ItemHeaderLayout header,
            PdfImageExtractionService.VectorRegion grid) {
        if (header == null || grid == null || header.cells() == null || header.cells().isEmpty()) return header;
        double left = grid.x() - 3.0;
        double right = grid.x() + grid.width() + 3.0;
        List<PdfAutoMappingService.ItemHeaderCell> kept = header.cells().stream()
                .filter(c -> c != null)
                .filter(c -> {
                    double center = c.x() + c.width() / 2.0;
                    return center >= left && center <= right;
                })
                .sorted(Comparator.comparingDouble(PdfAutoMappingService.ItemHeaderCell::x))
                .toList();
        if (kept.size() < 2) return header;
        double minX = kept.stream().mapToDouble(PdfAutoMappingService.ItemHeaderCell::x).min().orElse(header.x());
        double maxX = kept.stream().mapToDouble(c -> c.x() + c.width()).max().orElse(header.x() + header.width());
        double minY = kept.stream().mapToDouble(PdfAutoMappingService.ItemHeaderCell::y).min().orElse(header.y());
        double maxY = kept.stream().mapToDouble(c -> c.y() + c.height()).max().orElse(header.y() + header.height());
        return new PdfAutoMappingService.ItemHeaderLayout(header.pageIndex(), minX, minY,
                Math.max(1, maxX - minX), Math.max(1, maxY - minY), kept);
    }

    public static Optional<PdfImageExtractionService.VectorRegion> sourceGridFor(
            PdfAutoMappingService.ItemHeaderLayout header,
            List<PdfImageExtractionService.VectorRegion> vectors) {
        if (header == null || vectors == null) return Optional.empty();
        return vectors.stream()
                .filter(v -> v != null && (v.kind().contains("TABLE") || v.kind().contains("GRID")))
                .filter(v -> header.y() + header.height() >= v.y() - 5
                        && header.y() <= v.y() + Math.min(v.height(), 60))
                .filter(v -> overlap(v.x(), v.x() + v.width(), header.x(), header.x() + header.width())
                        >= Math.min(v.width(), header.width()) * .45)
                .max(Comparator.comparingDouble(v -> v.width() * v.height()));
    }


    /**
     * Returns the actual vertical source-grid boundaries for a physical table. Header text is not
     * allowed to define widths when the imported PDF already contains column rules.
     */
    public static List<Double> sourceColumnBoundaries(PdfImageExtractionService.VectorRegion grid,
                                                      PdfAutoMappingService.ItemHeaderLayout header) {
        if (grid == null || grid.primitives() == null || grid.primitives().isEmpty()) return List.of();
        double headerTop = header == null ? grid.y() : header.y();
        double headerBottom = header == null ? grid.y() + Math.min(40, grid.height()) : header.y() + header.height();
        double minVerticalSpan = Math.max(10.0, Math.min(grid.height() * .18, 48.0));
        List<Double> raw = grid.primitives().stream()
                .filter(p -> p != null && p.stroked())
                .filter(p -> p.width() <= Math.max(2.5, grid.width() * .008))
                .filter(p -> p.height() >= minVerticalSpan
                        || (p.y() <= headerTop + 3 && p.y() + p.height() >= headerBottom - 3))
                .map(PdfImageExtractionService.VectorPrimitive::x)
                .filter(Double::isFinite)
                .filter(x -> x >= grid.x() - 3 && x <= grid.x() + grid.width() + 3)
                .sorted()
                .toList();
        if (raw.size() < 2) return List.of();
        List<Double> unique = new ArrayList<>();
        for (double x : raw) {
            if (unique.isEmpty() || Math.abs(unique.get(unique.size() - 1) - x) > 1.0) unique.add(x);
            else unique.set(unique.size() - 1, (unique.get(unique.size() - 1) + x) / 2.0);
        }
        if (unique.size() < 2) return List.of();
        double span = unique.get(unique.size() - 1) - unique.get(0);
        if (span < Math.max(40, grid.width() * .70)) return List.of();
        return List.copyOf(unique);
    }

    /**
     * Align the Item Table envelope and header/body split to physical vector rules. This prevents
     * printed header text bounds from changing either column widths or first-row Y placement.
     */
    public static boolean applySourceTableGeometry(TemplateElement table, PdfImageExtractionService.VectorRegion grid,
                                                   PdfAutoMappingService.ItemHeaderLayout header) {
        if (table == null) return false;
        List<Double> boundaries = sourceColumnBoundaries(grid, header);
        boolean changed = false;
        if (boundaries.size() >= 2) {
            double left = boundaries.get(0), right = boundaries.get(boundaries.size() - 1);
            if (right > left + 20) {
                table.setX(left);
                table.setWidth(right - left);
                changed = true;
            }
        }
        double bodyTop = sourceBodyTop(grid, header);
        if (Double.isFinite(bodyTop) && bodyTop > table.getY() + 4 && bodyTop < table.getY() + Math.min(80, table.getHeight())) {
            table.setHeaderHeight(bodyTop - table.getY());
            changed = true;
        }
        double bodyBottom = sourceBodyBottom(grid, header);
        if (Double.isFinite(bodyBottom) && bodyBottom > table.getY() + table.getHeaderHeight() + 10) {
            table.setHeight(bodyBottom - table.getY());
            changed = true;
        }
        return changed;
    }

    /** Backward-compatible alias for callers/tests compiled against the previous helper name. */
    public static boolean applySourceColumnEnvelope(TemplateElement table, PdfImageExtractionService.VectorRegion grid,
                                                    PdfAutoMappingService.ItemHeaderLayout header) {
        return applySourceTableGeometry(table, grid, header);
    }

    /** Detect the physical header/body separator from the repeated long vertical body rules. */
    static double sourceBodyTop(PdfImageExtractionService.VectorRegion grid, PdfAutoMappingService.ItemHeaderLayout header) {
        if (grid == null) return Double.NaN;
        double minY = header == null ? grid.y() + 2 : header.y() + header.height() - 2;
        List<Double> starts = grid.primitives().stream()
                .filter(p -> p != null && p.stroked())
                .filter(p -> p.width() <= Math.max(2.5, grid.width() * .008))
                .filter(p -> p.height() >= Math.max(30, grid.height() * .35))
                .map(PdfImageExtractionService.VectorPrimitive::y)
                .filter(Double::isFinite)
                .filter(y -> y >= minY && y <= grid.y() + Math.min(90, grid.height() * .35))
                .sorted().toList();
        if (starts.isEmpty()) return Double.NaN;
        List<List<Double>> clusters = new ArrayList<>();
        for (double y : starts) {
            if (clusters.isEmpty() || Math.abs(clusters.get(clusters.size()-1).get(0) - y) > 1.0) {
                List<Double> cluster = new ArrayList<>(); cluster.add(y); clusters.add(cluster);
            } else clusters.get(clusters.size()-1).add(y);
        }
        return clusters.stream()
                .max(Comparator.<List<Double>>comparingInt(List::size).thenComparingDouble(c -> c.get(0)))
                .map(c -> c.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN))
                .orElse(Double.NaN);
    }

    /** Detect the physical body bottom from the repeated long vertical column rules. */
    static double sourceBodyBottom(PdfImageExtractionService.VectorRegion grid, PdfAutoMappingService.ItemHeaderLayout header) {
        if (grid == null) return Double.NaN;
        double minSpan = Math.max(30, grid.height() * .35);
        List<Double> ends = grid.primitives().stream()
                .filter(p -> p != null && p.stroked())
                .filter(p -> p.width() <= Math.max(2.5, grid.width() * .008))
                .filter(p -> p.height() >= minSpan)
                .map(p -> p.y() + p.height())
                .filter(Double::isFinite)
                .sorted().toList();
        if (ends.isEmpty()) return Double.NaN;
        List<List<Double>> clusters = new ArrayList<>();
        for (double y : ends) {
            if (clusters.isEmpty() || Math.abs(clusters.get(clusters.size()-1).get(0) - y) > 1.0) {
                List<Double> cluster = new ArrayList<>(); cluster.add(y); clusters.add(cluster);
            } else clusters.get(clusters.size()-1).add(y);
        }
        return clusters.stream()
                .max(Comparator.<List<Double>>comparingInt(List::size).thenComparingDouble(c -> c.get(0)))
                .map(c -> c.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN))
                .orElse(Double.NaN);
    }

    /**
     * Builds semantic bindings on top of the physical PDF grid. Vector boundaries own x/width;
     * detected header text supplies only the label and ERP suggestion. A physical column with no
     * recognized header remains REVIEW_REQUIRED instead of disappearing.
     */
    public static List<TemplateColumnBinding> sourceColumnBindings(PdfAutoMappingService.ItemHeaderLayout header,
                                                                   TemplateElement table,
                                                                   PdfImageExtractionService.VectorRegion grid) {
        if (header == null || table == null) return List.of();
        List<Double> boundaries = sourceColumnBoundaries(grid, header);
        if (boundaries.size() < 2) return ManualTemplateMappingService.sourceColumnBindings(header, table);

        List<PdfAutoMappingService.ItemHeaderCell> cells = header.cells().stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingDouble(PdfAutoMappingService.ItemHeaderCell::x))
                .toList();
        List<TemplateColumnBinding> out = new ArrayList<>();
        java.util.Set<String> used = new java.util.HashSet<>();
        for (int i = 0; i < boundaries.size() - 1; i++) {
            double left = boundaries.get(i), right = boundaries.get(i + 1);
            if (right - left < 4) continue;
            double center = (left + right) / 2.0;
            var candidates = cells.stream().filter(c -> {
                double cx = c.x() + c.width() / 2.0;
                return cx >= left - 1.5 && cx <= right + 1.5;
            }).toList();
            PdfAutoMappingService.ItemHeaderCell cell = candidates.stream()
                    .max(Comparator.<PdfAutoMappingService.ItemHeaderCell>comparingDouble(PdfAutoMappingService.ItemHeaderCell::confidence)
                            .thenComparingDouble(c -> -Math.abs((c.x() + c.width() / 2.0) - center)))
                    .orElse(null);
            String label = cell == null || cell.label() == null || cell.label().isBlank() ? "COLUMN " + (i + 1) : cell.label();
            double confidence = cell == null ? 0.0 : cell.confidence();
            String suggested = cell == null || cell.suggestedField() == null ? "" : cell.suggestedField();
            String key = confidence >= .90 ? suggested : "";
            if (!key.isBlank() && !used.add(key)) key = "";
            TemplateColumnBinding binding = new TemplateColumnBinding(label, key, Math.max(0, left - table.getX()),
                    right - left, ManualTemplateMappingService.itemHeaderAlignment(key), confidence);
            binding.setAutoDetectedFieldKey(suggested);
            binding.setAutoDetectedConfidence(confidence);
            binding.setMappingState(key.isBlank() ? "REVIEW_REQUIRED" : "AUTO");
            out.add(binding);
        }
        return out.size() >= 2 ? List.copyOf(out) : ManualTemplateMappingService.sourceColumnBindings(header, table);
    }

    public static double inferSourceRowHeight(PdfImageExtractionService.VectorRegion grid,
                                              PdfAutoMappingService.ItemHeaderLayout header,
                                              List<PdfTextRegion> regions) {
        if (grid != null) {
            List<Double> ys = grid.primitives().stream()
                    .filter(p -> p.height() <= 2.5 && p.width() > grid.width() * .35)
                    .map(PdfImageExtractionService.VectorPrimitive::y).distinct().sorted().toList();
            List<Double> gaps = new ArrayList<>();
            for (int i = 1; i < ys.size(); i++) {
                double gap = ys.get(i) - ys.get(i - 1);
                if (gap >= 10 && gap <= 60) gaps.add(gap);
            }
            if (!gaps.isEmpty()) {
                gaps.sort(Double::compare);
                return gaps.get(gaps.size() / 2);
            }
        }
        if (header != null && regions != null) {
            List<Double> rowYs = regions.stream()
                    .filter(r -> r != null && r.pageIndex() == header.pageIndex() && r.y() > header.y() + header.height() + 2)
                    .filter(r -> r.y() < header.y() + 160).map(PdfTextRegion::y).distinct().sorted().toList();
            for (int i = 1; i < rowYs.size(); i++) {
                double gap = rowYs.get(i) - rowYs.get(i - 1);
                if (gap >= 12 && gap <= 40) return gap;
            }
        }
        return 20.0;
    }


    /**
     * Infer a borderless table body from repeated physical alignment with the detected header.
     * No business labels are consulted: the stop condition is a break in the repeating row geometry.
     */
    public static double inferPhysicalTableBottom(PdfAutoMappingService.ItemHeaderLayout header,
                                                  List<PdfTextRegion> regions, double pageHeight) {
        if (header == null) return Math.max(0, pageHeight - 8);
        double bodyStart = header.y() + header.height();
        double rowHeight = inferSourceRowHeight(null, header, regions);
        double fallback = Math.min(pageHeight - 8, header.y() + Math.max(160, pageHeight * .38));
        if (regions == null || regions.isEmpty()) return fallback;

        List<PdfTextRegion> below = regions.stream()
                .filter(r -> r != null && r.pageIndex() == header.pageIndex())
                .filter(r -> r.y() > bodyStart + 1 && r.y() < pageHeight - 4)
                .sorted(Comparator.comparingDouble(PdfTextRegion::y).thenComparingDouble(PdfTextRegion::x)).toList();
        if (below.isEmpty()) return fallback;

        List<List<PdfTextRegion>> rows = new ArrayList<>();
        for (PdfTextRegion r : below) {
            List<PdfTextRegion> row = rows.isEmpty() ? null : rows.getLast();
            double center = r.y() + r.height() / 2.0;
            double rowCenter = row == null || row.isEmpty() ? Double.NaN
                    : row.stream().mapToDouble(v -> v.y() + v.height() / 2.0).average().orElse(center);
            if (row == null || Math.abs(center - rowCenter) > Math.max(3.5, r.height() * .45)) {
                row = new ArrayList<>(); rows.add(row);
            }
            row.add(r);
        }

        boolean started = false;
        double lastBottom = bodyStart;
        double maxGap = Math.max(34, rowHeight * 2.6);
        int required = Math.max(1, Math.min(2, header.cells().size()));
        for (List<PdfTextRegion> row : rows) {
            double y = row.stream().mapToDouble(PdfTextRegion::y).min().orElse(bodyStart);
            if (started && y - lastBottom > maxGap) break;
            int aligned = 0;
            for (PdfTextRegion r : row) {
                double cx = r.x() + r.width() / 2.0;
                boolean hit = header.cells().stream().anyMatch(c ->
                        cx >= c.x() - 8 && cx <= c.x() + c.width() + 8);
                if (hit) aligned++;
            }
            if (aligned >= required) {
                started = true;
                lastBottom = Math.max(lastBottom, row.stream().mapToDouble(v -> v.y() + v.height()).max().orElse(y));
            } else if (started && y - lastBottom > Math.max(18, rowHeight * 1.35)) break;
        }
        if (!started) return fallback;
        return Math.min(pageHeight - 8, Math.max(bodyStart + rowHeight, lastBottom + rowHeight * .55));
    }

    /** Capture only paint that is actually present in the source vector grid. */
    public static boolean captureSourceStyle(TemplateElement table, PdfImageExtractionService.VectorRegion grid) {
        if (table == null || grid == null) return false;
        var strokes = grid.primitives().stream().filter(PdfImageExtractionService.VectorPrimitive::stroked).toList();
        var fills = grid.primitives().stream().filter(PdfImageExtractionService.VectorPrimitive::filled).toList();
        boolean captured = false;
        if (!strokes.isEmpty()) {
            Map<String, Long> colors = strokes.stream().collect(Collectors.groupingBy(
                    PdfImageExtractionService.VectorPrimitive::strokeColor, LinkedHashMap::new, Collectors.counting()));
            colors.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).ifPresent(table::setStrokeColor);
            List<Double> widths = strokes.stream().map(PdfImageExtractionService.VectorPrimitive::strokeWidth)
                    .filter(w -> Double.isFinite(w) && w > 0).sorted().toList();
            if (!widths.isEmpty()) table.setStrokeWidth(widths.get(widths.size() / 2));
            table.setStrokeEnabled(true);
            captured = true;
        }
        if (!fills.isEmpty()) {
            Map<String, Long> colors = fills.stream().collect(Collectors.groupingBy(
                    PdfImageExtractionService.VectorPrimitive::fillColor, LinkedHashMap::new, Collectors.counting()));
            colors.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).ifPresent(table::setFillColor);
            table.setFillEnabled(true);
            captured = true;
        } else {
            // Absence of a source fill is itself meaningful; do not invent white paint.
            table.setFillEnabled(false);
        }
        table.setSourceStyleCaptured(captured);
        return captured;
    }

    private static double overlap(double a1, double a2, double b1, double b2) {
        return Math.max(0, Math.min(a2, b2) - Math.max(a1, b1));
    }
}
