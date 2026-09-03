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