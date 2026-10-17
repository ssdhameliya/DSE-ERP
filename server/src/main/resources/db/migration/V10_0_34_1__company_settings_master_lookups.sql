-- DSE ERP 10.0.34: Company Settings & Credit/Debit Note Master Lookup Authority
-- Register master categories for Business Type, Industry, Currency, Timezone, Date & Time Formats, and Credit/Debit Note Reasons.

INSERT INTO master_category(category_code, category_name, description, display_order, is_active) VALUES
 ('BUSINESS_TYPE', 'BUSINESS TYPE', 'Business structure types for company setup', 160, 1),
 ('INDUSTRY', 'INDUSTRY', 'Industry classification sectors for company setup', 170, 1),
 ('CURRENCY', 'CURRENCY', 'Supported currencies for invoicing and accounts', 180, 1),
 ('TIME_ZONE', 'TIME ZONE', 'Supported business timezones', 190, 1),
 ('DATE_FORMAT', 'DATE FORMAT', 'Supported business date formatting patterns', 200, 1),
 ('TIME_FORMAT', 'TIME FORMAT', 'Supported business time formatting patterns', 210, 1),
 ('CREDIT_NOTE_REASON', 'CREDIT NOTE REASON', 'Standard reason codes for issuing Credit Notes', 220, 1),
 ('DEBIT_NOTE_REASON', 'DEBIT NOTE REASON', 'Standard reason codes for issuing Debit Notes', 230, 1)
ON CONFLICT (category_code) DO NOTHING;

