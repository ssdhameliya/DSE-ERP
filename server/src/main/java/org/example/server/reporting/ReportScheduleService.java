package org.example.server.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.InternetAddress;
import org.example.server.auth.SmtpMailService;
import org.example.server.persistence.JpaNativeRepository;
import org.example.server.security.CurrentUser;
import org.example.server.util.BusinessClock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

import static org.example.server.reporting.ReportScheduleDtos.*;
import static org.example.server.reporting.ReportingDtos.*;

/**
 * Persistent server-side scheduler for Saved Reports. The scheduler always
 * re-reads the Saved Report at execution time, resolves its relative date
 * preset, runs the same ReportingService used by the UI, then exports/delivers
 * the result. It never recalculates financial data independently.
 */
@Service
public class ReportScheduleService {
    private static final Set<String> FREQUENCIES = Set.of("DAILY", "WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY");
    private static final Set<String> FORMATS = Set.of("PDF", "XLSX", "PDF_XLSX", "CSV");
    private static final Set<String> DELIVERIES = Set.of("EMAIL", "ARCHIVE", "EMAIL_ARCHIVE");
    private static final DateTimeFormatter RUN_LABEL = DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm a z", Locale.ENGLISH);
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final JpaNativeRepository db;
    private final ReportingService reporting;
    private final ScheduledReportExportService exporter;
    private final SmtpMailService mail;
    private final ObjectMapper json = new ObjectMapper();
    private final TransactionTemplate tx;
    private final Path workspace;

    public ReportScheduleService(JpaNativeRepository db,
                                 ReportingService reporting,
                                 ScheduledReportExportService exporter,
                                 SmtpMailService mail,
                                 PlatformTransactionManager transactionManager,
                                 @Value("${dse.workspace.path:}") String workspace) {
        this.db = db;
        this.reporting = reporting;
        this.exporter = exporter;
        this.mail = mail;
        this.tx = new TransactionTemplate(transactionManager);
        this.workspace = workspace == null || workspace.isBlank() ? null : Path.of(workspace).toAbsolutePath().normalize();
    }

    @Transactional(readOnly = true)
    public SchedulePage pageForCurrentUser() {
        int userId = CurrentUser.require().id();
        return new SchedulePage(rows(userId), summary(userId));
    }

    @Transactional(readOnly = true)
    public List<SavedReportOption> savedReportsForCurrentUser() {
        int userId = CurrentUser.require().id();
        return savedReportOptions(userId);
    }

    @Transactional(readOnly = true)
    public List<RunHistory> historyForCurrentUser(long scheduleId) {
        int userId = CurrentUser.require().id();
        requireOwned(scheduleId, userId);
        return db.query("""
                SELECT r.id,r.schedule_id,r.started_at,COALESCE(r.finished_at,''),r.status,
                       COALESCE(r.report_title,''),COALESCE(r.output_format,''),COALESCE(r.delivery_mode,''),
                       COALESCE(r.row_count,0),COALESCE(r.artifacts,''),COALESCE(r.error_message,''),COALESCE(r.triggered_by,'')
                FROM report_schedule_run r
                JOIN report_schedule s ON s.id=r.schedule_id
                WHERE r.schedule_id=? AND s.user_id=?
                ORDER BY CAST(r.started_at AS timestamptz) DESC,r.id DESC
                LIMIT 100
                """, (r,i) -> new RunHistory(r.getLong(1), r.getLong(2), displayInstant(r.getString(3)),
                displayInstant(r.getString(4)), safe(r.getString(5)), safe(r.getString(6)), safe(r.getString(7)),
                safe(r.getString(8)), r.getLong(9), safe(r.getString(10)), safe(r.getString(11)), safe(r.getString(12))), scheduleId, userId);
    }

    @Transactional
    public ScheduleRow create(ScheduleRequest request) {
        int userId = CurrentUser.require().id(); String username = CurrentUser.require().username();
        Validated v = validate(request, userId, null);
        String next = instantText(nextRun(v, ZonedDateTime.now(BusinessClock.zone())));
        long id = db.queryForObject("""
                INSERT INTO report_schedule(user_id,schedule_name,saved_report_name,frequency,day_of_week,day_of_month,month_of_year,
                    run_time,output_format,delivery_mode,recipients,status,next_run_at,created_by,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id
                """, Long.class, userId, v.name, v.savedReport, v.frequency, v.dayOfWeek, v.dayOfMonth, v.monthOfYear,
                v.time.toString(), v.format, v.delivery, v.recipients, "ACTIVE", next, username,
                BusinessClock.nowUtcText(), BusinessClock.nowUtcText());
        return rowById(id, userId);
    }

