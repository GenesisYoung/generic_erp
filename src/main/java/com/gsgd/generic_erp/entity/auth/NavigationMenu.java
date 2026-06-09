package com.gsgd.generic_erp.entity.auth;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Represents a navigation menu item displayed in the frontend sidebar.
 * Maps to table: navigation_menu_tb
 */
@Entity
@Table(name = "navigation_menu_tb")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class NavigationMenu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** i18n key used to resolve the display title on the frontend */
    @Column(name = "title_key", length = 50, nullable = false)
    private String titleKey;

    /** MDI icon name (e.g. "mdi-home") */
    @Column(name = "icon", length = 50, nullable = false)
    private String icon;

    /** Vue Router route path (e.g. "/dashboard") */
    @Column(name = "route", length = 50, nullable = false)
    private String route;

    /** Vuetify color name applied to the menu item (e.g. "indigo") */
    @Column(name = "color", length = 20, nullable = false)
    private String color;

    /** Whether this menu item is visible/active in the frontend */
    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled;

    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;
}