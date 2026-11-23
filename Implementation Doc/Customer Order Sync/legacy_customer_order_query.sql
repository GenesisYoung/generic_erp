/* ============================================================================
   Default old-ERP (高格 VEPRO, SQL Server) query for the customer order sync.
   The same text is the default of erp.legacy.customer-order.query in
   application.yml. Read-only; SQL Server 2008 compatible.

   Facts it relies on (checks S1-S3, 2026-11-20, last 12 months):
     - About 449 orders a year (12-55 a month), so every run re-reads the
       orders dated in the last 60 days (:since) and compares them by ERI.
     - UPDATETIME is empty on 102 of 449 orders and INDATETIME on 104, so a
       "changed since" filter would miss orders. Not used.
     - SCRUTINY (old-ERP audit) is filled on only 34 of 449: informational only,
       never a trigger.
     - CLOSETYPE: 0 on 444 orders (open), 1 or 2 on 5 (closed). ORDSTATE is
       always 0. ORDNO (customer PO number) is never filled.
   Assumed, being checked (S5): ORDCORD2.MASTERI = ORDCORD1.ERI.
   If it is wrong, only line_count is empty; nothing else depends on it.

   Output columns (names are required by the reader):
     legacy_id, order_no, customer_code, customer_name, order_date,
     delivery_date, sales_code, line_count, total_qty, legacy_audited (0/1),
     closed (0/1)
   ============================================================================ */
SELECT
    RTRIM(h.ERI)                                          AS legacy_id,
    RTRIM(h.CODE)                                         AS order_no,
    RTRIM(h.CUST)                                         AS customer_code,
    RTRIM(c.CNAME)                                        AS customer_name,
    h.SDATE                                               AS order_date,
    h.DLVDATE                                             AS delivery_date,
    RTRIM(h.STAFF)                                        AS sales_code,
    l.line_count                                          AS line_count,
    h.QTY                                                 AS total_qty,
    CASE WHEN ISNULL(h.SCRUTINY, '') <> '' THEN 1 ELSE 0 END AS legacy_audited,
    CASE WHEN h.CLOSETYPE <> 0 THEN 1 ELSE 0 END          AS closed
FROM ORDCORD1 h WITH (NOLOCK)
LEFT JOIN (
    SELECT MASTERI, COUNT(*) AS line_count
    FROM ORDCORD2 WITH (NOLOCK)
    GROUP BY MASTERI
) l ON l.MASTERI = h.ERI
LEFT JOIN CUSTOMER c WITH (NOLOCK) ON c.CODE = h.CUST
WHERE h.SDATE >= :since
  AND h.CODE IS NOT NULL
ORDER BY h.SDATE, h.CODE;
