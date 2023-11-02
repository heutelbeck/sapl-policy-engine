package io.sapl.spring.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.reactive.context.AnnotationConfigReactiveWebApplicationContext;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebApplicationContext;
import org.springframework.mock.web.MockServletContext;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.manager.ReactiveSaplAuthorizationManager;
import io.sapl.spring.manager.SaplAuthorizationManager;

class AuthorizationManagerConfigurationTests {

    @Test
    void testWebApplicationWithServletContext() {
        var ctx = new AnnotationConfigServletWebApplicationContext();
        ctx.registerBean(PolicyDecisionPoint.class, () -> mock(PolicyDecisionPoint.class));
        ctx.registerBean(ConstraintEnforcementService.class, () -> mock(ConstraintEnforcementService.class));
        ctx.registerBean(ObjectMapper.class, () -> mock(ObjectMapper.class));
        ctx.register(AuthorizationManagerConfiguration.class);
        ctx.setServletContext(new MockServletContext());
        ctx.refresh();
        assertThat(ctx.getBeansOfType(SaplAuthorizationManager.class)).hasSize(1);
        ctx.close();
    }

    @Test
    void testWebApplicationWithReactiveWebContext() {
        var ctx = new AnnotationConfigReactiveWebApplicationContext();
        ctx.registerBean(PolicyDecisionPoint.class, () -> mock(PolicyDecisionPoint.class));
        ctx.registerBean(ConstraintEnforcementService.class, () -> mock(ConstraintEnforcementService.class));
        ctx.registerBean(ObjectMapper.class, () -> mock(ObjectMapper.class));
        ctx.register(AuthorizationManagerConfiguration.class);
        ctx.refresh();
        assertThat(ctx.getBeansOfType(ReactiveSaplAuthorizationManager.class)).hasSize(1);
        ctx.close();
    }
}
