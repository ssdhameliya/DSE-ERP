# DSE ERP 10.0.31 Release Notes

## Overview
DSE ERP 10.0.31 introduces a comprehensive GST Compliance Suite and a complete overhaul of the Financial Statements engine.

## Key Highlights

### 1. Statutory GSTR-1 Return Generator & Government Schema Exporter
- **Table 4 (B2B Registered):** Aggregates outward supplies to registered dealers grouped by 15-character GSTIN with invoice dates, values, POS, tax rates, CGST, SGST, and IGST.
- **Table 7 (B2C Small):** Summarizes unregistered retail supplies grouped by State POS and GST tax rate with automatic intra-state and inter-state classification.
- **Table 9B (CDNR):** Registered Credit and Debit Notes with original invoice cross-references, differential taxable values, and credit/debit classifications.
- **Table 12 (HSN Summary):** Item-level HSN/SAC summary reporting with description, UQC (Unit Quantity Code), total quantity, taxable value, and integrated/central/state tax heads.
- **Table 13 (Documents Issued):** Serial number tracking for outward tax invoices, credit notes, and debit notes including starting number, ending number, total count, cancelled count, and net issued.
- **Government Offline Tool JSON Schema:** Generates official `GSTR1_v2.0` JSON structure ready for direct upload into the GST Portal / GST Offline Tool.

### 2. GSTR-2B 4-Point Auto-Reconciliation Engine & Supplier Follow-Up
- **4-Point Matching Algorithm:** Matches purchase register bills against portal GSTR-2B by Supplier GSTIN, normalized invoice number, invoice date tolerance, and tax amount within ±₹1.00.
- **Precise Status Categorization:** Classifies bills as `MATCHED`, `VALUE_DIFF`, `DATE_MISMATCH`, `MISSING_IN_BOOKS`, and `MISSING_IN_2B`.
- **Supplier Follow-up Notice Generator:** One-click WhatsApp and Email notice generator to alert suppliers regarding unfiled invoices under GST Rule 36(4), protecting company Input Tax Credit (ITC).

### 3. Statutory GSTR-3B Computation & Rule 88A Tax Set-Off
- **Table 3.1 Outward Taxable Supplies & RCM:** Captures outward tax liabilities and reverse charge obligations net of registered returns.
- **Table 4 Eligible ITC & Reversals:** Accounts for eligible input tax credits from matched 2B records and statutory reversals under Rule 42/43.
- **Section 49 / Rule 88A Set-off Engine:** Automatically applies statutory set-off hierarchy (IGST credit exhausting IGST then CGST/SGST, followed by CGST and SGST credits) to compute exact electronic cash ledger challan dues (PMT-06).

### 4. Dual-Engine Financial Statements & 3-Tier Accounting
- **Live Operational Engine (Real-Time):** Directly aggregates live sales headers, purchase headers, credit/debit notes, inventory closing stock valuation, and bank expense entries so statements match transactional registers 100%.
- **Canonical GL Engine:** Maintains double-entry general ledger mode for posted journal entries.
- **Indian 3-Tier Structure:**
  - *Trading Account:* Sales Revenue vs Cost of Goods Sold (Opening Stock + Net Purchases + Direct Costs - Closing Stock) → Gross Profit / (Loss).
  - *Profit & Loss Account:* Gross Profit + Other Income - Operating Overheads → Net Profit / (Loss).
  - *Balance Sheet:* Current & Non-Current Assets vs Liabilities & Equity with verified equilibrium.
- **Interactive Drill-Down API & Dialog:** Double-click any row to inspect underlying vouchers, invoices, or inventory items.
- **Executive Ratio Health Cards:** Displays Gross Margin %, Net Margin %, Current Ratio, Quick Ratio, Days Sales Outstanding (DSO), and Days Payables Outstanding (DPO).
