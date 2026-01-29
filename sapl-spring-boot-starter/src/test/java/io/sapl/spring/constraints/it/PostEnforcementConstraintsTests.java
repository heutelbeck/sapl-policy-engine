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
package io.sapl.spring.constraints.it;

import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.config.EnableSaplMethodSecurity;
import io.sapl.spring.constraints.api.RunnableConstraintHandlerProvider;
import io.sapl.spring.constraints.it.PostEnforcementConstraintsTests.*;
import io.sapl.spring.method.metadata.PostEnforce;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import reactor.core.publisher.Flux;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = { Application.class, TestService.class, MethodSecurityConfiguration.class,
        ConstraintHandlerOne.class, ConstraintHandlerTwo.class,
        FailingConstraintHandler.class }, properties = { "spring.main.web-application-type=servlet" })
class PostEnforcementConstraintsTests {
    private static final String UNKNOWN_CONSTRAINT = "unknown constraint";
    private static final String FAILING_CONSTRAINT = "failing constraint";
    private static final String KNOWN_CONSTRAINT   = "known constraint";

    private static AuthorizationDecision permitWithObligations(Value... obligations) {
        return new AuthorizationDecision(Decision.PERMIT, Value.ofArray(obligations), Value.EMPTY_ARRAY,
                Value.UNDEFINED);
    }

    private static AuthorizationDecision denyWithObligations(Value... obligations) {
        return new AuthorizationDecision(Decision.DENY, Value.ofArray(obligations), Value.EMPTY_ARRAY, Value.UNDEFINED);
    }

