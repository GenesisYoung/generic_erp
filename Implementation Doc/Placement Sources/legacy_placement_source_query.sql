/* =============================================================================
   DRAFT old-ERP queries for purchase placement tasks (采购下单任务).
   Not active yet: application.yml leaves both queries blank until the
   confirmation column is known, and the job keeps using order audits until then.

   Checked by: ERP Design/outputs/old-erp-discovery/legacy_placement_source_check.sql
   (R1-R5, sent 2026-11-24). Every line marked "TO CONFIRM" is an assumption.

   Contract (both queries): document lines dated on or after :since (a date)
     order_no     the order the document buys for (YP... sample, P... production)
     source_no    the purchase application / purchase plan number
     source_date  the document date; the 2-day deadline starts here
     confirmed    1 = confirmed for purchase (采购确认), 0 = not yet
   Set them with ERP_LEGACY_PLACEMENT_SAMPLE_QUERY / ERP_LEGACY_PLACEMENT_PRODUCTION_QUERY
   or in erp.legacy.placement-source.* of application.yml.
   ============================================================================= */

/* Sample orders <- purchase applications (采购申请). */
SELECT
    RTRIM(r1.ORDCODE)                                   AS order_no,     -- TO CONFIRM: column holding YP... (R3)
    RTRIM(r1.CODE)                                      AS source_no,    -- TO CONFIRM (R2)
    r1.SDATE                                            AS source_date,  -- TO CONFIRM (R2)
    CASE WHEN r2.CONFIRMFLAG = 1 THEN 1 ELSE 0 END      AS confirmed     -- TO CONFIRM: 采购确认 column + value (R4)
FROM ORDREQ1 r1 WITH (NOLOCK)                                            -- TO CONFIRM: 采购申请 header (R1)
JOIN ORDREQ2 r2 WITH (NOLOCK) ON r2.MASTERI = r1.ERI                     -- line -> header join used by earlier checks
WHERE r1.SDATE >= :since
  AND RTRIM(r1.ORDCODE) LIKE 'YP%';

/* Production orders <- purchase plans (采购计划). The plan line -> customer order
   chain PLANORDER2.FROMORDERI = ORDCORD2.ERI, ORDCORD2.MASTERI = ORDCORD1.ERI was
   confirmed on 2,682 of 2,735 plan lines (round 2/3 checks, 2026-11-20). */
SELECT
    RTRIM(co.CODE)                                      AS order_no,
    RTRIM(p1.CODE)                                      AS source_no,    -- TO CONFIRM (R2)
    p1.SDATE                                            AS source_date,  -- TO CONFIRM (R2)
    CASE WHEN p2.CONFIRMFLAG = 1 THEN 1 ELSE 0 END      AS confirmed     -- TO CONFIRM: 采购确认 column + value (R4)
FROM PLANORDER1 p1 WITH (NOLOCK)
JOIN PLANORDER2 p2 WITH (NOLOCK) ON p2.MASTERI = p1.ERI
JOIN ORDCORD2   cl WITH (NOLOCK) ON cl.ERI = p2.FROMORDERI
JOIN ORDCORD1   co WITH (NOLOCK) ON co.ERI = cl.MASTERI
WHERE p1.SDATE >= :since
  AND RTRIM(co.CODE) LIKE 'P%';
