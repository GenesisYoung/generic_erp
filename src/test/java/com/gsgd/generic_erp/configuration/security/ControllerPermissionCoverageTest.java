package com.gsgd.generic_erp.configuration.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Deny-by-default guard: URL rules only require a login, so a handler without
 * {@code @PreAuthorize} would be open to every authenticated user. This fails
 * the build for any such handler. Mark deliberately open endpoints with
 * {@code @PreAuthorize("isAuthenticated()")} or {@code permitAll()}.
 */
class ControllerPermissionCoverageTest {

    @Test
    void everyHandlerDeclaresAnAuthorizationRule() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class)); // also matches @RestController

        List<String> unprotected = new ArrayList<>();
        int handlers = 0;
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.gsgd.generic_erp")) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            boolean classRule = AnnotatedElementUtils.hasAnnotation(type, PreAuthorize.class);
            for (Method method : type.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers())
                        || !AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) {
                    continue;
                }
                handlers++;
                if (!classRule && !AnnotatedElementUtils.hasAnnotation(method, PreAuthorize.class)) {
                    unprotected.add(type.getSimpleName() + "#" + method.getName());
                }
            }
        }

        assertTrue(handlers > 0, "scanner found no handlers - check the base package");
        assertFalse(!unprotected.isEmpty(), "Handlers without @PreAuthorize: " + unprotected);
    }
}