    private static AuthorizationDecision permitWithAdvice(Value... advice) {
        return new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, Value.ofArray(advice), Value.UNDEFINED);
    }

    @MockitoBean
    PolicyDecisionPoint pdp;

    @MockitoSpyBean
    ConstraintHandlerOne constraintHandlerOne;

    @MockitoSpyBean
    ConstraintHandlerTwo constraintHandlerTwo;

    @MockitoSpyBean
    TestService service;

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

    @Component
    public static class ConstraintHandlerOne implements RunnableConstraintHandlerProvider {

        @Override
        public boolean isResponsible(Value constraint) {
            return constraint instanceof TextValue text && KNOWN_CONSTRAINT.equals(text.value());
        }

        @Override
        public Signal getSignal() {
            return Signal.ON_DECISION;
        }

        @Override
        public Runnable getHandler(Value constraint) {
            return this::run;
        }

        public void run() {
            // NOOP test dummy
        }

    }

    @Component
    public static class ConstraintHandlerTwo implements RunnableConstraintHandlerProvider {

        @Override
        public boolean isResponsible(Value constraint) {
            return constraint instanceof TextValue text && KNOWN_CONSTRAINT.equals(text.value());
        }

        @Override
        public Signal getSignal() {
            return Signal.ON_DECISION;
        }

        @Override
        public Runnable getHandler(Value constraint) {
            return this::run;
        }

        public void run() {
            // NOOP test dummy
        }

    }

    @Component
    public static class FailingConstraintHandler implements RunnableConstraintHandlerProvider {

        @Override
        public boolean isResponsible(Value constraint) {
            return constraint instanceof TextValue text && FAILING_CONSTRAINT.equals(text.value());
        }

        @Override
        public Signal getSignal() {
            return Signal.ON_DECISION;
        }

        @Override
        public Runnable getHandler(Value constraint) {
            return this::run;
        }

        public void run() {
            throw new IllegalArgumentException("I fail because I must test!");
        }
    }

    @Service
    static class TestService {
        @PostEnforce
        public String execute(String argument) {
            return "Argument: " + argument;
        }

        @PostEnforce
        public Optional<String> executeOptional(String argument) {
            return Optional.of("Argument: " + argument);
        }

        @PostEnforce
        public Optional<String> executeOptionalEmpty() {
            return Optional.empty();
        }
    }

    @Test
    void contextLoads(ApplicationContext context) {
        assertThat(context).isNotNull();
    }

    @Test
    @WithMockUser()
    void when_testServiceCalled_then_pdpDecideIsInvoked() {
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
        service.execute("test");
        verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
    }

    @Test
    @WithMockUser()
    void when_testServiceCalledAndPdpPermits_then_pdpMethodReturnsNormally() {
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
        assertEquals("Argument: test", service.execute("test"));
        verify(pdp).decide(any(AuthorizationSubscription.class));
        verify(service, times(1)).execute(any());
    }

    @Test
    @WithMockUser()
    void when_testServiceCalledAndPdpDenies_then_pdpMethodThrowsAccessDeniedButWasInvoked() {
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));
        assertThrows(AccessDeniedException.class, () -> service.execute("test"));
        verify(service, times(1)).execute(any());
    }

    @Test
    @WithMockUser()
    void when_testServiceCalledAndPdpIndeterminate_then_pdpMethodThrowsAccessDeniedButWasInvoked() {
        when(pdp.decide(any(AuthorizationSubscription.class)))
                .thenReturn(Flux.just(AuthorizationDecision.INDETERMINATE));
        assertThrows(AccessDeniedException.class, () -> service.execute("test"));
        verify(service, times(1)).execute(any());
    }

    @Test
    @WithMockUser()
    void when_testServiceCalledAndPdpNotApplicable_then_pdpMethodThrowsAccessDeniedButWasInvoked() {
        when(pdp.decide(any(AuthorizationSubscription.class)))
                .thenReturn(Flux.just(AuthorizationDecision.NOT_APPLICABLE));
        assertThrows(AccessDeniedException.class, () -> service.execute("test"));
        verify(service, times(1)).execute(any());
    }

    @Test
    @WithMockUser()
    void when_testServiceCalledAndPdpReturnsEmptyStream_then_pdpMethodThrowsAccessDeniedButWasInvoked() {
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.empty());
        assertThrows(AccessDeniedException.class, () -> service.execute("test"));
        verify(pdp).decide(any(AuthorizationSubscription.class));
        verify(service, times(1)).execute(any());
    }

    @Test
    @WithMockUser()
    void when_testServiceCalledAndPdpReturnsNull_then_pdpMethodThrowsAccessDeniedButWasInvoked() {
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(null);
        assertThrows(AccessDeniedException.class, () -> service.execute("test"));
        verify(pdp).decide(any(AuthorizationSubscription.class));
        verify(service, times(1)).execute(any());
    }

    @Test
    @WithMockUser()
    void when_testServiceCalledAndDecisionContainsUnenforceableObligation_then_pdpMethodThrowsAccessDeniedButWasInvoked() {
        final var decision = permitWithObligations(Value.of(UNKNOWN_CONSTRAINT));
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));
        assertThrows(AccessDeniedException.class, () -> service.execute("test"));
        verify(pdp).decide(any(AuthorizationSubscription.class));
        verify(service, times(1)).execute(any());
    }

    @Test
    @WithMockUser()
    void when_testServiceCalledAndDecisionContainsFailingObligation_then_pdpMethodThrowsAccessDeniedButWasInvoked() {
        final var decision = permitWithObligations(Value.of(FAILING_CONSTRAINT), Value.of(KNOWN_CONSTRAINT));
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));
        assertThrows(AccessDeniedException.class, () -> service.execute("test"));
        verify(pdp).decide(any(AuthorizationSubscription.class));
        verify(service, times(1)).execute(any());
    }

    @Test
    @WithMockUser()
    void when_testServiceCalledAndDecisionContainsUnenforceableAdvice_then_accessGranted() {
        final var decision = permitWithAdvice(Value.of(UNKNOWN_CONSTRAINT));
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));
        assertEquals("Argument: test", service.execute("test"));
        verify(pdp).decide(any(AuthorizationSubscription.class));
        verify(service, times(1)).execute(any());
    }

    @Test
    @WithMockUser()
    void when_testServiceCalledAndDecisionContainsFailingAdvice_then_normalAccessGranted() {
        final var decision = permitWithAdvice(Value.of(FAILING_CONSTRAINT));
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));
        assertEquals("Argument: test", service.execute("test"));
        verify(pdp).decide(any(AuthorizationSubscription.class));
        verify(service, times(1)).execute(any());
    }

    @Test
    @WithMockUser()
    void when_testServiceCalledAndDecisionContainsEnforceableObligation_then_pdpMethodReturnsNormallyAndHandlersAreInvoked() {
        final var decision = permitWithObligations(Value.of(KNOWN_CONSTRAINT));
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));
        assertEquals("Argument: test", service.execute("test"));
        verify(pdp).decide(any(AuthorizationSubscription.class));
        verify(constraintHandlerTwo).run();
        verify(constraintHandlerOne).run();
    }

    @Test
    @WithMockUser()
    void when_testServiceCalledAndDecisionDenyContainsEnforceableObligation_then_accessDeniedButConstraintsHandled() {
        final var decision = denyWithObligations(Value.of(KNOWN_CONSTRAINT));
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));
        assertThrows(AccessDeniedException.class, () -> service.execute("test"));
        verify(pdp).decide(any(AuthorizationSubscription.class));
        verify(constraintHandlerOne).run();
        verify(constraintHandlerTwo).run();
        verify(service, times(1)).execute(any());
    }

    @Test
    @WithMockUser()
    void when_testServiceCalledAndDecisionContainsEnforceableAdvice_then_pdpMethodReturnsNormallyAndHandlersAreInvoked() {
        final var decision = permitWithAdvice(Value.of(KNOWN_CONSTRAINT));
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));
        assertEquals("Argument: test", service.execute("test"));
        verify(pdp).decide(any(AuthorizationSubscription.class));
        verify(constraintHandlerTwo).run();
        verify(constraintHandlerOne).run();
        verify(service, times(1)).execute(any());
    }

    @Test
    @WithMockUser()
    void when_testServiceCalledAndDecisionContainsEnforceableObligationsAndAdvice_then_pdpMethodReturnsNormallyAndHandlersAreInvoked() {
        final var knownConstraint = Value.of(KNOWN_CONSTRAINT);
        final var decision        = new AuthorizationDecision(Decision.PERMIT, Value.ofArray(knownConstraint),
                Value.ofArray(knownConstraint), Value.UNDEFINED);
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));
        assertEquals("Argument: test", service.execute("test"));
        verify(pdp).decide(any(AuthorizationSubscription.class));
        verify(constraintHandlerOne, times(2)).run();
        verify(constraintHandlerTwo, times(2)).run();
        verify(service, times(1)).execute(any());
    }

    @Test
    @WithMockUser()
    void when_testServiceCalledOptionalReturnValueAndPermit_then_returnsNormally() {
        final var decision = AuthorizationDecision.PERMIT;
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));
        assertEquals(Optional.of("Argument: test"), service.executeOptional("test"));
        verify(pdp).decide(any(AuthorizationSubscription.class));
        verify(service, times(1)).executeOptional(any());
    }

    @Test
    @WithMockUser()
    void when_testServiceCalledOptionalEmptyReturnValueAndPermit_then_returnsNormally() {
        final var decision = AuthorizationDecision.PERMIT;
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));
        assertEquals(Optional.empty(), service.executeOptionalEmpty());
        verify(pdp).decide(any(AuthorizationSubscription.class));
        verify(service, times(1)).executeOptionalEmpty();
    }

}
