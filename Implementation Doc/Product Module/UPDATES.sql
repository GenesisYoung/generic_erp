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


