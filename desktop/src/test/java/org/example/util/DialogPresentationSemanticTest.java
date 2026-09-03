package org.example.util;

import javafx.scene.control.Alert;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DialogPresentationSemanticTest {
    @Test
    void informationAlertCannotBecomeErrorBecauseSummaryContainsFailedZero() {
        assertEquals("notification", DialogPresentation.inferAlertSemantic(
                Alert.AlertType.INFORMATION,
                "Import completed successfully Processed 10 Passed 10 Failed: 0"));
    }

    @Test
    void warningAndErrorAlertTypesWinOverIncidentalKeywords() {
        assertEquals("warning", DialogPresentation.inferAlertSemantic(
                Alert.AlertType.WARNING,
                "Backup could not be completed"));
        assertEquals("error", DialogPresentation.inferAlertSemantic(
                Alert.AlertType.ERROR,
                "Backup operation failed"));
    }

    @Test
    void ordinaryConfirmationDoesNotBecomeSuccessBecauseTextContainsUnsaved() {
        assertEquals("confirmation", DialogPresentation.inferAlertSemantic(
                Alert.AlertType.CONFIRMATION,
                "Discard unsaved quotation changes?"));
    }

    @Test
    void destructiveConfirmationMayStillUseDeleteSemantic() {
        assertEquals("delete", DialogPresentation.inferAlertSemantic(
                Alert.AlertType.CONFIRMATION,
                "Delete this user?"));
    }
}
