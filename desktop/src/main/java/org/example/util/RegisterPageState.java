package org.example.util;

/**
 * Shared paging state for server-backed ERP registers.
 *
 * <p>Controllers still own the API request and business filters; this class owns
 * only the repeated page counters/navigation and display text. Keeping this
 * state in one place prevents small differences between registers from
 * reintroducing off-by-one page bugs after server-side pagination.</p>
 */
public final class RegisterPageState {
    private int currentPage;
    private int totalPages;
    private long totalRows;
    private boolean applyingServerPage;

    public int currentPage() { return currentPage; }
    public int totalPages() { return totalPages; }
    public long totalRows() { return totalRows; }
    public boolean isApplyingServerPage() { return applyingServerPage; }

    public void reset() { currentPage = 0; }

    public void apply(int page, int pages, long rows) {
        totalPages = Math.max(0, pages);
        totalRows = Math.max(0L, rows);
        if (totalPages <= 0) currentPage = 0;
        else currentPage = Math.min(Math.max(0, page), totalPages - 1);
    }

    public void runApplying(Runnable action) {
        applyingServerPage = true;
        try { action.run(); }
        finally { applyingServerPage = false; }
    }

    public boolean first() {
        if (currentPage == 0) return false;
        currentPage = 0;
        return true;
    }

    public boolean previous() {
        if (currentPage <= 0) return false;
        currentPage--;
        return true;
    }

    public boolean next() {
        if (currentPage + 1 >= totalPages) return false;
        currentPage++;
        return true;
    }

    public boolean last() {
        if (totalPages <= 0 || currentPage == totalPages - 1) return false;
        currentPage = totalPages - 1;
        return true;
    }

    public long firstRow(int pageSize) {
        return totalRows == 0 ? 0 : (long) currentPage * Math.max(1, pageSize) + 1;
    }

    public long lastRow(int pageSize, int currentRows) {
        if (totalRows == 0) return 0;
        return Math.min(totalRows, firstRow(pageSize) + Math.max(0, currentRows) - 1);
    }

    public String pageNumberText() {
        return totalPages <= 0 ? "0 / 0" : (currentPage + 1) + " / " + totalPages;
    }

    public String rangeText(int pageSize, int currentRows, String noun) {
        String normalizedNoun = noun == null || noun.isBlank() ? "entries" : noun.trim();
        if (totalRows == 0) return "Showing 0 of 0 " + normalizedNoun;
        return "Showing " + firstRow(pageSize) + " to " + lastRow(pageSize, currentRows)
                + " of " + totalRows + " " + normalizedNoun;
    }

    public String rangeWithPageText(int pageSize, int currentRows, String noun) {
        String base = rangeText(pageSize, currentRows, noun);
        if (totalRows == 0) return base;
        return base + " • Page " + (currentPage + 1) + " / " + Math.max(1, totalPages);
    }


}
