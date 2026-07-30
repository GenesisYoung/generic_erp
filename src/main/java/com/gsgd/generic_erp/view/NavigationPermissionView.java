package com.gsgd.generic_erp.view;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.View;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Immutable
@Table(name = "navigation_permission_view")
@View(query = """
        select m.id              as id,
               n.id              as nav_id,
               n.title_key       as nav_key,
               n.route           as route,
               u.id              as u_id,
               u.username        as u_name,
               p.id              as p_id,
               p.permission_name as p_name,
               p.val             as p_val
        from user_nav_menu m
                 left join navigation_menu_tb n on m.nav_id = n.id
                 left join user_tb u on m.user_id = u.id
                 left join permission_tb p on m.permission_id = p.id
        """)
@Getter
@NoArgsConstructor
public class NavigationPermissionView {

    /** Surrogate key inherited from the junction table. */
    @Id
    private Long id;

    /** Navigation menu id. */
    private Long navId;

    /** Navigation menu i18n title key. */
    private String navKey;

    /** Navigation menu route. */
    private String route;

    /** User id. */
    @Column(name = "u_id")
    private Long uId;

    /** Username. */
    @Column(name = "u_name")
    private String uName;

    /** Permission id. */
    @Column(name = "p_id")
    private Long pId;

    /** Permission name. */
    @Column(name = "p_name")
    private String pName;

    /** Permission bit value. */
    @Column(name = "p_val")
    private Long pVal;
}