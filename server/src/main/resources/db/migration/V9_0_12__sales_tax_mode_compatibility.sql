-- v9.0.12 historical Sales GST mode compatibility.
-- Old releases permitted NULL/blank gst_type values. They represent the original
-- intra-state GST behavior and are normalized once so document export/preview is stable.
UPDATE sales_header
SET gst_type = 'GST'
WHERE gst_type IS NULL OR BTRIM(gst_type) = '';
