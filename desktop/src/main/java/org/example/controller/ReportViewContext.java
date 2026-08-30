package org.example.controller;

import org.example.api.reporting.ReportingApiClient.ReportRequest;

/** One-shot context used when Report Center/Saved Reports opens the unified viewer. */
public final class ReportViewContext {
    private static Selection pending;
    private ReportViewContext() {}

    public static synchronized void open(String reportId, String groupBy, String from, String to) {
        pending = new Selection(reportId, groupBy, from, to, null, null);
    }

    public static synchronized void openSaved(ReportRequest request, String datePreset) {
        pending = new Selection(request.reportId(), request.groupBy(), request.from(), request.to(), request, datePreset);
    }

    public static synchronized Selection consume() {
        Selection value = pending;
        pending = null;
        return value;
    }

    public record Selection(String reportId,String groupBy,String from,String to,ReportRequest request,String datePreset) {}
}