    @Transactional
    public ScheduleRow update(long id, ScheduleRequest request) {
        int userId = CurrentUser.require().id(); requireOwned(id, userId);
        Validated v = validate(request, userId, id);
        String next = instantText(nextRun(v, ZonedDateTime.now(BusinessClock.zone())));
        int changed = db.update("""
                UPDATE report_schedule SET schedule_name=?,saved_report_name=?,frequency=?,day_of_week=?,day_of_month=?,month_of_year=?,
                    run_time=?,output_format=?,delivery_mode=?,recipients=?,next_run_at=?,updated_at=?
                WHERE id=? AND user_id=?
                """, v.name, v.savedReport, v.frequency, v.dayOfWeek, v.dayOfMonth, v.monthOfYear, v.time.toString(),
                v.format, v.delivery, v.recipients, next, BusinessClock.nowUtcText(), id, userId);
        if (changed != 1) throw new IllegalArgumentException("Schedule not found");
        return rowById(id, userId);
    }

    @Transactional
    public void pause(long id) {
        int userId = CurrentUser.require().id();
        if (db.update("UPDATE report_schedule SET status='PAUSED',updated_at=? WHERE id=? AND user_id=?", BusinessClock.nowUtcText(), id, userId) != 1)
            throw new IllegalArgumentException("Schedule not found");
    }

    @Transactional
    public void resume(long id) {
        int userId = CurrentUser.require().id(); ScheduleDefinition d = requireOwned(id, userId);
        String next = instantText(nextRun(d.validated(), ZonedDateTime.now(BusinessClock.zone())));
        if (db.update("UPDATE report_schedule SET status='ACTIVE',next_run_at=?,updated_at=? WHERE id=? AND user_id=?", next, BusinessClock.nowUtcText(), id, userId) != 1)
            throw new IllegalArgumentException("Schedule not found");
    }

    @Transactional
    public ScheduleRow duplicate(long id) {
        int userId = CurrentUser.require().id(); String username = CurrentUser.require().username();
        ScheduleDefinition source = requireOwned(id, userId);
        String name = uniqueCopyName(userId, source.name());
        String next = instantText(nextRun(source.validated().withName(name), ZonedDateTime.now(BusinessClock.zone())));
        long newId = db.queryForObject("""
                INSERT INTO report_schedule(user_id,schedule_name,saved_report_name,frequency,day_of_week,day_of_month,month_of_year,
                    run_time,output_format,delivery_mode,recipients,status,next_run_at,created_by,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id
                """, Long.class, userId, name, source.savedReport(), source.frequency(), source.dayOfWeek(), source.dayOfMonth(), source.monthOfYear(),
                source.time(), source.format(), source.delivery(), source.recipients(), "PAUSED", next, username,
                BusinessClock.nowUtcText(), BusinessClock.nowUtcText());
        return rowById(newId, userId);
    }

    @Transactional
    public void delete(long id) {
        int userId = CurrentUser.require().id();
        if (db.update("DELETE FROM report_schedule WHERE id=? AND user_id=?", id, userId) != 1)
            throw new IllegalArgumentException("Schedule not found");
    }

    public Result runNow(long id) {
        int userId = CurrentUser.require().id();
        ScheduleDefinition schedule = tx.execute(status -> requireOwned(id, userId));
        if (schedule == null) throw new IllegalArgumentException("Schedule not found");
        execute(schedule, "MANUAL");
        return new Result(true, "Scheduled report generated successfully");
    }

    /** Central server scheduler. Shared/company deployments therefore run only once on the server. */
    @Scheduled(initialDelayString = "${dse.report.scheduler-initial-delay-ms:30000}",
               fixedDelayString = "${dse.report.scheduler-delay-ms:60000}")
    public void runDueSchedules() {
        List<Long> due;
        try {
            due = db.query("""
                    SELECT id FROM report_schedule
                    WHERE status='ACTIVE' AND CAST(next_run_at AS timestamptz) <= CURRENT_TIMESTAMP
                    ORDER BY CAST(next_run_at AS timestamptz),id LIMIT 25
                    """, (r,i) -> r.getLong(1));
        } catch (Exception unavailable) {
            return; // startup/migration can still be in progress
        }
        for (Long id : due) {
            try {
                ScheduleDefinition claimed = tx.execute(status -> claimDue(id));
                if (claimed != null) execute(claimed, "SCHEDULED");
            } catch (Exception failure) {
                System.err.println("Scheduled report " + id + " failed: " + root(failure));
            }
        }
    }

