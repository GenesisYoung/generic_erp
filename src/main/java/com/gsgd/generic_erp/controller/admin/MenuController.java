package com.gsgd.generic_erp.controller.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gsgd.generic_erp.dto.MenuDTO;
import com.gsgd.generic_erp.entity.auth.NavigationMenu;
import com.gsgd.generic_erp.service.admin.MenuService;
import com.gsgd.generic_erp.util.BasicPageResponse;
import com.gsgd.generic_erp.util.SimpleResponse;

/**
 * Navigation-menu management endpoints ({@code /api/manager/menu}):
 * paginated listing (all or valid-only), save/update, and batch deletion.
 */
@RestController
@RequestMapping("/api/manager/menu")
public class MenuController {
    private MenuService service;

    public MenuController(MenuService m) {
        this.service = m;
    }

    /** Returns a page of menu items matching the given filter. */
    @GetMapping("/fetch")
    public BasicPageResponse<NavigationMenu, MenuDTO> fetch(int page, int size, MenuDTO filter) {
        return service.fetchData(page, size, filter);
    }

    /** Same as {@link #fetch}, but restricted to menu items marked valid. */
    @GetMapping("/fetch/valid")
    public BasicPageResponse<NavigationMenu, MenuDTO> fetchValid(int page, int size, MenuDTO filter) {
        return service.fetchValidData(page, size, filter);
    }

    /** Creates or updates a menu item. */
    @PostMapping("/save")
    public SimpleResponse postMethodName(@RequestBody MenuDTO entity) {
        try {
            service.saveAndFlush(entity);
        } catch (Exception e) {
            new SimpleResponse(500, e.getMessage());
        }
        return new SimpleResponse(200, "Susccess");
    }

    /** Deletes the menu items whose ids are listed in the request body. */
    @PostMapping("/delete")
    public SimpleResponse getMethodName(@RequestBody SimpleBody request) {
        try {
            service.delete(request.deleteVal());
        } catch (Exception e) {
            return new SimpleResponse(500, e.getMessage());
        }
        return new SimpleResponse(200, null);
    }

}

/** Request body carrying the ids of menu items to delete. */
record SimpleBody(Long[] deleteVal) {
}