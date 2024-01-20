/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
