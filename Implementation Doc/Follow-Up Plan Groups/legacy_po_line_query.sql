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

   OPEN QUESTION (2026-11-24): which old-ERP field is the "purchase plan
   type" is not decided yet. The default below uses the material's big class
   (PRODUCT.BCLAS / BCLASNAME). Other candidates, checked by
   "ERP Design/outputs/old-erp-discovery/legacy_plan_type_check.sql":
     - PRODUCT.MCLAS / MCLASNAME               (material middle class)
     - PLANORDER1.PLANTYPE via ORDSPHS2.PLANERI -> PLANORDER2.ERI -> PLANORDER2.MASTERI
     - ORDSPHS1.TYPE / ORDSPHS2.TYPE            (order / line type)
   To switch, change only the two plan_type columns (and the joins they need).

   ASSUMPTION to check: ORDSPHS2.PROD holds PRODUCT.CODE (not PRODUCT.ERI).
   If plan_type comes back empty on most lines, join on pr.ERI = l.PROD.
   SQL Server 2008 compatible.
   ============================================================================ */
SELECT
    RTRIM(h.CODE)                                         AS po_no,
    l.SERIAL                                              AS line_no,
    RTRIM(l.PROD)                                         AS material_code,
    RTRIM(pr.CNAME)                                       AS material_name,
    RTRIM(pr.BCLAS)                                       AS plan_type,
    RTRIM(pr.BCLASNAME)                                   AS plan_type_name,
    l.QTY                                                 AS qty,
    l.RECQTY                                              AS received_qty
FROM ORDSPHS1 h WITH (NOLOCK)
JOIN ORDSPHS2 l WITH (NOLOCK) ON l.MASTERI = h.ERI
LEFT JOIN PRODUCT pr WITH (NOLOCK) ON pr.CODE = l.PROD
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
