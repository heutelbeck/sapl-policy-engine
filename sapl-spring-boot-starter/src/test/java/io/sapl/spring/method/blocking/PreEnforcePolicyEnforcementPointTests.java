/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.spring.method.blocking;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.config.EnableSaplMethodSecurity;
import io.sapl.spring.constraints.BlockingConstraintHandlerBundle;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.constraints.FunctionUtil;
import io.sapl.spring.method.blocking.PreEnforcePolicyEnforcementPointTests.Application;
import io.sapl.spring.method.blocking.PreEnforcePolicyEnforcementPointTests.MethodSecurityConfiguration;
import io.sapl.spring.method.blocking.PreEnforcePolicyEnforcementPointTests.TestService;
import io.sapl.spring.method.metadata.PreEnforce;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.stereotype.Service;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Flux;

import java.util.Optional;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = { Application.class, MethodSecurityConfiguration.class, TestService.class }, properties = {
        "spring.main.web-application-type=servlet", "io.sapl.pdp.embedded.enabled=false" })
class PreEnforcePolicyEnforcementPointTests {

    private static final String ORIGINAL_RETURN_OBJECT = "original return object";
    private static final String CHANGED_RETURN_OBJECT  = "changed return object";

    @MockitoBean
    private PolicyDecisionPoint pdp;

    @MockitoBean
    private ConstraintEnforcementService constraintEnforcementService;

    @Autowired
    TestService testService;

    @SpringBootApplication(exclude = org.springframework.boot.r2dbc.autoconfigure.R2dbcAutoConfiguration.class)
    static class Application {
        public static void main(String... args) {
            SpringApplication.run(Application.class, args);
        }
    }

    @TestConfiguration
    @EnableSaplMethodSecurity
    static class MethodSecurityConfiguration {
    }

    @Service
    static class TestService {
        @PreEnforce
        public String doSomething() {
            return ORIGINAL_RETURN_OBJECT;
        }

        @PreEnforce
        public Optional<String> doSomethingOptional() {
            return Optional.of("I did something!");
        }

        @PreEnforce
        public Optional<String> doSomethingOptionalEmpty() {
            return Optional.empty();
        }
    }

    @Test
    @WithMockUser()
    void whenBeforeAndDecideDeny_thenReturnFalse() {
        when(constraintEnforcementService.blockingPreEnforceBundleFor(any(), any()))
                .thenReturn(BlockingConstraintHandlerBundle.BLOCKING_NOOP);
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));
        assertThatThrownBy(() -> testService.doSomething()).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser()
    void whenBeforeAndDecideNull_thenReturnFalse() {
        when(constraintEnforcementService.blockingPreEnforceBundleFor(any(), any()))
                .thenReturn(BlockingConstraintHandlerBundle.BLOCKING_NOOP);
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(null);
        assertThatThrownBy(() -> testService.doSomething()).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser()
    void whenBeforeAndDecidePermitButBundleNull_thenReturnFalse() {
        when(constraintEnforcementService.blockingPreEnforceBundleFor(any(), any())).thenReturn(null);
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
        assertThatThrownBy(() -> testService.doSomething()).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser()
    void whenBeforeAndDecidePermit_thenReturnTrue() {
        when(constraintEnforcementService.blockingPreEnforceBundleFor(any(), any()))
                .thenReturn(BlockingConstraintHandlerBundle.BLOCKING_NOOP);
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
        assertThat(testService.doSomething()).isEqualTo(ORIGINAL_RETURN_OBJECT);
    }

    @Test
    @WithMockUser()
    void when_BeforeAndDecidePermit_and_obligationsFail_then_ReturnFalse() {
        final var mockBundle = BlockingConstraintHandlerBundle.preEnforceConstraintHandlerBundle(() -> {
            throw new AccessDeniedException("INTENDED FAILURE IN TEST");
        }, FunctionUtil.sink(), UnaryOperator.identity(), FunctionUtil.sink(), UnaryOperator.identity(),
                FunctionUtil.all(), FunctionUtil.sink(), UnaryOperator.identity());
        when(constraintEnforcementService.blockingPreEnforceBundleFor(any(), any())).thenReturn(mockBundle);
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
        assertThatThrownBy(() -> testService.doSomething()).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser()
    void whenBeforeAndDecideNotApplicable_thenReturnFalse() {
        when(constraintEnforcementService.blockingPreEnforceBundleFor(any(), any()))
                .thenReturn(BlockingConstraintHandlerBundle.BLOCKING_NOOP);
        when(pdp.decide(any(AuthorizationSubscription.class)))
                .thenReturn(Flux.just(AuthorizationDecision.NOT_APPLICABLE));
        assertThatThrownBy(() -> testService.doSomething()).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser()
    void whenBeforeAndDecideIndeterminate_thenReturnFalse() {
        when(constraintEnforcementService.blockingPreEnforceBundleFor(any(), any()))
                .thenReturn(BlockingConstraintHandlerBundle.BLOCKING_NOOP);
        when(pdp.decide(any(AuthorizationSubscription.class)))
                .thenReturn(Flux.just(AuthorizationDecision.INDETERMINATE));
        assertThatThrownBy(() -> testService.doSomething()).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser()
    void whenBeforeAndDecideEmpty_thenReturnFalse() {
        when(constraintEnforcementService.blockingPreEnforceBundleFor(any(), any()))
                .thenReturn(BlockingConstraintHandlerBundle.BLOCKING_NOOP);
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.empty());
        assertThatThrownBy(() -> testService.doSomething()).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser()
    void when_AfterAndDecideIsPermitWithResourceAndMethodReturnsOptional_then_ReturnTheReplacementObject() {
        final var replaceBundle = BlockingConstraintHandlerBundle.postEnforceConstraintHandlerBundle(
                FunctionUtil.noop(), FunctionUtil.sink(), UnaryOperator.identity(), FunctionUtil.sink(),
                UnaryOperator.identity(), FunctionUtil.all(), x -> CHANGED_RETURN_OBJECT);

        when(constraintEnforcementService.blockingPreEnforceBundleFor(any(), any())).thenReturn(replaceBundle);
        when(pdp.decide(any(AuthorizationSubscription.class)))
                .thenReturn(Flux.just(new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY,
                        Value.of(CHANGED_RETURN_OBJECT))));
        assertThat(testService.doSomethingOptional()).isEqualTo(Optional.of(CHANGED_RETURN_OBJECT));
    }

}
