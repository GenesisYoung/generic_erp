/* ============================================================================
   Old ERP (高格 VEPRO, SQL Server) -> purchase_order_tb
   This is the SELECT the sync job runs (erp.legacy.purchase-order.query).
   The same text is the default in application.yml; keep the two in sync.

   Tables (from the 2026-11-20 discovery, Q3/Q4):
     ORDSPHS1  purchase order header (32,624 rows)
     ORDSPHS2  purchase order lines  (86,136 rows), SERIAL = line number
     SUPPLIER  supplier master (CODE, CNAME)

   ASSUMPTIONS TO CHECK with legacy_po_sync_check.sql before turning the sync on:
     A1  ORDSPHS2.ERI is the header's ERI (lines join on ERI + SERIAL).
     A2  ORDSPHS1.CLOSETYPE = 0 means open; any other value means closed
         (finished, short-closed or cancelled). Follow-up stops when closed.
     A3  An order counts as placed once it is audited (SCRUTINY not empty).
     A4  ORDSPHS1.STAFF holds the buyer. It may be a code or a name; either way
         map it to a user in legacy_user_map_tb if it is not the username.

   Output columns (names are required by the reader):
     po_no, supplier_code, supplier_name, buyer_code, order_date,
     expected_date, status, updated_at
   status is already one of this system's values:
     PLACED / PARTIALLY_RECEIVED / RECEIVED / CLOSED

   Which rows: orders changed after :since, plus every audited order that is
   still open and was dated in the last 120 days. Line receipts (RECQTY) can
   change without touching the header's UPDATETIME, so open orders are re-read
   on every run to notice receipts. The upsert is idempotent.
   SQL Server 2008 compatible (no CONCAT, no IIF).
   ============================================================================ */
SELECT
    RTRIM(h.CODE)                                         AS po_no,
    RTRIM(h.SUPP)                                         AS supplier_code,
    RTRIM(s.CNAME)                                        AS supplier_name,
    RTRIM(h.STAFF)                                        AS buyer_code,
    h.SDATE                                               AS order_date,
    h.DLVDATE                                             AS expected_date,
    CASE
        WHEN h.CLOSETYPE <> 0                              THEN 'CLOSED'
        WHEN l.qty > 0 AND l.recqty >= l.qty               THEN 'RECEIVED'
        WHEN l.recqty > 0                                  THEN 'PARTIALLY_RECEIVED'
        ELSE 'PLACED'
    END                                                   AS status,
    COALESCE(h.UPDATETIME, h.SCRDATE, h.INDATETIME, h.SDATE) AS updated_at
FROM ORDSPHS1 h WITH (NOLOCK)
LEFT JOIN (
    SELECT ERI, SUM(QTY) AS qty, SUM(RECQTY) AS recqty
    FROM ORDSPHS2 WITH (NOLOCK)
    GROUP BY ERI
) l ON l.ERI = h.ERI
LEFT JOIN SUPPLIER s WITH (NOLOCK) ON s.CODE = h.SUPP
WHERE ISNULL(h.SCRUTINY, '') <> ''
  AND h.CODE IS NOT NULL
  AND h.SDATE IS NOT NULL
  AND (
        COALESCE(h.UPDATETIME, h.SCRDATE, h.INDATETIME, h.SDATE) > :since
     OR (h.CLOSETYPE = 0 AND h.SDATE >= DATEADD(day, -120, GETDATE()))
  )
ORDER BY COALESCE(h.UPDATETIME, h.SCRDATE, h.INDATETIME, h.SDATE), h.CODE
