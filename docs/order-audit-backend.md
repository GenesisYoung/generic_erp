> 完整流程更新：页面已补齐规则配置／批准、主管业务确认、变更申请／审批、重开／重评、快照、下载和导出。当前接入步骤与权限登记见 [完整流程说明](./order-audit-complete-workflow.md)，旧接口开关说明不再适用。

# 订单评审后端交付与接入说明

已实现与订单评审前端一致的正式接口。以下为代码实现状态，**不表示已迁移或部署到现有数据库**。

## 已实现范围

根路径 `/api/pmc/order-audit-tasks`，成功直接返回JSON对象；继续使用现有JWT登录与PermissionChecker。全部业务操作在服务层再次校验权限、数据范围、状态和版本。错误返回真实HTTP状态与 `{code,message,traceId?}`，不泄露SQL或异常内部信息。

| 方法／相对路径 | 请求与返回 | 状态 |
| --- | --- | --- |
| GET 根路径 | keyword、ownerUserId、status、dueFrom、dueTo、timing、page=1、size=20；返回items、total、sourceChecked、asOf | 已实现，全范围筛选后分页，20/50/100，逾期临期未结优先 |
| GET `/metadata` | 授权users、canCreate、timezone、acceptanceRule、completionRule | 已实现，规则未获批时三个规则相关字段为null |
| GET `/{id}` | 完整Task，含成果、核验、资料、审计、申请、allowedActions | 已实现，越权与不存在均404 |
| POST 根路径 | Draft，不允许lockVersion；返回201 Task | 已实现，手工来源首轮草稿，复合唯一约束 |
| PUT `/{id}` | Draft＋lockVersion | 已实现，仅草稿／待补，订单与轮次不可改 |
| POST `/{id}/actions/check-data` | lockVersion | 已实现，进入待补／待受理队列，不自动计时 |
| POST `/{id}/actions/accept` | lockVersion、dueAt、basis | 已实现，完整获批清单、凭证、责任人和期限检查，冻结受理规则与原期限 |
| POST `/{id}/actions/start` | lockVersion | 已实现，TODO→IN_PROGRESS |
| POST `/{id}/actions/block` | lockVersion、reason、nextAction、nextOwnerUserId、nextFeedbackAt | 已实现，阻塞不暂停计时 |
| POST `/{id}/actions/resume` | lockVersion、reason、evidenceRef | 已实现，前置解决凭证必填 |
| POST `/{id}/actions/submit` | lockVersion、conclusion、completedAt、evidenceRef | 已实现，生成新成果版本与快照，提交人／提交时刻由服务端记录 |
| POST `/{id}/actions/verify` | lockVersion、result、reason、evidenceRef | 已实现，禁止本人核验本人提交；PASS/FAIL→VERIFIED，NEED_PROOF→RETURNED，DISPUTED保持SUBMITTED |
| GET `/{id}/submissions/{version}` | Submission及资料、规则、业务确认、核验记录 | 已实现，历史首版保留 |
| POST `/{id}/business-confirmations` | lockVersion、submissionVersion、decision=APPROVED/RETURNED、reason、evidenceRef | 已实现，独立主管确认；与绩效核验分开，已通过的本版不能再次覆盖完成时刻 |
| POST `/{id}/change-requests` | ChangeInput | 已实现，DEADLINE/REOPEN/CANCEL/MATERIAL_CORRECTION/DECLINE/REVIEW_AGAIN；只登记待批申请 |
| GET `/{id}/change-requests` | page=1、size=20；items、total | 已实现 |
| POST `/{id}/change-requests/{requestId}/decision` | lockVersion、requestVersion、decision=APPROVED/REJECTED、reason、evidenceRef | 已实现，禁止自批；返回更新后的父任务Task，新轮次编号可查history与列表 |
| GET `/rules` | RuleState，含draft、approved、version、history、approvedBy、approvedAt | 已实现，未获批保持空 |
| PUT `/rules/default/draft` | Rule，见下文 | 已实现，独立配置权限、规则版本锁 |
| POST `/rules/default/approval` | ruleVersion、decision=APPROVED/REJECTED、reason、evidenceRef | 已实现，禁止规则提交人自批，批准后只影响后续受理 |
| POST `/{id}/evidence` | multipart file、purpose、lockVersion | 已实现，20MB上限，PDF/PNG/JPEG/UTF-8 TXT，校验内容类型，事务失败清理文件 |
| GET `/{id}/evidence/{ref}/download` | 文件流；ref可用UUID或audit:UUID | 已实现，与任务同权限，固定attachment和nosniff，不返回磁盘路径 |
| GET `/export` | 同列表筛选、format=csv；返回UTF-8 CSV | 已实现，最多10000条，CSV公式转义，额外export权限 |
| GET `/assignees` | keyword、page、size；items、total | 已实现，授权范围人员，metadata同来源 |
| GET `/source-orders` | 501 SOURCE_NOT_CONNECTED | 明确未接老ERP，不编造客户订单信息 |

