package org.example.documentstudio.service;

import org.example.documentstudio.controller.ExcelDesignerFormulaReplacementSmoke;
import org.junit.jupiter.api.Test;

/** JUnit entry points for the legacy Excel smoke programs so release gates run them with Maven's exact dependency classpath. */
class ExcelLegacySmokeRegressionTest {
    @Test void formulaReplacementPersists() throws Exception { ExcelDesignerFormulaReplacementSmoke.main(new String[0]); }
    @Test void persistedTemplateRemainsImmutable() throws Exception { ExcelTemplateRendererMutationSmoke.main(new String[0]); }
    @Test void repeatingRowsRemainDistinctAndFormulaSafe() throws Exception { ExcelTemplateRepeatingRowsSmoke.main(new String[0]); }
    @Test void inactiveTotalsRenderVisibleZeroes() throws Exception { ExcelTemplateVisibleZeroSmoke.main(new String[0]); }
}
