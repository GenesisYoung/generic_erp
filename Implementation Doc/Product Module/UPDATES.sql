alter table product_variant_tb
    change length_mm length decimal(12, 3) null;

alter table product_variant_tb
    change width_mm width decimal(12, 3) null;

alter table product_variant_tb
    change height_mm height decimal(12, 3) null;

alter table product_variant_tb
    add size_uom_id bigint null after height;

alter table product_variant_tb
    drop foreign key fk_variant_weight_uom;

ALTER TABLE product_variant_tb ADD INDEX idx_variant_size_uom (size_uom_id);

ALTER TABLE product_variant_tb
ADD CONSTRAINT ck_variant_size_uom
CHECK (size_uom_id IS NOT NULL
       OR (length IS NULL AND width IS NULL AND height IS NULL));

rename table product_audit_log_tb to audit_log_tb;

alter table audit_log_tb
    add module varchar(50) null after change_group_id;

create index audit_log_tb_module_index
    on audit_log_tb (module);

alter table audit_log_tb
    add operation_sql varchar(3000) not null after operation;

alter table audit_log_tb
    add client_ip varchar(45) null after edited_by;

drop table audit_log_tb;

CREATE TABLE IF NOT EXISTS audit_log_tb (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    module        VARCHAR(50)  NOT NULL,
    action        VARCHAR(20)  NOT NULL,
    target_id     VARCHAR(64)      NULL,
    description   VARCHAR(255)     NULL,
    method_name   VARCHAR(255)     NULL,
    operator_id   BIGINT           NULL,
    operator_name VARCHAR(50)      NULL,
    client_ip     VARCHAR(45)      NULL,
    operate_time  DATETIME     NOT NULL,
    create_date   DATE         NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id),
    KEY idx_audit_module_target (module, target_id),
    KEY idx_audit_operate_time (operate_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

alter table product_tb
    add is_enabled bool default true null after tax_category;

alter table appendix_tb
    add constraint appendix_tb_pk
        unique (storage_key);

create table file_chunk_tb
(
    id            bigint auto_increment
        primary key,
    appendix_id   bigint                 not null,
    uuid          varchar(36)            not null,
    standard_size int                    null comment 'Standard chuck size',
    chuck_idx     int                    null,
    finished      bool                   null,
    create_time   datetime default NOW() null,
    update_time   datetime               null,
    operator_id   bigint                 null,
    constraint file_chunk_tb_appendix_tb_appendix_id_fk
        foreign key (appendix_id) references appendix_tb (appendix_id),
    constraint file_chunk_tb_user_tb_id_fk
        foreign key (operator_id) references user_tb (id)
);

create index file_chunk_tb_uuid_index
    on file_chunk_tb (uuid);

create table upload_session
(
    id          bigint auto_increment
        primary key,
    file_uuid   varchar(36)                                                  not null,
    total_size  double                                                       not null,
    current_idx int                                      default 0           not null,
    chuck_size  double                                                       not null,
    status      enum ('FINISHED', 'UPLOADING', 'PAUSED') default 'UPLOADING' null
);

create index upload_session_file_uuid_index
    on upload_session (file_uuid);

alter table upload_session
    modify total_size bigint not null;

alter table upload_session
    modify chuck_size bigint not null;

alter table upload_session
    modify status enum ('FINISHED', 'UPLOADING', 'MERGING') default 'UPLOADING' null;

alter table upload_session
    add appendix_id bigint null after file_uuid;

alter table upload_session
    add constraint upload_session_appendix_tb_appendix_id_fk
        foreign key (appendix_id) references appendix_tb (appendix_id);

alter table file_chunk_tb
    add constraint file_chunk_tb_pk
        unique (chuck_idx, uuid);
