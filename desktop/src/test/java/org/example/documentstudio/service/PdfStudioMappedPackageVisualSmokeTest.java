package org.example.documentstudio.service;

import org.example.config.WorkspaceManager;
import org.example.documentstudio.model.ElementType;
import org.example.documentstudio.model.TemplateElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PdfStudioMappedPackageVisualSmokeTest {
    @Test
    @EnabledIfSystemProperty(named="dse.v15.template", matches=".+")
    void correctedMappedPackageKeepsSourceGridAndHasNoSourceStyleWarning() throws Exception {
        Path pkg = Path.of(System.getProperty("dse.v15.template")).toAbsolutePath();
        Path workspace = Files.createTempDirectory("dse-v15-package-smoke-");
        WorkspaceManager.configure(workspace);
        var template = PdfStudioTemplatePackageService.importPackage(pkg);
        TemplateElement table = template.getElements().stream().filter(e -> e.getType()==ElementType.ITEM_TABLE).findFirst().orElseThrow();
        assertEquals(7,table.getTableColumnBindings().size());
        assertTrue(table.isSourceStyleCaptured());
        assertTrue(table.isStrokeEnabled());
        assertEquals("#7599C6",table.getStrokeColor());
        List<Double> expectedWidths = List.of(33.97,47.98,295.67,29.99,47.98,30.99,59.96);
        assertEquals(expectedWidths.size(), table.getTableColumnWidths().size());
        for (int i=0;i<expectedWidths.size();i++)
            assertEquals(expectedWidths.get(i), table.getTableColumnWidths().get(i), .05, "source width " + i);
        assertTrue(TemplateMappingValidationService.evaluate(template).issues().stream()
                .noneMatch(i -> "ITEM_SOURCE_STYLE".equals(i.requirementId())));

        Path out = workspace.resolve("v15-source-table-smoke.pdf");
        PdfStudioRenderer.renderSample(template,out);
        assertTrue(Files.isRegularFile(out));
        var vectors = PdfImageExtractionService.extractVectors(out,0);
        double bodyTop = table.getY()+table.getHeaderHeight()-1.5;
        double bodyBottom = table.getY()+table.getHeight()+1.5;
        List<Double> xs = new ArrayList<>();
        for (var region : vectors) for (var p : region.primitives()) {
            if (!p.stroked() || p.width()>2.5 || p.height()<80) continue;
            if (p.x()<table.getX()-2 || p.x()>table.getX()+table.getWidth()+2) continue;
            if (p.y()<bodyTop || p.y()>bodyBottom) continue;
            xs.add(p.x());
        }
        xs.sort(Comparator.naturalOrder());
        List<Double> unique = new ArrayList<>();
        for(double x:xs) if(unique.isEmpty() || Math.abs(unique.get(unique.size()-1)-x)>.75) unique.add(x);
        double[] expected={24.23,58.20,106.18,401.85,431.84,479.82,510.81,570.77};
        for(double want:expected) assertTrue(unique.stream().anyMatch(x->Math.abs(x-want)<=1.0),
                "Missing source grid boundary at x="+want+"; actual="+unique);
        assertTrue(unique.stream().noneMatch(x->x>110 && x<398),
                "No extra vertical rule is allowed inside PRODUCT DESCRIPTION; actual="+unique);
        System.out.println("V15_TEMPLATE_VISUAL_SMOKE_OK output="+out+" boundaries="+unique);
    }
}
