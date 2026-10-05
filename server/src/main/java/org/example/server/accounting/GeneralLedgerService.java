package org.example.server.accounting;

import org.example.server.persistence.entity.ChartOfAccountsEntity;
import org.example.server.persistence.entity.JournalEntryEntity;
import org.example.server.persistence.entity.JournalLineEntity;
import org.example.server.persistence.repository.ChartOfAccountsRepository;
import org.example.server.persistence.repository.JournalEntryRepository;
import org.example.server.persistence.repository.JournalLineRepository;
import org.example.server.persistence.JpaNativeRepository;
import org.example.shared.ReferenceFormatRules;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Transactional
public class GeneralLedgerService {

    private final ChartOfAccountsRepository coaRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final JournalLineRepository journalLineRepository;
    private final JpaNativeRepository jdbc;
    private final AtomicLong sequenceGen = new AtomicLong(System.currentTimeMillis() % 100000);

    public GeneralLedgerService(ChartOfAccountsRepository coaRepository,
                                JournalEntryRepository journalEntryRepository,
                                JournalLineRepository journalLineRepository,
                                JpaNativeRepository jdbc) {
        this.coaRepository = coaRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.journalLineRepository = journalLineRepository;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<GeneralLedgerDtos.AccountDto> listAccounts() {
        return coaRepository.findAllByIsActiveTrueOrderByAccountCodeAsc().stream()
                .map(this::toAccountDto)
                .toList();
    }

    public GeneralLedgerDtos.AccountDto createAccount(GeneralLedgerDtos.AccountDto dto) {
        ChartOfAccountsEntity entity = new ChartOfAccountsEntity();
        entity.setAccountCode(dto.accountCode());
        entity.setAccountName(dto.accountName());
        entity.setAccountType(dto.accountType());
        entity.setAccountSubtype(dto.accountSubtype());
        entity.setParentId(dto.parentId());
        entity.setCurrency(dto.currency() != null ? dto.currency() : "INR");
        entity.setOpeningBalance(dto.openingBalance() != null ? dto.openingBalance() : BigDecimal.ZERO);
        entity.setCurrentBalance(entity.getOpeningBalance());
        entity.setIsActive(dto.isActive() != null ? dto.isActive() : true);
        entity.setIsSystem(false);
        ChartOfAccountsEntity saved = coaRepository.save(entity);
        return toAccountDto(saved);
    }

    public GeneralLedgerDtos.JournalEntryDto postJournalEntry(GeneralLedgerDtos.CreateJournalRequest req) {
        if (req.lines() == null || req.lines().isEmpty()) {
            throw new IllegalArgumentException("Journal entry must contain at least two lines");
        }

        BigDecimal sumDebit = BigDecimal.ZERO;
        BigDecimal sumCredit = BigDecimal.ZERO;

        for (GeneralLedgerDtos.CreateLineRequest line : req.lines()) {
            BigDecimal d = line.debitAmount() != null ? line.debitAmount() : BigDecimal.ZERO;
            BigDecimal c = line.creditAmount() != null ? line.creditAmount() : BigDecimal.ZERO;
            sumDebit = sumDebit.add(d);
            sumCredit = sumCredit.add(c);
        }

        if (sumDebit.setScale(2, RoundingMode.HALF_UP).compareTo(sumCredit.setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw new IllegalArgumentException("Unbalanced Journal Entry: Debits (" + sumDebit +
                    ") must equal Credits (" + sumCredit + ")");
        }

        JournalEntryEntity entry = new JournalEntryEntity();
        entry.setEntryNumber(nextJournalVoucherNumber(req.entryDate()));
        entry.setEntryDate(req.entryDate() != null ? req.entryDate() : LocalDate.now());
        entry.setEntryType(req.entryType() != null ? req.entryType() : "GENERAL");
        entry.setReferenceType(req.referenceType());
        entry.setReferenceId(req.referenceId());
        entry.setReferenceNo(req.referenceNo());
        entry.setNarration(req.narration());
        entry.setTotalDebit(sumDebit);
        entry.setTotalCredit(sumCredit);
        entry.setStatus("POSTED");
        entry.setPostedBy(req.postedBy() != null ? req.postedBy() : "SYSTEM");
        entry.setPostedAt(LocalDateTime.now());

        JournalEntryEntity savedEntry = journalEntryRepository.save(entry);

        int lineNum = 1;
        for (GeneralLedgerDtos.CreateLineRequest lineReq : req.lines()) {
            ChartOfAccountsEntity account;
            if (lineReq.accountId() != null) {
                account = coaRepository.findById(lineReq.accountId())
                        .orElseThrow(() -> new IllegalArgumentException("Account not found: " + lineReq.accountId()));
            } else if (lineReq.accountCode() != null) {
                account = coaRepository.findByAccountCode(lineReq.accountCode())
                        .orElseThrow(() -> new IllegalArgumentException("Account not found for code: " + lineReq.accountCode()));
            } else {
                throw new IllegalArgumentException("Line " + lineNum + " missing account specification");
            }

            BigDecimal debit = lineReq.debitAmount() != null ? lineReq.debitAmount() : BigDecimal.ZERO;
            BigDecimal credit = lineReq.creditAmount() != null ? lineReq.creditAmount() : BigDecimal.ZERO;

            JournalLineEntity line = new JournalLineEntity();
            line.setJournalEntry(savedEntry);
            line.setAccountId(account.getId());
            line.setLineNumber(lineNum++);
            line.setDebitAmount(debit);
            line.setCreditAmount(credit);
            line.setPartyId(lineReq.partyId());
            line.setLineNarration(lineReq.lineNarration());
            line.setCostCenter(lineReq.costCenter());
            journalLineRepository.save(line);

            // Update Account Balance according to normal accounting nature
            BigDecimal current = account.getCurrentBalance() != null ? account.getCurrentBalance() : BigDecimal.ZERO;
            if ("ASSET".equalsIgnoreCase(account.getAccountType()) || "EXPENSE".equalsIgnoreCase(account.getAccountType())) {
                account.setCurrentBalance(current.add(debit).subtract(credit));
            } else {
                account.setCurrentBalance(current.add(credit).subtract(debit));
            }
            coaRepository.save(account);
        }

        return toJournalEntryDto(savedEntry);
    }

    @Transactional(readOnly = true)
    public List<GeneralLedgerDtos.JournalEntryDto> listRecentEntries() {
        return journalEntryRepository.findTop100ByOrderByEntryDateDescIdDesc().stream()
                .map(this::toJournalEntryDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GeneralLedgerDtos.TrialBalanceItemDto> getTrialBalance() {
        List<ChartOfAccountsEntity> accounts = coaRepository.findAllByIsActiveTrueOrderByAccountCodeAsc();
        List<GeneralLedgerDtos.TrialBalanceItemDto> tb = new ArrayList<>();

        for (ChartOfAccountsEntity acc : accounts) {
            BigDecimal current = acc.getCurrentBalance() != null ? acc.getCurrentBalance() : BigDecimal.ZERO;
            BigDecimal debit = BigDecimal.ZERO;
            BigDecimal credit = BigDecimal.ZERO;

            if ("ASSET".equalsIgnoreCase(acc.getAccountType()) || "EXPENSE".equalsIgnoreCase(acc.getAccountType())) {
                if (current.compareTo(BigDecimal.ZERO) >= 0) {
                    debit = current;
                } else {
                    credit = current.abs();
                }
            } else {
                if (current.compareTo(BigDecimal.ZERO) >= 0) {
                    credit = current;
                } else {
                    debit = current.abs();
                }
            }

            tb.add(new GeneralLedgerDtos.TrialBalanceItemDto(
                    acc.getAccountCode(),
                    acc.getAccountName(),
                    acc.getAccountType(),
                    acc.getOpeningBalance(),
                    debit,
                    credit,
                    current
            ));
        }

        return tb;
    }

    @Transactional(readOnly = true)
    public GeneralLedgerDtos.FinancialStatementDto getFinancialStatements() {
        List<ChartOfAccountsEntity> accounts = coaRepository.findAllByIsActiveTrueOrderByAccountCodeAsc();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        BigDecimal totalAssets = BigDecimal.ZERO;
        BigDecimal totalLiabilities = BigDecimal.ZERO;
        BigDecimal totalEquity = BigDecimal.ZERO;

        for (ChartOfAccountsEntity acc : accounts) {
            BigDecimal bal = acc.getCurrentBalance() != null ? acc.getCurrentBalance() : BigDecimal.ZERO;
            switch (acc.getAccountType().toUpperCase()) {
                case "REVENUE" -> totalRevenue = totalRevenue.add(bal);
                case "EXPENSE" -> totalExpense = totalExpense.add(bal);
                case "ASSET" -> totalAssets = totalAssets.add(bal);
                case "LIABILITY" -> totalLiabilities = totalLiabilities.add(bal);
                case "EQUITY" -> totalEquity = totalEquity.add(bal);
            }
        }

        BigDecimal netProfit = totalRevenue.subtract(totalExpense);
        return new GeneralLedgerDtos.FinancialStatementDto(
                totalRevenue, totalExpense, netProfit, totalAssets, totalLiabilities, totalEquity
        );
    }

    private GeneralLedgerDtos.AccountDto toAccountDto(ChartOfAccountsEntity e) {
        return new GeneralLedgerDtos.AccountDto(
                e.getId(), e.getAccountCode(), e.getAccountName(), e.getAccountType(),
                e.getAccountSubtype(), e.getParentId(), e.getCurrency(), e.getOpeningBalance(),
                e.getCurrentBalance(), e.getIsActive(), e.getIsSystem(), e.getRowVersion()
        );
    }

    private GeneralLedgerDtos.JournalEntryDto toJournalEntryDto(JournalEntryEntity e) {
        List<GeneralLedgerDtos.JournalLineDto> lines = e.getLines().stream().map(l -> {
            Optional<ChartOfAccountsEntity> acc = coaRepository.findById(l.getAccountId());
            String code = acc.map(ChartOfAccountsEntity::getAccountCode).orElse("");
            String name = acc.map(ChartOfAccountsEntity::getAccountName).orElse("");
            return new GeneralLedgerDtos.JournalLineDto(
                    l.getId(), l.getAccountId(), code, name, l.getLineNumber(),
                    l.getDebitAmount(), l.getCreditAmount(), l.getPartyId(),
                    l.getLineNarration(), l.getCostCenter()
            );
        }).toList();

        return new GeneralLedgerDtos.JournalEntryDto(
                e.getId(), e.getEntryNumber(), e.getEntryDate(), e.getEntryType(),
                e.getReferenceType(), e.getReferenceId(), e.getReferenceNo(),
                e.getNarration(), e.getTotalDebit(), e.getTotalCredit(),
                e.getStatus(), e.getPostedBy(), e.getPostedAt(), e.getRowVersion(),
                lines
        );
    }

    private String nextJournalVoucherNumber(LocalDate date) {
        LocalDate d = date != null ? date : LocalDate.now();
        String format = "JV-YYYYMMDD-XXXX";
        try {
            String val = jdbc.queryForObject(
                    "SELECT lookup_value FROM lookup_master WHERE UPPER(TRIM(REPLACE(lookup_type, ' ', '_')))='REFERENCE_FORMAT' AND UPPER(lookup_code) IN ('REF_JOURNAL','JOURNAL_VOUCHER','JOURNAL') LIMIT 1",
                    String.class
            );
            if (val != null && !val.isBlank()) format = val.trim();
        } catch (Exception ignored) {}

        try {
            String dated = format
                    .replace("YYYY", String.format(Locale.ROOT, "%04d", d.getYear()))
                    .replace("YY", String.format(Locale.ROOT, "%02d", d.getYear() % 100))
                    .replace("MM", String.format(Locale.ROOT, "%02d", d.getMonthValue()))
                    .replace("DD", String.format(Locale.ROOT, "%02d", d.getDayOfMonth()));
            java.util.regex.Matcher seqMatcher = ReferenceFormatRules.sequenceMatcher(dated);
            int width = seqMatcher.end() - seqMatcher.start();
            String prefix = dated.substring(0, seqMatcher.start());
            String suffix = dated.substring(seqMatcher.end());
            String scope = prefix + "\u0000" + suffix;
            String counterKey = "REF_JOURNAL|" + UUID.nameUUIDFromBytes(scope.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String regex = "^" + pgRegex(prefix) + "([0-9]+)" + pgRegex(suffix) + "$";

            Long observedValue = jdbc.queryForObject(
                    "SELECT COALESCE(MAX(((regexp_match(entry_number, ?))[1])::bigint),0)+1 FROM journal_entry WHERE entry_number ~ ?",
                    Long.class, regex, regex
            );
            long observed = observedValue == null ? 1L : Math.max(1L, observedValue);

            Long allocatedValue = jdbc.queryForObject(
                    "INSERT INTO reference_counter(counter_key,next_value,updated_at) VALUES(?,?,NOW()::text) ON CONFLICT(counter_key) DO UPDATE SET next_value=GREATEST(reference_counter.next_value+1,EXCLUDED.next_value),updated_at=EXCLUDED.updated_at RETURNING next_value",
                    Long.class, counterKey, observed
            );
            long allocated = allocatedValue == null ? observed : allocatedValue;
            return prefix + String.format(Locale.ROOT, "%0" + width + "d", allocated) + suffix;
        } catch (Exception e) {
            String datePrefix = d.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            return "JV-" + datePrefix + "-" + String.format("%04d", sequenceGen.incrementAndGet());
        }
    }

    private static String pgRegex(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length() * 2);
        String meta = "\\.^$|?*+()[]{}";
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (meta.indexOf(c) >= 0) out.append('\\');
            out.append(c);
        }
        return out.toString();
    }
}