    private ScheduleDefinition claimDue(long id) {
        ScheduleDefinition schedule = loadById(id, null);
        if (schedule == null || !"ACTIVE".equals(schedule.status())) return null;
        Instant due = parseInstant(schedule.nextRunRaw());
        if (due == null || due.isAfter(BusinessClock.nowUtc())) return null;
        String next = instantText(nextRun(schedule.validated(), ZonedDateTime.now(BusinessClock.zone()).plusSeconds(1)));
        int changed = db.update("""
                UPDATE report_schedule SET next_run_at=?,updated_at=?
                WHERE id=? AND status='ACTIVE' AND next_run_at=?
                """, next, BusinessClock.nowUtcText(), id, schedule.nextRunRaw());
        return changed == 1 ? schedule : null;
    }

    private void execute(ScheduleDefinition schedule, String triggeredBy) {
        long runId = beginRun(schedule.id(), triggeredBy);
        Path outputRoot = null;
        boolean deleteAfter = false;
        try {
            SavedConfig saved = loadSavedConfig(schedule.userId(), schedule.savedReport());
            if (saved == null || saved.request() == null) throw new IllegalStateException("Saved Report '" + schedule.savedReport() + "' no longer exists or is invalid");
            DateRange range = resolveDateRange(saved.datePreset(), saved.request());
            ReportRequest prepared = withRange(saved.request(), range.from(), range.to(), 0, 250);
            String username = username(schedule.userId());
            ReportResult result = runAll(prepared, username);
            Set<String> visible = saved.request().visibleColumns() == null ? Set.of() : new LinkedHashSet<>(saved.request().visibleColumns());

            String stamp = ZonedDateTime.now(BusinessClock.zone()).format(FILE_STAMP);
            String base = safeFile(schedule.name()) + "-" + safeFile(result.title()) + "-" + stamp;
            boolean archive = schedule.delivery().contains("ARCHIVE");
            if (archive) {
                if (workspace == null) throw new IllegalStateException("Server workspace is not configured for Scheduled Report archive delivery");
                LocalDate today = LocalDate.now(BusinessClock.zone());
                outputRoot = workspace.resolve("Reports").resolve("Scheduled")
                        .resolve(financialYear(today))
                        .resolve(String.format(Locale.ROOT, "%02d", today.getMonthValue()))
                        .resolve(safeFile(schedule.name()));
                Files.createDirectories(outputRoot);
            } else {
                outputRoot = Files.createTempDirectory("dse-scheduled-report-");
                deleteAfter = true;
            }

            List<Path> files = new ArrayList<>();
            switch (schedule.format()) {
                case "PDF" -> { Path p = outputRoot.resolve(base + ".pdf"); exporter.pdf(p, result, visible); files.add(p); }
                case "XLSX" -> { Path p = outputRoot.resolve(base + ".xlsx"); exporter.excel(p, result, visible); files.add(p); }
                case "CSV" -> { Path p = outputRoot.resolve(base + ".csv"); exporter.csv(p, result, visible); files.add(p); }
                case "PDF_XLSX" -> {
                    Path pdf = outputRoot.resolve(base + ".pdf"); Path xlsx = outputRoot.resolve(base + ".xlsx");
                    exporter.pdf(pdf, result, visible); exporter.excel(xlsx, result, visible); files.add(pdf); files.add(xlsx);
                }
                default -> throw new IllegalStateException("Unsupported scheduled output format: " + schedule.format());
            }

            if (schedule.delivery().contains("EMAIL")) {
                List<String> recipients = recipients(schedule.recipients());
                if (recipients.isEmpty()) throw new IllegalStateException("Scheduled Report email recipient is missing");
                List<SmtpMailService.Attachment> attachments = new ArrayList<>();
                for (Path file : files) attachments.add(new SmtpMailService.Attachment(file.getFileName().toString(), contentType(file), Files.readAllBytes(file)));
                String subject = "DSE ERP Scheduled Report - " + result.title();
                String body = "Scheduled Report: " + schedule.name() + "\nReport: " + result.title()
                        + "\nPeriod: " + result.periodFrom() + " to " + result.periodTo()
                        + "\nGenerated: " + result.generatedAt() + "\nRows: " + result.totalRows();
                for (String recipient : recipients) mail.sendBusiness(recipient, subject, body, attachments);
            }

            String artifacts = String.join("; ", files.stream().map(Path::toString).toList());
            finishRun(schedule.id(), runId, true, result.title(), result.totalRows(), artifacts, "");
        } catch (Exception failure) {
            finishRun(schedule.id(), runId, false, "", 0, "", root(failure));
            throw failure instanceof RuntimeException r ? r : new IllegalStateException(failure);
        } finally {
            if (deleteAfter && outputRoot != null) deleteTreeQuietly(outputRoot);
        }
    }

