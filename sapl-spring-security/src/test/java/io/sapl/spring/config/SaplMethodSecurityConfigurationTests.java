/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.core.GrantedAuthorityDefaults;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintEnforcementService;

class SaplMethodSecurityConfigurationTests {

    @Test
    void whenRan_thenFilterBeansArePresent() {
        new ApplicationContextRunner().withUserConfiguration(OnlyBlockingSaplMethodSecurityConfiguration.class)
                .withBean(PolicyDecisionPoint.class, () -> mock(PolicyDecisionPoint.class))
                .withBean(ConstraintEnforcementService.class, () -> mock(ConstraintEnforcementService.class))
                .withBean(ObjectMapper.class, () -> mock(ObjectMapper.class)).run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("postEnforcePolicyEnforcementPoint");
                    assertThat(context).hasBean("preEnforcePolicyEnforcementPoint");
                });
    }

    @Test
    void whenRan_withDefaultProviderThenRegistrationHappens() {
        final var defaults = mock(GrantedAuthorityDefaults.class);
        new ApplicationContextRunner().withUserConfiguration(OnlyBlockingSaplMethodSecurityConfiguration.class)
                .withBean(GrantedAuthorityDefaults.class, () -> defaults)
                .withBean(PolicyDecisionPoint.class, () -> mock(PolicyDecisionPoint.class))
                .withBean(ConstraintEnforcementService.class, () -> mock(ConstraintEnforcementService.class))
                .withBean(ObjectMapper.class, () -> mock(ObjectMapper.class)).run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("postEnforcePolicyEnforcementPoint");
                    assertThat(context).hasBean("preEnforcePolicyEnforcementPoint");
                });
        verify(defaults, times(1)).getRolePrefix();
    }

    @Configuration
    @EnableSaplMethodSecurity
    public static class OnlyBlockingSaplMethodSecurityConfiguration {
    }

}
