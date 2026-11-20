create table order_assessment_task
(
    id                     int auto_increment comment '数据编号'
        primary key,
    order_number           varchar(50)           not null comment '订单号',
    file_arrive_date       datetime              null comment '资料接收时间',
    file_completion_status boolean default false not null comment '资料完整度状态',
    file_completion_memo   longtext              null comment '文件完整度备注',
    assessment_conclusion  longtext              null comment '评审结论及依据',
    submission_date        datetime              null comment '评审成果提交时间',
    approval_credential    varchar(50)           null comment '批准凭证'
)
    comment '订单评审任务';

alter table order_assessment_task
    add constraint order_assessment_task_pk
        unique (order_number);

alter table order_assessment_task
    add assessment_deadline datetime null comment '评审截止日' after submission_date;

create table order_assess_file_status
(
    id                       int auto_increment comment '数据编号'
        primary key,
    order_number             varchar(50)   not null comment '业务编号（订单编号）',
    submission_status        int default 3 not null comment '所需资料提交状态（1-已提交；2-部分提交;3-尚未提交）',
    incomplete_parts         longtext      null comment '缺省部分',
    unfinish_reason          longtext      null comment '未完成原因',
    expected_submission_date datetime      null comment '预期提交时间',
    actual_submission_date   datetime      null comment '实际提交日期',
    constraint order_assess_file_status_order_assessment_task_order_number_fk
        foreign key (order_number) references generic_erp.order_assessment_task (order_number)
);

alter table order_assessment_task
    change approval_credential approval_proof varchar(50) null comment '批准凭证';

create table material_requirement_accounting_tb
(
    id                 int auto_increment comment '任务编号'
        primary key,
    requirement_number varchar(50) not null comment '需求编号',
    bom_id             int         not null comment 'BOM表id',
    bom_version        int         not null comment 'BOM版本',
    constraint material_requirement_accounting_tb_pk_2
        unique (requirement_number)
)
    comment '物料需求核算任务';

create table material_assessment_history
(
    id                     int auto_increment comment '记录编号'
        primary key,
    requirement_id         varchar(50)       not null,
    is_first_accounting    int default false not null comment '是否为第一次核算',
    accounting_conclusion  longtext          null comment '核算结论',
    accounting_reason      longtext          null comment '核算原因',
    submission_date        datetime          null comment '提交日期',
    accounting_differences longtext          null comment '核算差异',
    difference_reason      longtext          null comment '差异原因',
    reaccounting_proof     varchar(50)       null comment '复核凭证',
    constraint requirement_key
        foreign key (requirement_id) references material_requirement_accounting_tb (requirement_number)
)
    comment '物料需求核算历史记录表';

create table online_material_availability
(
    id                         int auto_increment comment '编号'
        primary key,
    task_id                    varchar(50) not null comment '工单号',
    online_date                datetime    not null comment '上线日期',
    availability_status        boolean     null comment '是否齐备',
    quality_approval_status    longtext    null comment '品质放行情况及说明',
    quality_approval_proof     int         null comment '品质通行凭证',
    shortage_code              varchar(20) null comment '缺料编码',
    shortage_content           longtext    null comment '缺料内容',
    influence                  longtext    null comment '缺料影响',
    material_arrival_guarantee longtext    null comment '到货承诺／跟进人／下一反馈时间'
)
    comment '上线物料可用记录';

alter table online_material_availability
    add constraint online_material_availability_pk
        unique (task_id);

create table pmc_place_order_record
(
    id                       int auto_increment comment '记录编号'
        primary key,
    task_id                  varchar(20)         not null comment '任务编号',
    order_id                 varchar(20) not null comment '订单号',
    first_filling_snapshot   varchar(50) null comment '首次录入快照（凭证文件）',
    submission_date          datetime    null comment '首次提交时间',
    origin_requirement_proof varchar(50) null comment '客户原始需求凭证',
    constraint pmc_place_order_record_pk
        unique (task_id)
)
    comment '计划部门ERP下单记录';

create table pmc_order_change_history
(
    id                    int auto_increment comment '数据编号'
        primary key,
    task_id               varchar(20)          not null comment '任务编号',
    is_client_required    tinyint(1) default 0 not null comment '是否为客户原因变更',
    change_date           datetime             not null comment '变更日期',
    change_reason         int                  not null comment '变更原因',
    new_requirement_proof varchar(50)          null comment '客户新需求证明凭证',
    constraint pmc_order_change_history_pmc_place_order_record_task_id_fk
        foreign key (task_id) references generic_erp.pmc_place_order_record (task_id)
)
    comment 'pmc订单变更历史';

-- MySQL: independent personal evidence library and a standalone sidebar menu.
-- Existing task evidence and employee authorizations are not modified.
CREATE TABLE IF NOT EXISTS pmc_personal_evidence (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 created_by VARCHAR(20) NOT NULL,
 file_name VARCHAR(128) NOT NULL,
 storage_key VARCHAR(64) NOT NULL,
 request_hash VARCHAR(64) NOT NULL,
 byte_size BIGINT NOT NULL,
 purpose VARCHAR(1000) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 INDEX ix_personal_evidence_owner(created_by,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

INSERT INTO permission_tb(permission_code,is_enabled,create_date)
SELECT 'menu.evidence.read',1,CURDATE() WHERE NOT EXISTS(SELECT 1 FROM permission_tb WHERE permission_code='menu.evidence.read');
INSERT INTO permission_tb(permission_code,is_enabled,create_date)
SELECT 'menu.evidence.create',1,CURDATE() WHERE NOT EXISTS(SELECT 1 FROM permission_tb WHERE permission_code='menu.evidence.create');

INSERT INTO navigation_menu_tb(parent_id,title_key,icon,route,color,permission_id,is_enabled,create_time)
SELECT NULL,'evidenceManagement','mdi-file-document-outline','/pmc/evidence','indigo',p.id,1,NOW()
FROM permission_tb p WHERE p.permission_code='menu.evidence.read' AND p.is_enabled=1
AND NOT EXISTS(SELECT 1 FROM navigation_menu_tb WHERE route='/pmc/evidence');

INSERT INTO menu_registered_permissions_record(menu_id,permission_id,permission_enabled,create_date)
SELECT n.id,p.id,1,NOW() FROM navigation_menu_tb n JOIN permission_tb p ON p.permission_code IN ('menu.evidence.read','menu.evidence.create')
WHERE n.route='/pmc/evidence' AND n.is_enabled=1 AND p.is_enabled=1
AND NOT EXISTS(SELECT 1 FROM menu_registered_permissions_record r WHERE r.menu_id=n.id AND r.permission_id=p.id);

-- Existing ROOT users already possess all capabilities. Add only sidebar visibility.
-- No employee receives new menu or upload permissions automatically.
INSERT INTO user_nav_menu(nav_id,permission_id,user_id,is_enabled,create_date)
SELECT DISTINCT n.id,NULL,u.id,1,NOW()
FROM navigation_menu_tb n JOIN user_roles_tb ur ON ur.user_enabled=1
JOIN role_tb r ON r.id=ur.role_id AND r.role_name='ROOT'
JOIN user_tb u ON u.id=ur.user_id AND u.is_enabled=1 AND u.status=1
WHERE n.route='/pmc/evidence' AND n.is_enabled=1
AND NOT EXISTS(SELECT 1 FROM user_nav_menu g WHERE g.nav_id=n.id AND g.user_id=u.id);