所有JSON写接口及凭证上传都必须提供 `Idempotency-Key`，16—100位字母／数字／短横线，建议UUID。键按登录用户隔离。同键同请求回放原响应；同键换请求返回409。已失去任务权限或操作权限时不回放历史数据。上传的键在独立文件命名空间生效。

## 字段与重要校验

核心Draft、Material、Task、ActionInput字段继续与前端 `src/features/order-audit/model.ts` 对齐。后端在核心字段外还返回 `createdBy`、`departmentId`、`acceptanceRuleSnapshot`、`changeRequests`，以及history的actorId／before／after。编辑请求须使用白名单字段，不能回传整份Task；前端已按白名单提交。

JSON严格拒绝未知字段；每个动作只接受其允许的非空字段。服务端时间、状态、创建人、受理人、核验人均不能由客户端伪造。资料≤100项，名称≤200字、ID≤100字且不重复，订单号≤50字，交付要求≤1000字，缺项及审批说明≤4000字，成果结论≤10000字。

资料未齐必须填写缺项、补交人、预计时间；已交须有实际时间与受控凭证。接收／实际补齐／实际完成不能填未来时间，提交完成不能早于正式受理。首版禁止追溯受理，截止期限须在未来；追溯政策尚待业务确认。nextFeedbackAt须为未来时间。资料清单的必备项须逐项匹配**批准规则**，员工不能删除或降级必备标志绕过受理。

时间按带时区的ISO-8601传输，服务端使用Instant；数据库存UTC。业务展示时区由规则批准，未默认启用Asia/Shanghai。

Submission新增：

- `reportedCompletedAt`：员工提交时填报的完成时间，保持原始事实。
- `completedAt`：批准完成节点的完成时间；DELIVERY时取填报时间，BUSINESS_APPROVAL时初始为null，独立业务确认通过时取服务端确认时间。
- `materialsSnapshot`、`ruleSnapshot`、`verificationRecords[]`、`businessConfirmations[]`：保留资料、规则和历次核验／确认。原结论、提交人、提交时刻和填报时间不会被后续成果覆盖。

ChangeInput：`lockVersion,type,reason,evidenceRef,proposedDueAt?,proposedChanges?:Draft,affectedSubmissionVersion?`。DEADLINE必须指定新期限且只适用于生效未核验任务；MATERIAL_CORRECTION必须提供完整proposedChanges，订单、轮次、原接收时间不可改；REOPEN只适用于VERIFIED；DECLINE只适用于未受理；REVIEW_AGAIN只适用于VERIFIED，独立批准后创建下一轮DRAFT、清空资料清单与受理事实。CANCEL保留全部历史。批准时重新校验状态和当前版本；不会因请求曾合法就略过当前检查。改期保留originalDueAt；撤销、阻塞及改期均不自动改绩效分母。

原表 `order_assessment_task`、`order_assess_file_status` 不做自动写入或数据搬迁。新模块使用事务内的任务聚合文档保存全部业务快照，索引列用于数据库筛选、范围隔离和排序；**没有宣称已实现跨PMC模块的统一pmc_task表**。旧表字段缺失、时区、人员认领须业务核对后另做迁移。当前订单来源仍是手工录入，sourceChecked始终false，不因列表有数据而变成已核对。

## 权限及数据范围

操作前缀 `menu.order-audit-task.`：read、create、update、accept、execute、submit、verify、confirm、change.request、approve、export、rules.configure、rules.approve。每项操作同时需要read。父权限按现有层级规则包含子权限；数据库脚本只登记代码，不给任何角色或账号自动授权。

范围权限独立为：

- 无额外范围：本人创建、本人负责或明确分派下一动作的任务。
- `pmc.order-audit.scope.team`：加上任务归属部门与本人部门相交的任务，可担任主管动作的范围条件。
- `pmc.order-audit.scope.all`：全范围，需单独授权。

ROOT沿用现有全权限规则，但仍不能本人核验、自批规则或自批变更。核验比较本版submittedBy，不只比较任务owner。主管角色需同时有具体动作和team/all范围；普通员工即使拥有accept代码也不能直接受理自己的任务。没有明确部门的草稿保持部门空，不猜测归属。

默认人员下拉只返回本人、获授权同部门或全范围有效账号。SQL按数据库用户与部门关系确定范围，不信任客户端departmentId或请求头username。任务创建时记录部门归属；后续人员组织变化不会静默改写历史部门，归属调整需经业务确认与批准处理。