    private ReportResult runAll(ReportRequest request, String username) {
        ReportResult first = reporting.run(request, username);
        if (first.totalPages() <= 1) return first;
        List<ReportRow> rows = new ArrayList<>(first.rows());
        for (int page = 1; page < first.totalPages(); page++) {
            ReportResult next = reporting.run(withRange(request, LocalDate.parse(first.periodFrom()), LocalDate.parse(first.periodTo()), page, 250), username);
            rows.addAll(next.rows());
        }
        return new ReportResult(first.reportId(), first.title(), first.description(), first.periodFrom(), first.periodTo(),
                first.metrics(), first.columns(), rows, first.totalRows(), 0, rows.size(), 1,
                first.groupByOptions(), first.appliedFilters(), first.totals(), first.generatedAt(), first.generatedBy());
    }

    private long beginRun(long scheduleId, String triggeredBy) {
        return tx.execute(status -> db.queryForObject("""
                INSERT INTO report_schedule_run(schedule_id,started_at,status,triggered_by)
                VALUES(?,?,'RUNNING',?) RETURNING id
                """, Long.class, scheduleId, BusinessClock.nowUtcText(), triggeredBy));
    }

    private void finishRun(long scheduleId, long runId, boolean success, String reportTitle, long rows, String artifacts, String error) {
        tx.executeWithoutResult(status -> {
            String now = BusinessClock.nowUtcText(); String state = success ? "SUCCESS" : "FAILED";
            db.update("""
                    UPDATE report_schedule_run SET finished_at=?,status=?,report_title=?,output_format=(SELECT output_format FROM report_schedule WHERE id=?),
                        delivery_mode=(SELECT delivery_mode FROM report_schedule WHERE id=?),row_count=?,artifacts=?,error_message=? WHERE id=?
                    """, now, state, reportTitle, scheduleId, scheduleId, rows, artifacts, trim(error, 2000), runId);
            db.update("UPDATE report_schedule SET last_run_at=?,last_status=?,last_error=?,updated_at=? WHERE id=?",
                    now, state, trim(error, 2000), now, scheduleId);
        });
    }

    private List<ScheduleRow> rows(int userId) {
        return db.query("""
                SELECT id,user_id,schedule_name,saved_report_name,frequency,day_of_week,day_of_month,month_of_year,run_time,
                       output_format,delivery_mode,recipients,status,next_run_at,COALESCE(last_run_at,''),COALESCE(last_status,''),COALESCE(last_error,'')
                FROM report_schedule WHERE user_id=? ORDER BY CASE WHEN status='ACTIVE' THEN 0 ELSE 1 END,CAST(next_run_at AS timestamptz),schedule_name
                """, (r,i) -> toRow(new ScheduleDefinition(r.getLong(1), r.getInt(2), r.getString(3), r.getString(4), r.getString(5),
                nullableInt(r.getObject(6)), nullableInt(r.getObject(7)), nullableInt(r.getObject(8)), r.getString(9), r.getString(10),
                r.getString(11), safe(r.getString(12)), r.getString(13), r.getString(14), r.getString(15), r.getString(16), r.getString(17))), userId);
    }

    private ScheduleSummary summary(int userId) {
        Long active = db.queryForObject("SELECT COUNT(*) FROM report_schedule WHERE user_id=? AND status='ACTIVE'", Long.class, userId);
        List<String[]> next = db.query("""
                SELECT schedule_name,next_run_at FROM report_schedule WHERE user_id=? AND status='ACTIVE'
                ORDER BY CAST(next_run_at AS timestamptz),id LIMIT 1
                """, (r,i) -> new String[]{r.getString(1), r.getString(2)}, userId);
        LocalDate first = BusinessClock.today().withDayOfMonth(1); LocalDate nextMonth = first.plusMonths(1);
        Long month = db.queryForObject("""
                SELECT COUNT(*) FROM report_schedule_run r JOIN report_schedule s ON s.id=r.schedule_id
                WHERE s.user_id=? AND r.status='SUCCESS' AND CAST(r.started_at AS timestamptz)>=? AND CAST(r.started_at AS timestamptz)<?
                """, Long.class, userId, first.atStartOfDay(BusinessClock.zone()).toInstant(), nextMonth.atStartOfDay(BusinessClock.zone()).toInstant());
        Instant since = BusinessClock.nowUtc().minus(Duration.ofDays(30));
        Long failures = db.queryForObject("""
                SELECT COUNT(*) FROM report_schedule_run r JOIN report_schedule s ON s.id=r.schedule_id
                WHERE s.user_id=? AND r.status='FAILED' AND CAST(r.started_at AS timestamptz)>=?
                """, Long.class, userId, since);
        return new ScheduleSummary(active == null ? 0 : active, next.isEmpty() ? "" : displayInstant(next.getFirst()[1]),
                next.isEmpty() ? "" : next.getFirst()[0], month == null ? 0 : month, failures == null ? 0 : failures);
    }

