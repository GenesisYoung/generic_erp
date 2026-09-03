package com.gsgd.generic_erp.view.sql;

import java.math.BigDecimal;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.View;

import com.gsgd.generic_erp.entity.product.ProductStatus;
import com.gsgd.generic_erp.entity.product.ProductType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Read-only mapping of the database view that flattens
 * product_tb LEFT JOIN product_variant_tb LEFT JOIN product_category_rel_tb.
 *
 * <p>
 * Because both joins are LEFT joins, a product with no active variant and no
 * active category still produces one row, with every variant/category column
 * NULL.
 * That is why all numeric fields are wrapper types (Long / BigDecimal /
 * Boolean)
 * and never primitives: a primitive cannot hold NULL.
 * </p>
 *
 * <p>
 * The view is not updatable, so this entity is annotated {@link Immutable} and
 * exposes getters only. Hibernate will never issue INSERT/UPDATE/DELETE for it
 * and
 * will skip it during dirty checking.
 * </p>
 */
@Entity
@Immutable
@Table(name = "product_variant_category_view")
@View(query = """
                          select concat(`p`.`product_id`, ':', coalesce(`pvt`.`variant_id`, 0), ':',
                            coalesce(`pcrt`.`cate_id`, 0)) AS `row_id`,
                     `p`.`product_id`                      AS `product_id`,
                     `p`.`product_name`                    AS `product_name`,
                     `p`.`product_code`                    AS `product_code`,
                     `p`.`product_description`             AS `product_description`,
                     `p`.`type`                            AS `type`,
                     `p`.`configurable`                    AS `configurable`,
                     `p`.`status`                          AS `status`,
                     `p`.`tax_category`                    AS `tax_category`,
                     `pvt`.`variant_id`                    AS `variant_id`,
                     `pvt`.`sku`                           AS `sku`,
                     `pvt`.`variant_name`                  AS `variant_name`,
                     `pvt`.`is_default`                    AS `is_default`,
                     `pvt`.`description`                   AS `variant_description`,
                     `p`.`standard_cost`                 AS `standard_cost`,
                     `p`.`list_price`                    AS `list_price`,
                     `pvt`.`currency`                      AS `currency`,
                     `pvt`.`net_weight`                    AS `net_weight`,
                     `pvt`.`length`                        AS `length`,
                     `pvt`.`width`                         AS `width`,
                     `pvt`.`height`                        AS `height`,
                     `pvt`.`weight_uom_id`                 AS `weight_uom_id`,
                     `pvt`.`size_uom_id`                   AS `size_uom_id`,
                     `pcrt`.`cate_id`                      AS `cate_id`
              from ((`generic_erp`.`product_tb` `p` left join `generic_erp`.`product_variant_tb` `pvt`
                     on (((`pvt`.`product_id` = `p`.`product_id`) and (`pvt`.`active` = 1)))) left join `generic_erp`.`product_category_rel_tb` `pcrt`
                    on (((`pcrt`.`product_id` = `p`.`product_id`) and (`pcrt`.`active` = 1))))
                      """)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProductVariantCategoryView {

       private static final long serialVersionUID = 1L;

       /**
        * Synthetic primary key: concat(product_id, ':', coalesce(variant_id, 0), ':',
        * coalesce(cate_id, 0)).
        * JPA requires an @Id, and no single natural column is unique in this view,
        * so the view builds one for us.
        */
       @Id
       @Column(name = "row_id", length = 64)
       private String rowId;

       // ---------- product_tb ----------

       @Column(name = "product_id")
       private Long productId;

       @Column(name = "product_name")
       private String productName;

       @Column(name = "product_code")
       private String productCode;

       @Column(name = "product_description")
       private String productDescription;

       @Enumerated(EnumType.STRING)
       @Column(name = "type")
       private ProductType type;

       @Column(name = "configurable")
       private Boolean configurable;

       @Enumerated(EnumType.STRING)
       @Column(name = "status")
       private ProductStatus status;

       @Column(name = "tax_category")
       private String taxCategory;

       // ---------- product_variant_tb (nullable: LEFT JOIN) ----------

       @Column(name = "variant_id")
       private Long variantId;

       @Column(name = "sku")
       private String sku;

       @Column(name = "variant_name")
       private String variantName;

       /** Maps pvt.is_default, aliased as defaultVariant in the view. */
       @Column(name = "is_default")
       private Boolean isDefault;

       @Column(name = "variant_description")
       private String variantDescription;

       @Column(name = "standard_cost")
       private BigDecimal standardCost;

       @Column(name = "list_price")
       private BigDecimal listPrice;

       @Column(name = "currency", length = 3)
       private String currency;

       @Column(name = "net_weight")
       private BigDecimal netWeight;

       @Column(name = "length")
       private BigDecimal length;

       @Column(name = "width")
       private BigDecimal width;

       @Column(name = "height")
       private BigDecimal height;

       @Column(name = "weight_uom_id")
       private Long weightUomId;

       @Column(name = "size_uom_id")
       private Long sizeUomId;

       // ---------- product_category_rel_tb (nullable: LEFT JOIN) ----------

       @Column(name = "cate_id")
       private Long cateId;

}