## 凭证及文件路径

凭证字段支持两种格式：

- `audit:UUID`：本模块上传返回的任务专属凭证，不允许跨任务引用。新建时应先存不完整草稿，再在该任务下上传、补资料。
- `appendix:数字ID`：现有appendix_tb中的active附件；只可引用本人拥有的文件，或已经合法关联到本任务资料／成果／核验／确认／申请的文件。未完成上传、无权限、无记录均拒绝。

首版不能把随意路径、网址或文字证明当作已上传文件。现有分片上传merge流程没有完整落下active/path状态，本模块不依赖该不完整流程，不代其标记上传完成。现有附件能否迁移复用需检查实际记录。

模块专属路径配置：`pmc.order-audit.evidence-path`，默认 `${spring.global.file.home_path}/order-audit`。服务端生成不可变存储名，不接受客户端目录；只创建任务专属文件，下载校验目录和符号链接边界。需给应用账号该专属目录的读写权限。已有上传只做格式与签名检查，尚未接企业恶意文件扫描服务；下载强制附件。

## 部署与正式接入顺序

1. 在测试环境备份后评审执行 `src/main/resources/db/order_audit_v1.sql`。新增4表、索引、外键和权限代码，不覆盖旧表；脚本使用MySQL 8语法。本次仅在H2做集成验证，未连接实际MySQL。`IF NOT EXISTS`不用于修复同名但结构不同的表。
2. 配置数据库会话及JDBC为UTC，`spring.jpa.properties.hibernate.jdbc.time_zone=UTC`。生产建议使用 `spring.jpa.hibernate.ddl-auto=validate`，不要依赖项目原有update模式完成正式迁移。
3. 使用现有权限管理分配员工／独立主管／独立规则审批人所需代码与部门范围。侧边栏沿用现有菜单绑定，路由为 `/pmc/order-audit-task`，语言键orderAuditTaskList；脚本不自动扩大菜单或用户权限。
4. 重启后端；确认 `/api/pmc/order-audit-tasks/metadata` 能通过现有JWT读取。新库没有规则时能保存草稿，但不能受理。
5. 配置人提交default规则草稿，另一名有权限人员批准。可用本人已有的有效appendix凭证；也可先建立资料筹备草稿并上传本人凭证，再用于规则审批。
6. 前端本地环境设置 `VITE_ORDER_AUDIT_API_ENABLED=true` 并重启；API前缀仍由VITE_BASE_URL提供。代码不会自动启用未经迁移的真实服务。
7. 用真实独立账号执行下述联调。正式绩效仍需完整周期和获批规则，不以接口上线代替业务确认。

规则草稿示例（仅说明格式，内容不是预先批准的生产规则）：

```json
{
  "ruleId":"default", "ruleVersion":0,
  "timezone":"Asia/Shanghai",
  "acceptanceRule":"示例：必备资料完整且凭证有效后由授权主管受理",
  "completionNode":"DELIVERY",
  "requiredMaterials":["客户订单","包装规格"],
  "reason":"业务批准依据说明",
  "evidenceRef":"appendix:实际有效附件ID"
}
```

ruleVersion来自GET /rules的version。提交后读取新version，另一名人员调用approval：`{ruleVersion,decision:"APPROVED",reason,evidenceRef}`。`completionNode`只接受DELIVERY（合格成果交付）或BUSINESS_APPROVAL（独立主管业务确认通过），已受理任务继续使用受理时的规则快照。

## 验证与尚待确认

隔离H2真实仓储／事务集成测试、并发写测试及MVC请求响应测试已覆盖：只读禁止写入、跨部门隔离、人员与附件越界、未知字段／本地无时区时间拒绝、未获批规则禁止受理、必备清单不可绕过、自批／自核验、原期限保留、阻塞不暂停、退回保留首版、业务确认节点、改期审批、重开与新轮次、CSV防公式注入、上传签名和幂等、同版本并发提交最多成功一次。

待业务批准：实际必备资料、时区、完成节点、时限与工作日历、追溯政策、重评纳入月评规则、订单大小写／编码规范、人员归属和历史数据迁移。待外部接入：老ERP原单查询、正式绩效计算、企业文件扫描。首版CSV而非XLSX、default规则目录而非按订单类型动态规则；这些限制均未伪装为已实现。

模块现有列表与详情使用同形Task，包含完整历史；高数据量时需拆分列表摘要与历史分页，并同步前端契约。幂等响应目前持久保留，不自动清理；清理前需制定重试时效与归档规则。首版没有自动计算或更改任何采购、库存或绩效数量。