    private ScheduleRow rowById(long id, int userId) { return toRow(requireOwned(id, userId)); }

    private ScheduleRow toRow(ScheduleDefinition d) {
        SavedConfig saved = loadSavedConfig(d.userId(), d.savedReport());
        String title = saved == null || saved.request() == null ? "Saved Report unavailable" : reportTitle(saved.request().reportId());
        String preset = saved == null ? "" : safe(saved.datePreset());
        return new ScheduleRow(d.id(), d.name(), d.savedReport(), title, preset, d.frequency(), d.dayOfWeek(), d.dayOfMonth(), d.monthOfYear(),
                d.time(), displayFormat(d.format()), displayDelivery(d.delivery()), d.recipients(), displayInstant(d.nextRunRaw()),
                displayInstant(d.lastRunRaw()), d.status(), d.lastStatus(), d.lastError());
    }

    private ScheduleDefinition requireOwned(long id, int userId) {
        ScheduleDefinition schedule = loadById(id, userId);
        if (schedule == null) throw new IllegalArgumentException("Schedule not found");
        return schedule;
    }

    private ScheduleDefinition loadById(long id, Integer userId) {
        String sql = """
                SELECT id,user_id,schedule_name,saved_report_name,frequency,day_of_week,day_of_month,month_of_year,run_time,
                       output_format,delivery_mode,recipients,status,next_run_at,COALESCE(last_run_at,''),COALESCE(last_status,''),COALESCE(last_error,'')
                FROM report_schedule WHERE id=?
                """ + (userId == null ? "" : " AND user_id=?");
        Object[] args = userId == null ? new Object[]{id} : new Object[]{id, userId};
        List<ScheduleDefinition> found = db.query(sql, (r,i) -> new ScheduleDefinition(r.getLong(1), r.getInt(2), r.getString(3), r.getString(4), r.getString(5),
                nullableInt(r.getObject(6)), nullableInt(r.getObject(7)), nullableInt(r.getObject(8)), r.getString(9), r.getString(10),
                r.getString(11), safe(r.getString(12)), r.getString(13), r.getString(14), r.getString(15), r.getString(16), r.getString(17)), args);
        return found.isEmpty() ? null : found.getFirst();
    }

    private List<SavedReportOption> savedReportOptions(int userId) {
        List<ReportDefinition> definitions = reporting.definitions();
        Map<String,String> titles = new HashMap<>(); for (ReportDefinition d : definitions) titles.put(d.id(), d.title());
        return db.query("SELECT view_name,filter_json FROM saved_filter WHERE user_id=? AND screen_key='REPORT_CENTER' ORDER BY view_name", (r,i) -> {
            SavedConfig saved = decodeSaved(r.getString(2)); String reportId = saved == null || saved.request() == null ? "" : safe(saved.request().reportId());
            return new SavedReportOption(r.getString(1), reportId, titles.getOrDefault(reportId, titleCase(reportId)), saved == null ? "Custom" : safe(saved.datePreset()));
        }, userId).stream().filter(x -> !x.reportId().isBlank()).toList();
    }

