/* =============================================================================
   Old-ERP queries for purchase placement tasks (采购下单任务).
   The live copies are erp.legacy.placement-source.sample-query / production-query
   in application.yml (override with ERP_LEGACY_PLACEMENT_SAMPLE_QUERY /
   ERP_LEGACY_PLACEMENT_PRODUCTION_QUERY).

   What the checks found (ERP Design/outputs/old-erp-discovery/
   legacy_placement_source_check.sql + _round2.sql, 2026-11-24/25):
   - Purchase order numbers (ORDSPHS1.CODE) start with P (production) or YP (sample).
   - Purchase applications 采购申请 = ORDREQ1 (header, CODE like R261113001) /
     ORDREQ2 (lines). They are not linked to customer orders; the sample order is
     only written in the memo ("26256样品单 FL36-400W 驱动"). PO lines point to them
     with ORDSPHS2.SRCERI = ORDREQ2.ERI.
   - Purchase plans 采购计划 = PLANORDER1 / PLANORDER2. PLANORDER1.CODE is the
     customer order no. (26394-W53, 26382-A, YTSC-26027). PO lines point to them
     with ORDSPHS2.PLANERI = PLANORDER2.ERI.
   - There is NO "purchase confirmed" column. 审核 (SCRUTINY) is empty on 74 of 80
     applications and 124 of 420 plans that already have POs (last 180 days), so it
     is not the confirmation. ORDCREATED is 1 even on plans without a PO.
     ORDREQ2.PHSQTY > 0 on exactly the lines that have a PO (135 vs 248, Q4).
   => "Confirmed" = the document has its first purchase order (or was closed,
      CLOSETYPE <> 0). This is the same rule as before: a task closes at the
      first PO.

   Contract: order_no, source_no, source_date, confirmed (0/1), note, first_po_no.
   ============================================================================= */

/* Sample orders <- purchase applications. */
SELECT
    RTRIM(h.CODE)                                         AS order_no,
    RTRIM(h.CODE)                                         AS source_no,
    h.SDATE                                               AS source_date,
    LEFT(RTRIM(h.MEMO), 200)                              AS note,
    po.first_po_no                                        AS first_po_no,
    CASE WHEN po.first_po_no IS NOT NULL OR h.CLOSETYPE <> 0 THEN 1 ELSE 0 END AS confirmed
FROM ORDREQ1 h WITH (NOLOCK)
OUTER APPLY (
    SELECT TOP 1 RTRIM(p1.CODE) AS first_po_no
    FROM ORDREQ2 l WITH (NOLOCK)
    JOIN ORDSPHS2 p  WITH (NOLOCK) ON p.SRCERI = l.ERI
    JOIN ORDSPHS1 p1 WITH (NOLOCK) ON p1.ERI = p.MASTERI
    WHERE l.MASTERI = h.ERI
    ORDER BY p1.SDATE, p1.CODE
) po
WHERE h.SDATE >= DATEADD(day, -30, GETDATE())   -- the application uses :since
  AND h.CODE IS NOT NULL
ORDER BY h.SDATE DESC;

/* Production orders <- purchase plans. */
SELECT
    RTRIM(h.CODE)                                         AS order_no,
    RTRIM(h.CODE)                                         AS source_no,
    h.SDATE                                               AS source_date,
    LEFT(RTRIM(h.MEMO), 200)                              AS note,
    po.first_po_no                                        AS first_po_no,
    CASE WHEN po.first_po_no IS NOT NULL OR h.CLOSETYPE <> 0 THEN 1 ELSE 0 END AS confirmed
FROM PLANORDER1 h WITH (NOLOCK)
OUTER APPLY (
    SELECT TOP 1 RTRIM(p1.CODE) AS first_po_no
    FROM PLANORDER2 l WITH (NOLOCK)
    JOIN ORDSPHS2 p  WITH (NOLOCK) ON p.PLANERI = l.ERI
    JOIN ORDSPHS1 p1 WITH (NOLOCK) ON p1.ERI = p.MASTERI
    WHERE l.MASTERI = h.ERI
    ORDER BY p1.SDATE, p1.CODE
) po
WHERE h.SDATE >= DATEADD(day, -30, GETDATE())   -- the application uses :since
  AND h.CODE IS NOT NULL
ORDER BY h.SDATE DESC;
