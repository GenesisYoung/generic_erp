/* ============================================================================
   Old ERP (高格 VEPRO, SQL Server) -> purchase_order_line_tb
   This is the SELECT the sync job runs for order lines
   (erp.legacy.purchase-order.line-query). The same text is the default in
   application.yml; keep the two in sync.

   :poNos is a list of order numbers (ORDSPHS1.CODE) the header sync just read.
   The reader sends at most 500 numbers per statement.

   plan_type / plan_type_name decide which follow-up task a line belongs to.
   On the follow-up template page ("Plan groups" tab) each plan type value is
   put into a plan group; a group marked "own task" gets its own follow-up
   task series per order.

   Results of legacy_plan_type_check.sql (2026-11-24, audited POs, last 365
   days: 8,658 orders, 20,760 lines):
     - ORDSPHS1.TYPE/VCHRTYPE/ISSUETYPE/ISMOLDSPHS, ORDSPHS2.TYPE, ORDREQ1/2.TYPE:
       always 0, useless.
     - PRODUCT.BCLAS / MCLAS (+ names): empty on every line, useless.
     - PLANORDER1.PLANTYPE: 0 (12,221 lines), 2 (5,513), 1 (355), no plan (2,671);
       never mixed inside one order.
     - PRODUCT.CLAS -> PRODCLAS: filled, a 3-level tree (e.g. 306-1 灯体贴 under
       306, 305 说明书 under 3). Top levels seen: 1xx electronics, 2xx structure /
       hardware, 3xx packaging, 4 extra fees.
     - 2,410 orders buy for more than one customer order.
   OPEN QUESTION: the user has not chosen the field yet. The default below
   uses the material class one level up (PRODCLAS.UPPERCLAS, e.g. 306 for
   306-1), or the class itself when it has no upper class.
   To switch, change only the two plan_type columns (and the joins they need).

   ORDSPHS2.PROD = PRODUCT.CODE: confirmed by the check (P4 finds classes).
   SQL Server 2008 compatible.
   ============================================================================ */
SELECT
    RTRIM(h.CODE)                                         AS po_no,
    l.SERIAL                                              AS line_no,
    RTRIM(l.PROD)                                         AS material_code,
    RTRIM(pr.CNAME)                                       AS material_name,
    COALESCE(NULLIF(RTRIM(c.UPPERCLAS), ''), RTRIM(pr.CLAS)) AS plan_type,
    COALESCE(RTRIM(u.NAME), RTRIM(c.NAME))                AS plan_type_name,
    l.QTY                                                 AS qty,
    l.RECQTY                                              AS received_qty
FROM ORDSPHS1 h WITH (NOLOCK)
JOIN ORDSPHS2 l WITH (NOLOCK) ON l.MASTERI = h.ERI
LEFT JOIN PRODUCT pr WITH (NOLOCK) ON pr.CODE = l.PROD
LEFT JOIN PRODCLAS c WITH (NOLOCK) ON c.CODE = pr.CLAS
LEFT JOIN PRODCLAS u WITH (NOLOCK) ON u.CODE = c.UPPERCLAS
WHERE h.CODE IN (:poNos)
ORDER BY h.CODE, l.SERIAL

/* Alternative: plan type of the production plan the line came from.
SELECT
    RTRIM(h.CODE) AS po_no, l.SERIAL AS line_no, RTRIM(l.PROD) AS material_code, RTRIM(pr.CNAME) AS material_name,
    CAST(p1.PLANTYPE AS VARCHAR(10)) AS plan_type, NULL AS plan_type_name,
    l.QTY AS qty, l.RECQTY AS received_qty
FROM ORDSPHS1 h WITH (NOLOCK)
JOIN ORDSPHS2 l WITH (NOLOCK) ON l.MASTERI = h.ERI
LEFT JOIN PRODUCT pr WITH (NOLOCK) ON pr.CODE = l.PROD
LEFT JOIN PLANORDER2 p2 WITH (NOLOCK) ON p2.ERI = l.PLANERI
LEFT JOIN PLANORDER1 p1 WITH (NOLOCK) ON p1.ERI = p2.MASTERI
WHERE h.CODE IN (:poNos)
ORDER BY h.CODE, l.SERIAL
*/