    private Validated validate(ScheduleRequest request, int userId, Long editingId) {
        if (request == null) throw new IllegalArgumentException("Schedule details are required");
        String name = safe(request.name()).trim(); if (name.isBlank()) throw new IllegalArgumentException("Schedule name is required");
        if (name.length() > 120) throw new IllegalArgumentException("Schedule name is too long");
        Long duplicate = editingId == null
                ? db.queryForObject("SELECT COUNT(*) FROM report_schedule WHERE user_id=? AND LOWER(schedule_name)=LOWER(?)", Long.class, userId, name)
                : db.queryForObject("SELECT COUNT(*) FROM report_schedule WHERE user_id=? AND LOWER(schedule_name)=LOWER(?) AND id<>?", Long.class, userId, name, editingId);
        if (duplicate != null && duplicate > 0) throw new IllegalArgumentException("A schedule with this name already exists");
        String savedReport = safe(request.savedReport()).trim(); if (savedReport.isBlank()) throw new IllegalArgumentException("Select a Saved Report");
        if (loadSavedConfig(userId, savedReport) == null) throw new IllegalArgumentException("The selected Saved Report could not be found. Save the report again and retry.");
        String frequency = normalize(request.frequency()); if (!FREQUENCIES.contains(frequency)) throw new IllegalArgumentException("Unsupported schedule frequency");
        LocalTime time; try { time = LocalTime.parse(safe(request.time()).trim().isBlank() ? "08:00" : safe(request.time()).trim()); }
        catch (Exception e) { throw new IllegalArgumentException("Schedule time must use HH:mm format"); }
        Integer dow = request.dayOfWeek(), dom = request.dayOfMonth(), moy = request.monthOfYear();
        if ("WEEKLY".equals(frequency)) { if (dow == null) dow = 1; if (dow < 1 || dow > 7) throw new IllegalArgumentException("Select a valid weekday"); }
        else dow = null;
        if (Set.of("MONTHLY","QUARTERLY","YEARLY").contains(frequency)) { if (dom == null) dom = 1; if (dom < 1 || dom > 31) throw new IllegalArgumentException("Day of month must be between 1 and 31"); }
        else dom = null;
        if ("YEARLY".equals(frequency)) { if (moy == null) moy = 1; if (moy < 1 || moy > 12) throw new IllegalArgumentException("Select a valid month"); }
        else moy = null;
        String format = canonicalToken(request.format());
        if (!FORMATS.contains(format)) throw new IllegalArgumentException("Unsupported output format");
        String delivery = canonicalToken(request.delivery());
        if (!DELIVERIES.contains(delivery)) throw new IllegalArgumentException("Unsupported delivery mode");
        String recipients = safe(request.recipients()).trim();
        if (delivery.contains("EMAIL")) {
            List<String> addresses = recipients(recipients); if (addresses.isEmpty()) throw new IllegalArgumentException("Enter at least one email recipient");
            for (String address : addresses) try { new InternetAddress(address, true); } catch (Exception e) { throw new IllegalArgumentException("Invalid email recipient: " + address); }
            mail.requireConfigured();
        }
        if (delivery.contains("ARCHIVE") && workspace == null) throw new IllegalStateException("Server workspace is not configured for archive delivery");
        return new Validated(name, savedReport, frequency, dow, dom, moy, time, format, delivery, recipients);
    }

    private SavedConfig loadSavedConfig(int userId, String savedReportName) {
        List<String> values = db.query("SELECT filter_json FROM saved_filter WHERE user_id=? AND screen_key='REPORT_CENTER' AND view_name=?", (r,i) -> r.getString(1), userId, savedReportName);
        return values.isEmpty() ? null : decodeSaved(values.getFirst());
    }

    private SavedConfig decodeSaved(String payload) {
        if (payload == null || payload.isBlank()) return null;
        if (payload.startsWith("REPORT_V2:")) {
            try { return json.readValue(payload.substring("REPORT_V2:".length()), SavedConfig.class); }
            catch (Exception e) { return null; }
        }
        String[] x = payload.split("\\|", -1);
        if (x.length < 6) return null;
        String reportId = switch (safe(x[0]).trim().toUpperCase(Locale.ROOT)) {
            case "PURCHASE" -> "PURCHASE_REGISTER"; case "INVENTORY" -> "STOCK_SUMMARY"; case "PAYMENTS" -> "RECEIVABLE_AGEING"; default -> "SALES_REGISTER";
        };
        ReportRequest request = new ReportRequest(reportId, safe(x[4]), safe(x[5]), normalizeAll(x[1]), normalizeAll(x[2]), normalizeAll(x[3]),
                "", "", "", "", "", "", "", "None", "date", "DESC", null, null, 0, 25, List.of());
        return new SavedConfig(1, "", "Custom", request);
    }

