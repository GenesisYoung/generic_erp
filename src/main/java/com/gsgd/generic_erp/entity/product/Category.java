package com.gsgd.generic_erp.entity.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Product category entity mapped to {@code category_tb}.
 *
 * <p>Categories form an arbitrarily deep tree through {@code parentId}. When a
 * node is moved, the service layer must update the cached path and depth of the
 * node and all its descendants, and must reject moves beneath a descendant.</p>
 */
@Entity
@Table(name = "category_tb",
        uniqueConstraints = @UniqueConstraint(name = "uk_category_parent_name", columnNames = {"parent_id", "cate_name"}),
        indexes = {
                @Index(name = "idx_category_name", columnList = "cate_name"),
                @Index(name = "idx_category_abbr", columnList = "cate_abbr"),
                @Index(name = "idx_category_parent", columnList = "parent_id"),
                @Index(name = "idx_category_path", columnList = "path_cache")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Category extends ProductAuditableEntity {

    /** Category primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cate_id")
    private Long cateId;

    /** Category name; unique within the same parent category. */
    @Column(name = "cate_name", length = 50, nullable = false)
    private String cateName;

    /** Abbreviated category name for space-constrained interfaces. */
    @Column(name = "cate_abbr", length = 8)
    private String cateAbbr;

    /** Category description. */
    @Column(name = "description", length = 2000)
    private String description;

    /** Parent category identifier; null denotes a root category. */
    @Column(name = "parent_id")
    private Long parentId;

    /** Materialized ancestor path, for example {@code /1/7/23/}. */
    @Column(name = "path_cache", length = 512)
    private String pathCache;

    /** Distance from the root; zero for a root category. */
    @Column(name = "depth", nullable = false)
    @lombok.Builder.Default
    private Integer depth = 0;

    /** Whether the category is active; false represents a soft deletion. */
    @Column(name = "active", nullable = false)
    @lombok.Builder.Default
    private Boolean active = true;
}
