package org.example.server.reporting;

import java.util.List;

/** REST contracts for persistent scheduled reporting. */
public final class ReportScheduleDtos {
    private ReportScheduleDtos() { }

    public record ScheduleRow(
            long id,
            String name,
            String savedReport,
            String reportTitle,
            String datePreset,
            String frequency,
            Integer dayOfWeek,
            Integer dayOfMonth,
            Integer monthOfYear,
            String time,
            String format,
            String delivery,
            String recipients,
            String nextRun,
            String lastRun,
            String status,
            String lastStatus,
            String lastError) { }

    public record ScheduleRequest(
            String name,
            String savedReport,
            String frequency,
            Integer dayOfWeek,
            Integer dayOfMonth,
            Integer monthOfYear,
            String time,
            String format,
            String delivery,
            String recipients) { }

    public record ScheduleSummary(
            long activeSchedules,
            String nextRun,
            String nextSchedule,
            long reportsThisMonth,
            long failuresLast30Days) { }

    public record SavedReportOption(String name, String reportId, String title, String datePreset) { }

    public record RunHistory(
            long id,
            long scheduleId,
            String startedAt,
            String finishedAt,
            String status,
            String reportTitle,
            String format,
            String delivery,
            long rowCount,
            String artifacts,
            String error,
            String triggeredBy) { }

    public record SchedulePage(List<ScheduleRow> schedules, ScheduleSummary summary) { }
    public record Result(boolean success, String message) { }
}