-- Seed standard choices into lookup_master linked to the canonical category name
INSERT INTO lookup_master(lookup_type, lookup_code, lookup_value, description, display_order, is_active)
SELECT mc.category_name, v.code, v.value, v.description, v.ord, 1
FROM master_category mc
JOIN (VALUES
 -- BUSINESS_TYPE
 ('BUSINESS_TYPE', 'BTYPE001', 'Proprietorship', 'Sole proprietorship business entity', 10),
 ('BUSINESS_TYPE', 'BTYPE002', 'Partnership', 'Partnership firm', 20),
 ('BUSINESS_TYPE', 'BTYPE003', 'Private Limited Company', 'Private Limited corporate entity', 30),
 ('BUSINESS_TYPE', 'BTYPE004', 'Public Limited Company', 'Public Limited corporate entity', 40),
 ('BUSINESS_TYPE', 'BTYPE005', 'Limited Liability Partnership', 'LLP entity', 50),
 ('BUSINESS_TYPE', 'BTYPE006', 'Trust', 'Trust / Non-profit foundation', 60),
 ('BUSINESS_TYPE', 'BTYPE007', 'Society', 'Registered society', 70),
 ('BUSINESS_TYPE', 'BTYPE008', 'Other', 'Other business formation', 80),

 -- INDUSTRY
 ('INDUSTRY', 'IND001', 'Manufacturing', 'Industrial manufacturing and processing', 10),
 ('INDUSTRY', 'IND002', 'Trading', 'Commercial trading and distribution', 20),
 ('INDUSTRY', 'IND003', 'Retail', 'Retail consumer sales', 30),
 ('INDUSTRY', 'IND004', 'Wholesale', 'Wholesale B2B trading', 40),
 ('INDUSTRY', 'IND005', 'Construction', 'Construction and building materials', 50),
 ('INDUSTRY', 'IND006', 'Engineering', 'Engineering components and technical services', 60),
 ('INDUSTRY', 'IND007', 'Textile', 'Fabrics, clothing and yarn', 70),
 ('INDUSTRY', 'IND008', 'Automotive', 'Automobile components and repairs', 80),
 ('INDUSTRY', 'IND009', 'Information Technology', 'Software and IT hardware', 90),
 ('INDUSTRY', 'IND010', 'Professional Services', 'Consulting, accounting and legal', 100),
 ('INDUSTRY', 'IND011', 'Logistics', 'Transport, warehousing and freight', 110),
 ('INDUSTRY', 'IND012', 'Healthcare', 'Medical, pharmaceutical and healthcare', 120),
 ('INDUSTRY', 'IND013', 'Food & Beverage', 'Food processing and hospitality', 130),
 ('INDUSTRY', 'IND014', 'Other', 'Other industry sector', 140),

 -- CURRENCY
 ('CURRENCY', 'CURR001', 'INR - Indian Rupee', 'Indian Rupee (₹)', 10),
 ('CURRENCY', 'CURR002', 'USD - US Dollar', 'United States Dollar ($)', 20),
 ('CURRENCY', 'CURR003', 'EUR - Euro', 'European Union Euro (€)', 30),
 ('CURRENCY', 'CURR004', 'GBP - British Pound', 'British Pound (£)', 40),
 ('CURRENCY', 'CURR005', 'AED - UAE Dirham', 'United Arab Emirates Dirham (د.إ)', 50),

 -- TIME_ZONE
 ('TIME_ZONE', 'TZ001', 'Asia/Kolkata', 'India Standard Time (IST)', 10),
 ('TIME_ZONE', 'TZ002', 'Asia/Dubai', 'Gulf Standard Time (GST)', 20),
 ('TIME_ZONE', 'TZ003', 'Europe/London', 'Greenwich Mean Time / British Summer Time', 30),
 ('TIME_ZONE', 'TZ004', 'America/New_York', 'Eastern Time (ET)', 40),
 ('TIME_ZONE', 'TZ005', 'America/Los_Angeles', 'Pacific Time (PT)', 50),
 ('TIME_ZONE', 'TZ006', 'UTC', 'Coordinated Universal Time', 60),

 -- DATE_FORMAT
 ('DATE_FORMAT', 'DFMT001', 'dd/MM/yyyy', 'Standard Day/Month/Year (e.g. 17/10/2026)', 10),
 ('DATE_FORMAT', 'DFMT002', 'dd-MM-yyyy', 'Standard Day-Month-Year (e.g. 17-10-2026)', 20),
 ('DATE_FORMAT', 'DFMT003', 'yyyy-MM-dd', 'ISO Year-Month-Day (e.g. 2026-10-17)', 30),
 ('DATE_FORMAT', 'DFMT004', 'MM/dd/yyyy', 'US Month/Day/Year (e.g. 10/17/2026)', 40),
 ('DATE_FORMAT', 'DFMT005', 'dd MMM yyyy', 'Short Month Name (e.g. 17 Oct 2026)', 50),

 -- TIME_FORMAT
 ('TIME_FORMAT', 'TFMT001', 'hh:mm a', '12-Hour format with AM/PM (e.g. 09:45 PM)', 10),
 ('TIME_FORMAT', 'TFMT002', 'hh:mm:ss a', '12-Hour format with seconds (e.g. 09:45:30 PM)', 20),
 ('TIME_FORMAT', 'TFMT003', 'HH:mm', '24-Hour military time (e.g. 21:45)', 30),
 ('TIME_FORMAT', 'TFMT004', 'HH:mm:ss', '24-Hour with seconds (e.g. 21:45:30)', 40),

 -- CREDIT_NOTE_REASON
 ('CREDIT_NOTE_REASON', 'CNR001', 'Sales Return', 'Goods returned by customer', 10),
 ('CREDIT_NOTE_REASON', 'CNR002', 'Rate Difference', 'Price or rate difference correction', 20),
 ('CREDIT_NOTE_REASON', 'CNR003', 'Post-Sale Discount', 'Turnover or post-sale discount allowance', 30),
 ('CREDIT_NOTE_REASON', 'CNR004', 'Correction', 'Clerical invoice correction', 40),

 -- DEBIT_NOTE_REASON
 ('DEBIT_NOTE_REASON', 'DNR001', 'Purchase Return', 'Goods returned to supplier', 10),
 ('DEBIT_NOTE_REASON', 'DNR002', 'Rate Difference', 'Purchase rate correction', 20),
 ('DEBIT_NOTE_REASON', 'DNR003', 'Supplier Discount', 'Supplier incentive or debit note', 30),
 ('DEBIT_NOTE_REASON', 'DNR004', 'Correction', 'Purchase bill clerical correction', 40)
) AS v(category_code, code, value, description, ord) ON v.category_code = mc.category_code
WHERE NOT EXISTS (
    SELECT 1 FROM lookup_master x
    WHERE UPPER(TRIM(x.lookup_type)) = UPPER(TRIM(mc.category_name))
      AND (x.lookup_code = v.code OR UPPER(TRIM(x.lookup_value)) = UPPER(TRIM(v.value)))
);