    private DateRange resolveDateRange(String preset, ReportRequest request) {
        String p = normalize(preset); LocalDate today = BusinessClock.today(), from = today, to = today;
        switch (p) {
            case "TODAY" -> { }
            case "YESTERDAY", "PREVIOUS_DAY" -> { from = today.minusDays(1); to = from; }
            case "THIS_WEEK" -> from = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "LAST_WEEK", "PREVIOUS_WEEK" -> { to = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusDays(1); from = to.minusDays(6); }
            case "THIS_MONTH" -> from = today.withDayOfMonth(1);
            case "LAST_MONTH", "PREVIOUS_MONTH" -> { LocalDate m = today.minusMonths(1); from = m.withDayOfMonth(1); to = m.withDayOfMonth(m.lengthOfMonth()); }
            case "THIS_QUARTER" -> { int m = ((today.getMonthValue()-1)/3)*3+1; from = LocalDate.of(today.getYear(),m,1); }
            case "LAST_QUARTER", "PREVIOUS_QUARTER" -> { LocalDate q=today.minusMonths(3); int m=((q.getMonthValue()-1)/3)*3+1; from=LocalDate.of(q.getYear(),m,1); to=from.plusMonths(3).minusDays(1); }
            case "THIS_FINANCIAL_YEAR", "CURRENT_FINANCIAL_YEAR" -> { int y=today.getMonthValue()>=4?today.getYear():today.getYear()-1; from=LocalDate.of(y,4,1); to=LocalDate.of(y+1,3,31); }
            case "LAST_FINANCIAL_YEAR", "PREVIOUS_FINANCIAL_YEAR" -> { int y=today.getMonthValue()>=4?today.getYear()-1:today.getYear()-2; from=LocalDate.of(y,4,1); to=LocalDate.of(y+1,3,31); }
            case "LAST_7_DAYS" -> from = today.minusDays(6);
            case "LAST_30_DAYS" -> from = today.minusDays(29);
            default -> {
                try { from = request.from() == null || request.from().isBlank() ? today.withDayOfMonth(1) : LocalDate.parse(request.from()); }
                catch (Exception ignored) { from = today.withDayOfMonth(1); }
                try { to = request.to() == null || request.to().isBlank() ? today : LocalDate.parse(request.to()); }
                catch (Exception ignored) { to = today; }
            }
        }
        return new DateRange(from, to);
    }

    private ReportRequest withRange(ReportRequest q, LocalDate from, LocalDate to, int page, int size) {
        return new ReportRequest(q.reportId(), from.toString(), to.toString(), q.party(), q.item(), q.salesperson(), q.documentStatus(), q.paymentStatus(),
                q.returnStatus(), q.gstRate(), q.warehouse(), q.bankStatus(), q.search(), q.groupBy(), q.sortKey(), q.sortDirection(),
                q.minAmount(), q.maxAmount(), page, size, q.visibleColumns());
    }

    private ZonedDateTime nextRun(Validated v, ZonedDateTime now) {
        LocalTime time = v.time; ZoneId zone = BusinessClock.zone(); ZonedDateTime candidate;
        switch (v.frequency) {
            case "DAILY" -> {
                candidate = now.toLocalDate().atTime(time).atZone(zone); if (!candidate.isAfter(now)) candidate = candidate.plusDays(1);
            }
            case "WEEKLY" -> {
                DayOfWeek day = DayOfWeek.of(v.dayOfWeek == null ? 1 : v.dayOfWeek);
                LocalDate date = now.toLocalDate().with(TemporalAdjusters.nextOrSame(day)); candidate = date.atTime(time).atZone(zone); if (!candidate.isAfter(now)) candidate = candidate.plusWeeks(1);
            }
            case "MONTHLY" -> {
                YearMonth month = YearMonth.from(now); candidate = dateInMonth(month, v.dayOfMonth).atTime(time).atZone(zone);
                if (!candidate.isAfter(now)) candidate = dateInMonth(month.plusMonths(1), v.dayOfMonth).atTime(time).atZone(zone);
            }
            case "QUARTERLY" -> {
                int firstMonth = ((now.getMonthValue()-1)/3)*3+1; YearMonth quarter = YearMonth.of(now.getYear(), firstMonth);
                candidate = dateInMonth(quarter, v.dayOfMonth).atTime(time).atZone(zone);
                if (!candidate.isAfter(now)) candidate = dateInMonth(quarter.plusMonths(3), v.dayOfMonth).atTime(time).atZone(zone);
            }
            case "YEARLY" -> {
                int month = v.monthOfYear == null ? 1 : v.monthOfYear; YearMonth ym = YearMonth.of(now.getYear(), month);
                candidate = dateInMonth(ym, v.dayOfMonth).atTime(time).atZone(zone);
                if (!candidate.isAfter(now)) candidate = dateInMonth(YearMonth.of(now.getYear()+1, month), v.dayOfMonth).atTime(time).atZone(zone);
            }
            default -> throw new IllegalArgumentException("Unsupported schedule frequency: " + v.frequency);
        }
        return candidate;
    }

    private static LocalDate dateInMonth(YearMonth month, Integer requestedDay) {
        int day = requestedDay == null ? 1 : Math.max(1, Math.min(requestedDay, month.lengthOfMonth())); return month.atDay(day);
    }

    private String reportTitle(String reportId) {
        return reporting.definitions().stream().filter(d -> d.id().equalsIgnoreCase(safe(reportId))).map(ReportDefinition::title).findFirst().orElse(titleCase(reportId));
    }

    private String username(int userId) {
        try { return db.queryForObject("SELECT username FROM users WHERE id=? AND active=1", String.class, userId); }
        catch (Exception e) { throw new IllegalStateException("Schedule owner is no longer an active user"); }
    }

