/* ============================================================================
   Old ERP read-only checks for the purchase order sync.
   Run each query in DataGrip with the SELECT-only login and send back the
   results (counts only; no order details are needed).
   Each answers one assumption in legacy_po_sync_query.sql.
   ============================================================================ */

/* C1 (A1). Do lines join to headers on ERI? Expect matched ~= all lines.
   Result 2026-11-20: 86,136 lines, 0 on ERI, 86,126 on MASTERI -> join on MASTERI. */
SELECT
    (SELECT COUNT(*) FROM ORDSPHS2)                                       AS lines_total,
    (SELECT COUNT(*) FROM ORDSPHS2 l JOIN ORDSPHS1 h ON h.ERI = l.ERI)     AS lines_matched_on_eri,
    (SELECT COUNT(*) FROM ORDSPHS2 l JOIN ORDSPHS1 h ON h.ERI = l.MASTERI) AS lines_matched_on_masteri;

/* C2 (A2). What CLOSETYPE values exist, and how many orders each, last 2 years. */
SELECT CLOSETYPE, COUNT(*) AS orders
FROM ORDSPHS1
WHERE SDATE >= DATEADD(year, -2, GETDATE())
GROUP BY CLOSETYPE
ORDER BY CLOSETYPE;

/* C3 (A3). Audit fields: how many orders are audited or not. */
SELECT
    CASE WHEN ISNULL(SCRUTINY, '') = '' THEN 'not audited' ELSE 'audited' END AS scrutiny,
    AUDITSTATE,
    COUNT(*) AS orders
FROM ORDSPHS1
WHERE SDATE >= DATEADD(year, -2, GETDATE())
GROUP BY CASE WHEN ISNULL(SCRUTINY, '') = '' THEN 'not audited' ELSE 'audited' END, AUDITSTATE
ORDER BY 1, 2;

/* C4 (A4). Is STAFF a staff code or a name? Counts only. */
SELECT
    SUM(CASE WHEN EXISTS (SELECT 1 FROM STAFFER f WHERE f.CODE  = h.STAFF) THEN 1 ELSE 0 END) AS staff_is_code,
    SUM(CASE WHEN EXISTS (SELECT 1 FROM STAFFER f WHERE f.CNAME = h.STAFF) THEN 1 ELSE 0 END) AS staff_is_name,
    COUNT(*)                                                                                   AS orders
FROM ORDSPHS1 h
WHERE h.SDATE >= DATEADD(year, -1, GETDATE());

/* C5. Is UPDATETIME filled in? */
SELECT
    SUM(CASE WHEN UPDATETIME IS NULL THEN 1 ELSE 0 END) AS updatetime_null,
    COUNT(*)                                             AS orders
FROM ORDSPHS1
WHERE SDATE >= DATEADD(year, -1, GETDATE());

/* C6. Dry run of the sync query: how many rows it would read for the last 60 days. */
SELECT COUNT(*) AS rows_first_sync
FROM ORDSPHS1 h
WHERE ISNULL(h.SCRUTINY, '') <> ''
  AND (COALESCE(h.UPDATETIME, h.SCRDATE, h.INDATETIME, h.SDATE) > DATEADD(day, -60, GETDATE())
       OR (h.CLOSETYPE = 0 AND h.SDATE >= DATEADD(day, -120, GETDATE())));
