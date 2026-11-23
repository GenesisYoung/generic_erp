package com.gsgd.generic_erp.pmc;

import static org.junit.jupiter.api.Assertions.*;
import static com.gsgd.generic_erp.pmc.AuditTypes.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.*;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.mock.web.MockMultipartFile;
import com.gsgd.generic_erp.configuration.security.PermissionChecker;

@SpringJUnitConfig(AuditIntegrationTest.Config.class)
class AuditIntegrationTest {
    static final String EVIDENCE_PATH = "/tmp/erp-audit-evidence-test-" + UUID.randomUUID();

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = AuditTaskRepository.class)
    @Import({ AuditService.class, AuditAccess.class, AuditJson.class, PermissionChecker.class })
    static class Config {
        @Bean
        DataSource dataSource() {
            return new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource ds) {
            return new JdbcTemplate(ds);
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var f = new LocalContainerEntityManagerFactoryBean();
            f.setDataSource(ds);
            f.setPackagesToScan("com.gsgd.generic_erp.pmc");
            f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop", "hibernate.jdbc.time_zone", "UTC"));
            return f;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory f) {
            return new JpaTransactionManager(f);
        }

        @Bean
        PersonalEvidenceService personal(AuditAccess access, AuditService tasks,
                PersonalEvidenceRepository repository) {
            return new PersonalEvidenceService(access, tasks, repository, EVIDENCE_PATH);
        }

        @Bean
        AuditEvidenceService evidence(AuditService service, AuditAccess access, AuditEvidenceRepository repository,
                PersonalEvidenceService personal) {
            return new AuditEvidenceService(service, access, repository, personal, EVIDENCE_PATH);
        }
    }

    @Autowired
    AuditService service;
    @Autowired
    AuditJson json;
    @Autowired
    PersonalEvidenceService personal;
    @Autowired
    PersonalEvidenceRepository personalFiles;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    AuditTaskRepository tasks;
    @Autowired
    AuditOperationRepository operations;
    @Autowired
    AuditRuleRepository rules;
    @Autowired
    AuditEvidenceRepository files;
    @Autowired
    AuditEvidenceService evidence;

    @BeforeEach
    void setup() {
        personalFiles.deleteAll();
        operations.deleteAll();
        files.deleteAll();
        tasks.deleteAll();
        rules.deleteAll();
        jdbc.execute(
                "create table if not exists user_tb(id bigint primary key,username varchar(50),display_name varchar(50),status boolean,is_enabled int)");
        jdbc.execute("create table if not exists user_departments_tb(user_id bigint,dept_id bigint)");
        jdbc.execute(
                "create table if not exists appendix_tb(appendix_id bigint primary key,active boolean,created_by bigint)");
        jdbc.update("delete from user_departments_tb");
        jdbc.update("delete from user_tb");
        jdbc.update("delete from appendix_tb");
        for (int i = 1; i <= 5; i++) {
            jdbc.update("insert into user_tb values (?,?,?,?,?)", i, "user" + i, "人员" + i, true, 1);
            jdbc.update("insert into user_departments_tb values (?,?)", i, i == 3 || i == 5 ? 20 : 10);
            jdbc.update("insert into appendix_tb values (?,?,?)", i * 100, true, i);
        }
        login(1, false);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    static void login(int user, boolean team) {
        var authorities = new ArrayList<SimpleGrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority("menu.order-audit-task"));
        if (team)
            authorities.add(new SimpleGrantedAuthority("pmc.order-audit.scope.team"));
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("user" + user, null, authorities));
    }

    String key() {
        return UUID.randomUUID().toString();
    }

    Draft draft(String order) {
        return new Draft(order, 1, "核对资料与交期", "1", Instant.now().minusSeconds(3600), true, List.of(
                new Material("m1", "客户订单", true, 1, "", null, null, Instant.now().minusSeconds(3600), "appendix:100")),
                null);
    }

    Task create(String order) {
        login(1, false);
        return service.create(draft(order), key());
    }

    ActionInput input(long version) {
        return new ActionInput(version, null, null, null, null, null, null, null, null, null, null);
    }

    ActionInput accept(long version) {
        return new ActionInput(version, Instant.now().plusSeconds(3600), "主管批准时限", null, null, null, null, null, null,
                null, null);
    }

    void approveRules(String node) {
        login(1, false);
        var s = service.saveRule("default", new Rule("default", service.getRules().version, "Asia/Shanghai",
                "必备资料齐备后受理", node, List.of("客户订单"), "业务规则依据", "appendix:100"), key());
        login(2, true);
        service.approveRule("default", new RuleDecision(s.version, "APPROVED", "批准使用该规则", "appendix:200"), key());
    }

    Task accepted() {
        approveRules("DELIVERY");
        Task t = create("ORD-" + key());
        login(2, true);
        return service.act(t.id, "accept", accept(t.lockVersion), key());
    }

    Task submitted() {
        Task t = accepted();
        login(1, false);
        return service.act(t.id, "submit", new ActionInput(t.lockVersion, null, null, null, "appendix:100", null, null,
                null, "资料与交期已核对", Instant.now(), null), key());
    }

    void error(int status, String code, Runnable fn) {
        var e = assertThrows(AuditException.class, fn::run);
        assertEquals(status, e.status);
        assertEquals(code, e.code);
    }

    @Test
    void rulesAreUnapprovedByDefaultAndCannotBeSelfApproved() {
        assertNull(service.metadata().acceptanceRule());
        Task t = create("ORD-1");
        login(2, true);
        error(422, "RULE_NOT_APPROVED", () -> service.act(t.id, "accept", accept(t.lockVersion), key()));
        login(1, false);
        var s = service.saveRule("default",
                new Rule("default", 0L, "Asia/Shanghai", "齐备受理", "DELIVERY", List.of("客户订单"), "依据", "appendix:100"),
                key());
        error(403, "SELF_APPROVAL", () -> service.approveRule("default",
                new RuleDecision(s.version, "APPROVED", "批准", "appendix:100"), key()));
    }

    @Test
    void cannotDeleteOrDowngradeMandatoryChecklistToBypassAcceptance() {
        approveRules("DELIVERY");
        Task t = create("ORD-2");
        login(1, false);
        var d = draft("ORD-2");
        t = service.update(t.id,
                new Draft(d.orderNumber(), 1, d.title(), d.ownerUserId(), d.receivedAt(), true, List
                        .of(new Material("optional", "其他资料", false, 1, "", null, null, Instant.now(), "appendix:100")),
                        t.lockVersion),
                key());
        login(2, true);
        Task current = t;
        error(422, "REQUIRED_MATERIAL_MISSING",
                () -> service.act(current.id, "accept", accept(current.lockVersion), key()));
    }

    @Test
    void onlyExplicitTeamScopeSeesDepartmentTasksAndOutsideOwnerIsRejected() {
        Task t = create("ORD-3");
        login(2, false);
        error(404, "NOT_FOUND", () -> service.detail(t.id));
        login(2, true);
        assertEquals(t.id, service.detail(t.id).id);
        login(3, true);
        assertEquals(0, service.list(new Query(null, null, null, null, null, null, 1, 20)).total());
        error(404, "NOT_FOUND", () -> service.detail(t.id));
        var d = draft("ORD-4");
        error(403, "USER_OUT_OF_SCOPE", () -> service.create(d, key()));
    }

    @Test
    void readOnlyPermissionCannotWrite() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user1", null,
                List.of(new SimpleGrantedAuthority("menu.order-audit-task.read"))));
        assertFalse(service.metadata().canCreate());
        error(403, "FORBIDDEN", () -> service.create(draft("ORD-5"), key()));
    }

    @Test
    void idempotencyReplaysExactlyAndRejectsPayloadChangesAndDuplicateSources() {
        String k = key();
        Draft d = draft("ORD-6");
        Task t = service.create(d, k);
        Task replay = service.create(d, k);
        assertEquals(t.id, replay.id);
        assertEquals(1, tasks.count());
        error(409, "IDEMPOTENCY_CONFLICT", () -> service.create(draft("OTHER"), k));
        error(409, "DUPLICATE_ROUND", () -> service.create(d, key()));
        login(2, true); // key is scoped to the caller, not globally shared.
        assertEquals(1, service.list(new Query(null, null, null, null, null, null, 1, 20)).total());
    }

    @Test
    void optimisticVersionConflictKeepsWinningEdit() {
        Task t = create("ORD-7");
        Draft d = draft("ORD-7");
        service.update(t.id,
                new Draft(d.orderNumber(), 1, "获胜版本", "1", d.receivedAt(), true, d.materials(), t.lockVersion), key());
        error(409, "VERSION_CONFLICT", () -> service.update(t.id,
                new Draft(d.orderNumber(), 1, "旧版本", "1", d.receivedAt(), true, d.materials(), t.lockVersion), key()));
        assertEquals("获胜版本", service.detail(t.id).title);
    }

    @Test
    void acceptedTaskFreezesOriginalDeadlineAndCannotBeEditedDirectly() {
        Task t = accepted();
        assertEquals(t.originalDueAt, t.effectiveDueAt);
        assertNotNull(t.acceptanceRuleSnapshot);
        login(1, false);
        error(409, "ILLEGAL_STATE", () -> service.update(t.id, new Draft(t.orderNumber, t.round, t.title, t.ownerUserId,
                t.receivedAt, true, t.materials, t.lockVersion), key()));
        error(409, "ILLEGAL_STATE", () -> service.act(t.id, "accept", accept(t.lockVersion), key()));
    }

    @Test
    void blockingDoesNotPauseDeadlineAndCannotAssignUnscopedCollaborator() {
        Task t = accepted();
        login(1, false);
        error(403, "USER_OUT_OF_SCOPE", () -> service.act(t.id, "block", new ActionInput(t.lockVersion, null, null,
                "缺资料", null, "补资料", "5", Instant.now().plusSeconds(600), null, null, null), key()));
        Task b = service.act(t.id, "block", new ActionInput(t.lockVersion, null, null, "缺资料", null, "补资料", "1",
                Instant.now().plusSeconds(600), null, null, null), key());
        assertEquals(t.effectiveDueAt, b.effectiveDueAt);
        assertEquals("BLOCKED", b.status);
    }

    @Test
    void submittedByCannotVerifyEvenIfTheyHaveTeamScope() {
        Task t = submitted();
        login(1, true);
        error(403, "SELF_VERIFY", () -> service.act(t.id, "verify",
                new ActionInput(t.lockVersion, null, null, "自核验", "appendix:100", null, null, null, null, null, "PASS"),
                key()));
    }

    @Test
    void proofReturnPreservesEverySubmissionAndVerification() {
        Task t = submitted();
        login(2, true);
        Task returned = service.act(t.id, "verify", new ActionInput(t.lockVersion, null, null, "补依据", "appendix:200",
                null, null, null, null, null, "NEED_PROOF"), key());
        assertEquals("RETURNED", returned.status);
        login(1, false);
        Task next = service.act(t.id, "submit", new ActionInput(returned.lockVersion, null, null, null, "appendix:100",
                null, null, null, "已补证", Instant.now(), null), key());
        assertEquals(2, next.submissions.size());
        assertEquals("NEED_PROOF", next.submissions.get(0).verificationRecords.get(0).result());
        assertNull(next.verificationResult);
        login(2, true);
        Task fail = service.act(t.id, "verify", new ActionInput(next.lockVersion, null, null, "交付不符合", "appendix:200",
                null, null, null, null, null, "FAIL"), key());
        assertEquals("VERIFIED", fail.status);
        assertEquals("FAIL", fail.verificationResult);
    }

    @Test
    void businessApprovalCompletionIsSeparateFromEmployeeReportedFinish() {
        approveRules("BUSINESS_APPROVAL");
        Task t = create("ORD-8");
        login(2, true);
        t = service.act(t.id, "accept", accept(t.lockVersion), key());
        login(1, false);
        t = service.act(t.id, "submit", new ActionInput(t.lockVersion, null, null, null, "appendix:100", null, null,
                null, "已提交", Instant.now(), null), key());
        assertNull(t.completedAt);
        Task pending = t;
        login(2, true);
        error(422, "BUSINESS_CONFIRMATION_REQUIRED",
                () -> service.act(pending.id, "verify", new ActionInput(pending.lockVersion, null, null, "核验",
                        "appendix:200", null, null, null, null, null, "PASS"), key()));
        Task confirmed = service.businessConfirmation(t.id, 1, "APPROVED", "业务通过", "appendix:200", t.lockVersion,
                key());
        assertNotNull(confirmed.completedAt);
        assertEquals("SUBMITTED", confirmed.status);
        Task verified = service.act(t.id, "verify", new ActionInput(confirmed.lockVersion, null, null, "核验通过",
                "appendix:200", null, null, null, null, null, "PASS"), key());
        assertEquals("VERIFIED", verified.status);
    }

    @Test
    void deadlineChangesNeedIndependentApprovalAndPreserveOriginalDeadline() {
        Task t = accepted();
        login(1, false);
        Instant newDue = Instant.now().plusSeconds(7200);
        Task requested = service.requestChange(t.id,
                new ChangeInput(t.lockVersion, "DEADLINE", "客户调整", "appendix:100", newDue, null, null), key());
        assertEquals(t.effectiveDueAt, requested.effectiveDueAt);
        String rid = requested.changeRequests.get(0).requestId;
        login(1, true);
        error(403, "SELF_APPROVAL", () -> service.decideChange(t.id, rid,
                new DecisionInput(requested.lockVersion, 1L, "APPROVED", "批准", "appendix:100"), key()));
        login(2, true);
        Task approved = service.decideChange(t.id, rid,
                new DecisionInput(requested.lockVersion, 1L, "APPROVED", "批准延期", "appendix:200"), key());
        assertEquals(newDue, approved.effectiveDueAt);
        assertEquals(t.originalDueAt, approved.originalDueAt);
    }

    @Test
    void cannotCreateAnUnapprovedSecondRound() {
        Draft d = draft("ORD-9");
        error(422, "REVIEW_APPROVAL_REQUIRED", () -> service.create(
                new Draft(d.orderNumber(), 2, d.title(), d.ownerUserId(), d.receivedAt(), true, d.materials(), null),
                key()));
    }

    @Test
    void evidenceReferenceCannotBorrowAnUnrelatedPersonsAttachment() {
        Draft d = draft("ORD-10");
        var m = new Material("bad", "客户订单", true, 1, "", null, null, Instant.now(), "appendix:300");
        error(403, "INVALID_EVIDENCE", () -> service.create(
                new Draft(d.orderNumber(), 1, d.title(), d.ownerUserId(), d.receivedAt(), true, List.of(m), null),
                key()));
    }

    @Test
    void multipartEvidenceUsesTaskScopeAndCanBeReplayedSafely() throws Exception {
        Task t = create("ORD-11");
        String k = key();
        var file = new MockMultipartFile("file", "proof.txt", "text/plain",
                "受控凭证".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var r = evidence.upload(t.id, file, "资料依据", t.lockVersion, k);
        assertEquals(r, evidence.upload(t.id, file, "资料依据", t.lockVersion, k));
        assertTrue(evidence.download(t.id, r.evidenceRef()).resource().exists());
        login(3, true);
        error(404, "NOT_FOUND", () -> {
            try {
                evidence.download(t.id, r.evidenceRef());
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void unsupportedFileContentIsRejectedBeforePersisting() {
        Task t = create("ORD-12");
        var f = new MockMultipartFile("file", "bad.pdf", "application/pdf", "<script>bad</script>".getBytes());
        error(422, "INVALID_FILE", () -> {
            try {
                evidence.upload(t.id, f, "证明", t.lockVersion, key());
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        });
        assertEquals(0, files.count());
    }

    @Test
    void strictJsonRefusesForgedServerFieldsAndLocalTimes() {
        error(400, "INVALID_BODY",
                () -> json.read("{\"orderNumber\":\"x\",\"acceptedAt\":\"2026-01-01T00:00:00Z\"}", Draft.class));
        error(400, "INVALID_BODY", () -> json.read("{\"receivedAt\":\"2026-01-01T12:00\"}", Draft.class));
        error(400, "INVALID_BODY", () -> json.read("{\"round\":1.5}", Draft.class));
        error(400, "INVALID_BODY", () -> json.read("{\"round\":\"1\"}", Draft.class));
    }

    @Test
    void paginationFiltersAndExportUseFullAuthorizedResult() {
        create("=FORMULA");
        create("OTHER");
        assertEquals(1, service.list(new Query("FORMULA", null, null, null, null, null, 1, 20)).total());
        assertEquals(0, service.list(new Query(null, null, null, Instant.now(), null, null, 1, 20)).total());
        assertTrue(service.export(new Query(null, null, null, null, null, null, 1, 20)).contains("'=FORMULA"));
        login(3, true);
        assertFalse(service.export(new Query(null, null, null, null, null, null, 1, 20)).contains("FORMULA"));
    }

    @Test
    void concurrentSubmissionsWithSameVersionCreateOnlyOneVersion() throws Exception {
        Task t = accepted();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> submit = () -> {
                login(1, false);
                try {
                    service.act(t.id, "submit", new ActionInput(t.lockVersion, null, null, null, "appendix:100", null,
                            null, null, "并发提交", Instant.now(), null), key());
                    return true;
                } catch (AuditException e) {
                    assertEquals(409, e.status);
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };
            var futures = pool.invokeAll(List.of(submit, submit));
            assertEquals(1, futures.stream().filter(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).count());
            login(1, false);
            assertEquals(1, service.detail(t.id).submissions.size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void httpSerializesContractAndMapsErrorsWithoutSqlDetails() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new com.gsgd.generic_erp.controller.pmc.OrderAuditController(service, json))
                .setControllerAdvice(new AuditErrorHandler()).build();
        var response = mvc
                .perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/pmc/order-audit-tasks").contentType("application/json")
                        .header("Idempotency-Key", key()).content(json.write(draft("HTTP-1"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                .andReturn().getResponse();
        assertTrue(response.getContentAsString().contains("\"receivedAt\":\""));
        Task t = json.read(response.getContentAsString(), Task.class);
        assertEquals("DRAFT", t.status);
        assertTrue(t.allowedActions.contains("edit"));
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/pmc/order-audit-tasks")
                        .contentType("application/json").content(json.write(draft("HTTP-2"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        var bad = mvc
                .perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/pmc/order-audit-tasks").contentType("application/json")
                        .header("Idempotency-Key", key()).content("{\"acceptedAt\":\"2026-01-01T00:00:00Z\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                .andReturn().getResponse();
        assertTrue(bad.getContentAsString().contains("INVALID_BODY"));
        assertFalse(bad.getContentAsString().contains("pmc_order"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/pmc/order-audit-tasks").param("dueFrom", "yesterday"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void errorMessagesFollowTheFrontendLanguage() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new com.gsgd.generic_erp.controller.pmc.OrderAuditController(service, json))
                .setControllerAdvice(new AuditErrorHandler()).build();
        java.util.function.Function<String, String> messageIn = lang -> {
            try {
                String body = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/pmc/order-audit-tasks").param("dueFrom", "yesterday")
                        .header("Accept-Language", lang))
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isBadRequest())
                        .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
                return json.read(body, java.util.Map.class).get("message").toString();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
        String en = messageIn.apply("en-US");
        String zh = messageIn.apply("zh-CN");
        assertFalse(en.codePoints().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN), en);
        assertTrue(zh.codePoints().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN), zh);
    }

    @Test
    void httpReadOnlyCallerCannotCreateAndUnrelatedActionFieldsAreRejected() throws Exception {
        Task t = accepted();
        login(1, false);
        error(400, "INVALID_BODY", () -> service.act(t.id, "start",
                new ActionInput(t.lockVersion, null, null, null, null, null, null, null, "恶意结论", null, null), key()));
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new com.gsgd.generic_erp.controller.pmc.OrderAuditController(service, json))
                .setControllerAdvice(new AuditErrorHandler()).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user1", null,
                List.of(new SimpleGrantedAuthority("menu.order-audit-task.read"))));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/pmc/order-audit-tasks").contentType("application/json").header("Idempotency-Key", key())
                .content(json.write(draft("DENIED"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void cannotCreateNewTaskWithAnotherTasksDedicatedEvidence() throws Exception {
        Task t = create("EVIDENCE-SOURCE");
        var file = new MockMultipartFile("file", "source.txt", "text/plain", "proof".getBytes());
        var r = evidence.upload(t.id, file, "订单资料", t.lockVersion, key());
        var d = draft("EVIDENCE-OTHER");
        var m = new Material("m1", "客户订单", true, 1, "", null, null, Instant.now(), r.evidenceRef());
        error(422, "EVIDENCE_TASK_REQUIRED", () -> service.create(
                new Draft(d.orderNumber(), 1, d.title(), d.ownerUserId(), d.receivedAt(), true, List.of(m), null),
                key()));
    }

    @Test
    void repeatedBusinessApprovalCannotMoveCompletionTime() {
        Task t = submitted();
        login(2, true);
        Task confirmed = service.businessConfirmation(t.id, 1, "APPROVED", "通过", "appendix:200", t.lockVersion, key());
        error(409, "BUSINESS_ALREADY_CONFIRMED", () -> service.businessConfirmation(t.id, 1, "APPROVED", "再通过",
                "appendix:200", confirmed.lockVersion, key()));
    }

    @Test
    void approvedReopenAndNewRoundKeepHistoricalTaskAndResults() {
        Task t = submitted();
        login(2, true);
        t = service.act(t.id, "verify",
                new ActionInput(t.lockVersion, null, null, "符合", "appendix:200", null, null, null, null, null, "PASS"),
                key());
        login(1, false);
        t = service.requestChange(t.id, new ChangeInput(t.lockVersion, "REOPEN", "批准修正", "appendix:100", null, null, 1),
                key());
        login(2, true);
        String rid = t.changeRequests.get(0).requestId;
        t = service.decideChange(t.id, rid, new DecisionInput(t.lockVersion, 1L, "APPROVED", "允许重开", "appendix:200"),
                key());
        assertEquals("RETURNED", t.status);
        assertEquals("PASS", t.submissions.get(0).verificationRecords.get(0).result());
        login(1, false);
        t = service.act(t.id, "submit", new ActionInput(t.lockVersion, null, null, null, "appendix:100", null, null,
                null, "已修正", Instant.now(), null), key());
        login(2, true);
        t = service.act(t.id, "verify",
                new ActionInput(t.lockVersion, null, null, "符合", "appendix:200", null, null, null, null, null, "PASS"),
                key());
        login(1, false);
        t = service.requestChange(t.id,
                new ChangeInput(t.lockVersion, "REVIEW_AGAIN", "客户要求新一轮", "appendix:100", null, null, 2), key());
        login(2, true);
        rid = t.changeRequests.get(1).requestId;
        t = service.decideChange(t.id, rid, new DecisionInput(t.lockVersion, 1L, "APPROVED", "允许重评", "appendix:200"),
                key());
        assertEquals("VERIFIED", t.status);
        assertEquals(2, t.submissions.size());
        String order = t.orderNumber;
        var page = service.list(new Query(order, null, null, null, null, null, 1, 20));
        assertEquals(2, page.total());
        Task second = page.items().stream().filter(x -> x.round == 2).findFirst().orElseThrow();
        assertEquals(second.id, t.reviewChildTaskId);
        assertEquals(t.id, second.reviewParentTaskId);
        assertEquals("DRAFT", second.status);
        assertNull(second.acceptedAt);
        assertTrue(second.materials.isEmpty());
    }

    @Test
    void independentVerifierCanReuseEvidenceAlreadyLinkedToTaskHistory() {
        Task t = submitted();
        login(2, true);
        t = service.act(t.id, "verify", new ActionInput(t.lockVersion, null, null, "归责待议", "appendix:200", null, null,
                null, null, null, "DISPUTED"), key());
        login(4, true);
        t = service.act(t.id, "verify", new ActionInput(t.lockVersion, null, null, "复核不符合", "appendix:200", null, null,
                null, null, null, "FAIL"), key());
        assertEquals("FAIL", t.verificationResult);
        assertEquals(2, t.submissions.get(0).verificationRecords.size());
    }

    @Test
    void metadataAndTaskCapabilitiesReflectCallerRatherThanFrontendGuesses() {
        Task t = submitted();
        login(1, true);
        assertEquals("1", service.metadata().currentUserId());
        assertTrue(service.metadata().canConfigureRules());
        assertFalse(service.detail(t.id).allowedActions.contains("confirm-business"));
        login(2, true);
        assertTrue(service.detail(t.id).allowedActions.contains("confirm-business"));
        assertTrue(service.detail(t.id).allowedActions.contains("upload-evidence"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user1", null,
                List.of(new SimpleGrantedAuthority("menu.order-audit-task.read"))));
        assertFalse(service.metadata().canExport());
        assertFalse(service.metadata().canConfigureRules());
        assertFalse(service.metadata().canApproveRules());
        assertTrue(service.detail(t.id).allowedActions.isEmpty());
    }

    @Test
    void changeApprovalCapabilityExcludesApplicantEvenIfTheyHaveTeamScope() {
        Task t = accepted();
        login(1, true);
        t = service.requestChange(t.id, new ChangeInput(t.lockVersion, "DEADLINE", "改期依据", "appendix:100",
                Instant.now().plusSeconds(9000), null, null), key());
        assertFalse(t.allowedActions.contains("approve-change"));
        login(2, true);
        assertTrue(service.detail(t.id).allowedActions.contains("approve-change"));
    }

    @Test
    void approvedMaterialCorrectionPreservesAcceptedFactsAndCanBeResubmitted() {
        Task t = accepted();
        login(1, false);
        Draft proposed = new Draft(t.orderNumber, t.round, "修正后交付要求", t.ownerUserId, t.receivedAt, true, t.materials,
                null);
        t = service.requestChange(t.id,
                new ChangeInput(t.lockVersion, "MATERIAL_CORRECTION", "规格确认", "appendix:100", null, proposed, null),
                key());
        Instant accepted = t.acceptedAt, originalDue = t.originalDueAt;
        login(2, true);
        t = service.decideChange(t.id, t.changeRequests.get(0).requestId,
                new DecisionInput(t.lockVersion, 1L, "APPROVED", "批准修正", "appendix:200"), key());
        assertEquals("BLOCKED", t.status);
        assertEquals(accepted, t.acceptedAt);
        assertEquals(originalDue, t.originalDueAt);
        assertEquals("修正后交付要求", t.title);
        login(1, false);
        t = service.act(t.id, "submit", new ActionInput(t.lockVersion, null, null, null, "appendix:100", null, null,
                null, "按修正版完成", Instant.now(), null), key());
        login(2, true);
        t = service.act(t.id, "verify",
                new ActionInput(t.lockVersion, null, null, "符合", "appendix:200", null, null, null, null, null, "PASS"),
                key());
        assertEquals("VERIFIED", t.status);
    }

    @Test
    void declineClosesUnacceptedDraftAndPreservesDecision() {
        Task t = create("DECLINE-ORDER");
        t = service.requestChange(t.id,
                new ChangeInput(t.lockVersion, "DECLINE", "业务决定不予承接", "appendix:100", null, null, null), key());
        login(2, true);
        t = service.decideChange(t.id, t.changeRequests.get(0).requestId,
                new DecisionInput(t.lockVersion, 1L, "APPROVED", "确认不承接", "appendix:200"), key());
        assertEquals("CANCELLED", t.status);
        assertNull(t.acceptedAt);
        assertEquals("业务决定不予承接", t.declinedReason);
    }

    @Test
    void aSecondPendingCancellationCannotBeApprovedAfterTaskAlreadyCancelled() {
        Task t = accepted();
        login(1, false);
        t = service.requestChange(t.id,
                new ChangeInput(t.lockVersion, "CANCEL", "第一份申请", "appendix:100", null, null, null), key());
        t = service.requestChange(t.id,
                new ChangeInput(t.lockVersion, "CANCEL", "第二份申请", "appendix:100", null, null, null), key());
        login(2, true);
        t = service.decideChange(t.id, t.changeRequests.get(0).requestId,
                new DecisionInput(t.lockVersion, 1L, "APPROVED", "批准撤销", "appendix:200"), key());
        Task cancelled = t;
        error(409, "ILLEGAL_STATE", () -> service.decideChange(cancelled.id, cancelled.changeRequests.get(1).requestId,
                new DecisionInput(cancelled.lockVersion, 1L, "APPROVED", "重复批准", "appendix:200"), key()));
    }

    @Test
    void protectedEvidenceDownloadExposesOriginalFilenameToBrowser() throws Exception {
        Task t = create("DOWNLOAD-NAME");
        var file = new MockMultipartFile("file", "proof.txt", "text/plain", "proof".getBytes());
        var receipt = evidence.upload(t.id, file, "依据", t.lockVersion, key());
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new AuditEvidenceController(evidence)).setControllerAdvice(new AuditErrorHandler())
                .build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/pmc/order-audit-tasks/" + t.id + "/evidence/" + receipt.evidenceRef() + "/download"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Access-Control-Expose-Headers", "Content-Disposition"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("X-Content-Type-Options", "nosniff"));
    }

    void evidenceLogin(int id, boolean upload, boolean audit, boolean team) {
        var codes = new ArrayList<SimpleGrantedAuthority>();
        codes.add(new SimpleGrantedAuthority("menu.evidence.read"));
        if (upload)
            codes.add(new SimpleGrantedAuthority("menu.evidence.create"));
        if (audit)
            codes.add(new SimpleGrantedAuthority("menu.order-audit-task"));
        if (team)
            codes.add(new SimpleGrantedAuthority("pmc.order-audit.scope.team"));
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("user" + id, null, codes));
    }

    MockMultipartFile proofFile() {
        return new MockMultipartFile("file", "proof.txt", "text/plain",
                "独立凭证".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void standaloneUploadNeedsNoOrderTaskOrOrderPermissions() throws Exception {
        evidenceLogin(1, true, false, false);
        var p = personal.upload(proofFile(), "规则制定依据", key());
        assertTrue(p.evidenceRef().startsWith("evidence:"));
        assertEquals(0, tasks.count());
        assertEquals(1, personal.list("规则", 1, 20).total());
        assertTrue(personal.downloadOwn(p.evidenceRef()).resource().exists());
    }

    @Test
    void evidenceReaderCannotUpload() {
        evidenceLogin(1, false, false, false);
        assertFalse(personal.metadata().canUpload());
        error(403, "FORBIDDEN", () -> {
            try {
                personal.upload(proofFile(), "凭证", key());
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        });
        assertEquals(0, personal.list("", 1, 20).total());
    }

    @Test
    void standaloneLibraryIsPrivateEvenForRoot() throws Exception {
        evidenceLogin(1, true, false, false);
        var p = personal.upload(proofFile(), "本人凭证", key());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user2", null,
                List.of(new SimpleGrantedAuthority("ROLE_ROOT"))));
        assertEquals(0, personal.list("", 1, 20).total());
        error(404, "NOT_FOUND", () -> {
            try {
                personal.downloadOwn(p.evidenceRef());
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void standaloneUploadsAreIdempotentAndRejectChangedContent() throws Exception {
        evidenceLogin(1, true, false, false);
        String key = key();
        var p = personal.upload(proofFile(), "依据", key);
        assertEquals(p, personal.upload(proofFile(), "依据", key));
        assertEquals(1, personalFiles.count());
        error(409, "IDEMPOTENCY_CONFLICT", () -> {
            try {
                personal.upload(proofFile(), "改变用途", key);
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void foreignPersonalProofCannotBeAttachedToUnrelatedTaskOrRule() throws Exception {
        evidenceLogin(1, true, false, false);
        var p = personal.upload(proofFile(), "本人规则", key());
        login(3, false);
        var d = draft("FOREIGN-PROOF");
        var m = new Material("m1", "客户订单", true, 1, "", null, null, Instant.now(), p.evidenceRef());
        error(403, "INVALID_EVIDENCE", () -> service
                .create(new Draft("FOREIGN-PROOF", 1, d.title(), "3", d.receivedAt(), true, List.of(m), null), key()));
        error(403, "INVALID_EVIDENCE", () -> service.saveRule("default",
                new Rule("default", 0L, "Asia/Shanghai", "资料齐备", "DELIVERY", List.of("客户订单"), "依据", p.evidenceRef()),
                key()));
    }

    @Test
    void ownerCanUseStandaloneProofAndAuthorizedTaskReviewerCanDownloadIt() throws Exception {
        evidenceLogin(1, true, false, false);
        var p = personal.upload(proofFile(), "原始订单凭证", key());
        approveRules("DELIVERY");
        login(1, false);
        var d = draft("PERSONAL-REFERENCE");
        var m = new Material("m1", "客户订单", true, 1, "", null, null, Instant.now(), p.evidenceRef());
        Task t = service.create(new Draft(d.orderNumber(), 1, d.title(), "1", d.receivedAt(), true, List.of(m), null),
                key());
        evidenceLogin(2, false, true, true);
        t = service.act(t.id, "accept", accept(t.lockVersion), key());
        assertNotNull(evidence.download(t.id, p.evidenceRef()).resource());
        assertEquals(0, personal.list("", 1, 20).total());
        login(3, true);
        Task accepted = t;
        error(404, "NOT_FOUND", () -> {
            try {
                evidence.download(accepted.id, p.evidenceRef());
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void standaloneProofCanBootstrapRuleApprovalWithoutCreatingTask() throws Exception {
        evidenceLogin(1, true, true, false);
        var p = personal.upload(proofFile(), "规则草稿依据", key());
        var rules = service.saveRule("default",
                new Rule("default", 0L, "Asia/Shanghai", "齐备后受理", "DELIVERY", List.of("客户订单"), "依据", p.evidenceRef()),
                key());
        evidenceLogin(2, true, true, false);
        var review = personal.upload(proofFile(), "规则批准依据", key());
        rules = service.approveRule("default", new RuleDecision(rules.version, "APPROVED", "批准", review.evidenceRef()),
                key());
        assertNotNull(rules.approved);
        assertEquals(0, tasks.count());
    }

    @Test
    void malformedStandaloneFileIsRejected() {
        evidenceLogin(1, true, false, false);
        var bad = new MockMultipartFile("file", "not.pdf", "application/pdf", "not pdf".getBytes());
        error(422, "INVALID_FILE", () -> {
            try {
                personal.upload(bad, "依据", key());
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        });
        assertEquals(0, personalFiles.count());
    }

    @Test
    void ruleReviewerCanDownloadLinkedPersonalProofButNotThePrivateLibrary() throws Exception {
        evidenceLogin(1, true, true, false);
        var p = personal.upload(proofFile(), "待批规则依据", key());
        service.saveRule("default",
                new Rule("default", 0L, "Asia/Shanghai", "齐备后受理", "DELIVERY", List.of("客户订单"), "依据", p.evidenceRef()),
                key());
        evidenceLogin(2, false, true, false);
        assertTrue(personal.downloadForRule(p.evidenceRef()).resource().exists());
        assertEquals(0, personal.list("", 1, 20).total());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user2", null,
                List.of(new SimpleGrantedAuthority("menu.order-audit-task.read"))));
        error(403, "FORBIDDEN", () -> {
            try {
                personal.downloadForRule(p.evidenceRef());
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void ruleDownloadCannotBeUsedForUnlinkedPrivateProof() throws Exception {
        evidenceLogin(1, true, true, false);
        var p = personal.upload(proofFile(), "尚未引用的本人文件", key());
        evidenceLogin(2, false, true, false);
        error(404, "NOT_FOUND", () -> {
            try {
                personal.downloadForRule(p.evidenceRef());
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void approverCanValidateNewPersonalProofInProposedMaterialCorrection() throws Exception {
        Task t = accepted();
        evidenceLogin(1, true, true, false);
        var p = personal.upload(proofFile(), "修正后的客户订单", key());
        var m = new Material("m1", "客户订单", true, 1, "", null, null, Instant.now(), p.evidenceRef());
        Draft proposed = new Draft(t.orderNumber, t.round, t.title, t.ownerUserId, t.receivedAt, true, List.of(m),
                null);
        t = service.requestChange(t.id,
                new ChangeInput(t.lockVersion, "MATERIAL_CORRECTION", "客户修订", "appendix:100", null, proposed, null),
                key());
        login(2, true);
        t = service.decideChange(t.id, t.changeRequests.get(0).requestId,
                new DecisionInput(t.lockVersion, 1L, "APPROVED", "同意修正", "appendix:200"), key());
        assertEquals(p.evidenceRef(), t.materials.get(0).evidenceRef());
        assertTrue(evidence.download(t.id, p.evidenceRef()).resource().exists());
    }

}