    private String uniqueCopyName(int userId, String source) {
        String base = "Copy of " + source; String candidate = base; int n = 2;
        while (countByName(userId, candidate) > 0) candidate = base + " (" + n++ + ")";
        return candidate;
    }

    private long countByName(int userId, String name) {
        Long count = db.queryForObject("SELECT COUNT(*) FROM report_schedule WHERE user_id=? AND LOWER(schedule_name)=LOWER(?)", Long.class, userId, name);
        return count == null ? 0 : count;
    }

    private static List<String> recipients(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split("[;,]")) .map(String::trim).filter(x -> !x.isBlank()).distinct().toList();
    }

    private static String contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (name.endsWith(".csv")) return "text/csv; charset=UTF-8";
        return "application/octet-stream";
    }

    private static String financialYear(LocalDate date) {
        LocalDate effective = date == null ? LocalDate.now(BusinessClock.zone()) : date;
        int start = effective.getMonthValue() >= 4 ? effective.getYear() : effective.getYear() - 1;
        return start + "-" + String.format(Locale.ROOT, "%02d", (start + 1) % 100);
    }

    private static String safeFile(String value) {
        String s = safe(value).replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("-+", "-").replaceAll("^-|-$", "");
        return s.isBlank() ? "report" : s;
    }

    private static void deleteTreeQuietly(Path root) {
        try { if (root == null || !Files.exists(root)) return; try (var walk = Files.walk(root)) { walk.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) { } }); } }
        catch (Exception ignored) { }
    }

    private static String displayInstant(String value) {
        Instant instant = parseInstant(value); return instant == null ? "" : RUN_LABEL.format(instant.atZone(BusinessClock.zone()));
    }
    private static Instant parseInstant(String value) { try { return value == null || value.isBlank() ? null : BusinessClock.parseTimestamp(value); } catch (Exception e) { return null; } }
    private static String instantText(ZonedDateTime value) { return DateTimeFormatter.ISO_INSTANT.format(value.toInstant()); }
    private static String normalize(String value) { return safe(value).trim().toUpperCase(Locale.ROOT).replace(' ', '_'); }
    private static String canonicalToken(String value) { return normalize(value).replaceAll("[^A-Z0-9]+", "_").replaceAll("_+", "_").replaceAll("^_|_$", ""); }
    private static String normalizeAll(String value) { String v = safe(value).trim(); return v.toUpperCase(Locale.ROOT).startsWith("ALL ") || "ALL".equalsIgnoreCase(v) ? "" : v; }
    private static String displayFormat(String value) { return "PDF_XLSX".equals(value) ? "PDF + XLSX" : value; }
    private static String displayDelivery(String value) { return "EMAIL_ARCHIVE".equals(value) ? "Email + Archive" : titleCase(value); }
    private static String safe(String value) { return value == null ? "" : value; }
    private static Integer nullableInt(Object value) { return value instanceof Number n ? n.intValue() : null; }
    private static String trim(String value, int max) { String v = safe(value); return v.length() <= max ? v : v.substring(0, max); }
    private static String root(Throwable failure) { Throwable x = failure; while (x != null && x.getCause() != null && x.getCause() != x) x = x.getCause(); return x == null ? "Unknown error" : (x.getMessage() == null || x.getMessage().isBlank() ? x.getClass().getSimpleName() : x.getMessage()); }
    private static String titleCase(String value) { StringBuilder b = new StringBuilder(); for (String p : safe(value).toLowerCase(Locale.ROOT).split("[ _]+")) { if (p.isBlank()) continue; if (!b.isEmpty()) b.append(' '); b.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)); } return b.toString(); }

    private record SavedConfig(int version, String name, String datePreset, ReportRequest request) { }
    private record DateRange(LocalDate from, LocalDate to) { }
    private record Validated(String name,String savedReport,String frequency,Integer dayOfWeek,Integer dayOfMonth,Integer monthOfYear,LocalTime time,String format,String delivery,String recipients) {
        Validated withName(String name) { return new Validated(name,savedReport,frequency,dayOfWeek,dayOfMonth,monthOfYear,time,format,delivery,recipients); }
    }
    private record ScheduleDefinition(long id,int userId,String name,String savedReport,String frequency,Integer dayOfWeek,Integer dayOfMonth,Integer monthOfYear,String time,String format,String delivery,String recipients,String status,String nextRunRaw,String lastRunRaw,String lastStatus,String lastError) {
        Validated validated() { return new Validated(name,savedReport,frequency,dayOfWeek,dayOfMonth,monthOfYear,LocalTime.parse(time),format,delivery,recipients); }
    }
}
