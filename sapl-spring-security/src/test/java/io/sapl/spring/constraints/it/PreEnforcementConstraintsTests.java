/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.aop.framework.ReflectiveMethodInvocation;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.config.EnableSaplMethodSecurity;
import io.sapl.spring.constraints.api.MethodInvocationConstraintHandlerProvider;
import io.sapl.spring.constraints.api.RunnableConstraintHandlerProvider;
import io.sapl.spring.constraints.it.PreEnforcementConstraintsTests.Application;
import io.sapl.spring.constraints.it.PreEnforcementConstraintsTests.ConstraintHandlerOne;
import io.sapl.spring.constraints.it.PreEnforcementConstraintsTests.ConstraintHandlerTwo;
import io.sapl.spring.constraints.it.PreEnforcementConstraintsTests.FailingConstraintHandler;
import io.sapl.spring.constraints.it.PreEnforcementConstraintsTests.MethodSecurityConfiguration;
import io.sapl.spring.constraints.it.PreEnforcementConstraintsTests.SuccessfulMethodInvocationConstraintHandler;
import io.sapl.spring.constraints.it.PreEnforcementConstraintsTests.TestService;
import io.sapl.spring.method.metadata.PreEnforce;
import reactor.core.publisher.Flux;

@SpringBootTest(classes = { Application.class, TestService.class, MethodSecurityConfiguration.class,
        ConstraintHandlerOne.class, ConstraintHandlerTwo.class, FailingConstraintHandler.class,
        SuccessfulMethodInvocationConstraintHandler.class,
        SuccessfulMethodInvocationConstraintHandler.class }, properties = {
                "spring.main.web-application-type=servlet" })
class PreEnforcementConstraintsTests {
    private static final String UNKNOWN_CONSTRAINT                      = "unknown constraint";
    private static final String FAILING_CONSTRAINT                      = "failing constraint";
    private static final String KNOWN_CONSTRAINT                        = "known constraint";
    private static final String SUCCESSFUL_METHOD_INVOCATION_CONSTRAINT = "successful method invocation constraint";
    private static final String FAILING_METHOD_INVOCATION_CONSTRAINT    = "failing method invocation constraint";
    private static final String USER                                    = "user";

    public static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    @MockBean
    PolicyDecisionPoint pdp;

    @SpyBean
    ConstraintHandlerOne constraintHandlerOne;

    @SpyBean
    ConstraintHandlerTwo constraintHandlerTwo;

    @SpyBean
    TestService service;

    @SpringBootApplication
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
        public boolean isResponsible(JsonNode constraint) {
            return constraint != null && constraint.isTextual() && KNOWN_CONSTRAINT.equals(constraint.textValue());
        }

        @Override
        public Signal getSignal() {
            return Signal.ON_DECISION;
        }

        @Override
        public Runnable getHandler(JsonNode constraint) {
            return this::run;
        }

        public void run() {
        }

    }

    @Component
    public static class ConstraintHandlerTwo implements RunnableConstraintHandlerProvider {

        @Override
        public boolean isResponsible(JsonNode constraint) {
            return constraint != null && constraint.isTextual() && KNOWN_CONSTRAINT.equals(constraint.textValue());
        }

        @Override
        public Signal getSignal() {
            return Signal.ON_DECISION;
        }

        @Override
        public Runnable getHandler(JsonNode constraint) {
            return this::run;
        }

        public void run() {
        }

    }

    @Component
    public static class FailingConstraintHandler implements RunnableConstraintHandlerProvider {

        @Override
        public boolean isResponsible(JsonNode constraint) {
            return constraint != null && constraint.isTextual() && FAILING_CONSTRAINT.equals(constraint.textValue());
        }

        @Override
        public Signal getSignal() {
            return Signal.ON_DECISION;
        }

        @Override
        public Runnable getHandler(JsonNode constraint) {
            return this::run;
        }

        public void run() {
            throw new IllegalArgumentException("I fail because I must test!");
        }
    }

    @Component
    public static class FailingMethodInvocationConstraintHandler implements MethodInvocationConstraintHandlerProvider {

        @Override
        public boolean isResponsible(JsonNode constraint) {
            return constraint != null && constraint.isTextual()
                    && FAILING_METHOD_INVOCATION_CONSTRAINT.equals(constraint.textValue());
        }

        @Override
        public Consumer<ReflectiveMethodInvocation> getHandler(JsonNode constraint) {
            return this::accept;
        }

        public void accept(ReflectiveMethodInvocation invocation) {
            throw new IllegalArgumentException("I fail because I must test!");
        }

    }

    @Component
    public static class SuccessfulMethodInvocationConstraintHandler
            implements MethodInvocationConstraintHandlerProvider {

        @Override
        public boolean isResponsible(JsonNode constraint) {
            return constraint != null && constraint.isTextual()
                    && SUCCESSFUL_METHOD_INVOCATION_CONSTRAINT.equals(constraint.textValue());
        }

        @Override
        public Consumer<ReflectiveMethodInvocation> getHandler(JsonNode constraint) {
            return this::accept;
        }

        public void accept(ReflectiveMethodInvocation invocation) {
            Object[] originalArguments = invocation.getArguments();
            Object[] newArguments      = Arrays.copyOf(originalArguments, originalArguments.length);

            for (int i = 0; i < newArguments.length; i++)
                if (newArguments[i] instanceof String)
                    newArguments[i] = "replaced";

            invocation.setArguments(newArguments);
        }

    }

    @Service
    static class TestService {
        @PreEnforce
        public String execute(String argument) {
            return "Argument: " + argument;
        }

    }

    @Test
    void contextLoads(ApplicationContext context) {
        assertThat(context).isNotNull();
    }

    @Test
    @WithMockUser(USER)
    void when_testServiceCalled_then_pdpDecideIsInvoked() {
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
        service.execute("test");
        verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
    }

    @Test
    @WithMockUser(USER)
    void when_testServiceCalledAndPdpPermits_then_pdpMethodReturnsNormally() {
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
        assertEquals("Argument: test", service.execute("test"));
    }

    @Test
    @WithMockUser(USER)
    void when_testServiceCalledAndPdpDenies_then_pdpMethodThrowsAccessDenied() {
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));
        assertThrows(AccessDeniedException.class, () -> service.execute("test"));
        verify(service, times(0)).execute(any());
    }

    @Test
    @WithMockUser(USER)
    void when_testServiceCalledAndPdpIndeterminate_then_pdpMethodThrowsAccessDenied() {
        when(pdp.decide(any(AuthorizationSubscription.class)))
                .thenReturn(Flux.just(AuthorizationDecision.INDETERMINATE));
        assertThrows(AccessDeniedException.class, () -> service.execute("test"));
        verify(service, times(0)).execute(any());
    }

    @Test
    @WithMockUser(USER)
    void when_testServiceCalledAndPdpNotApplicable_then_pdpMethodThrowsAccessDenied() {
        when(pdp.decide(any(AuthorizationSubscription.class)))
                .thenReturn(Flux.just(AuthorizationDecision.NOT_APPLICABLE));
        assertThrows(AccessDeniedException.class, () -> service.execute("test"));
        verify(service, times(0)).execute(any());
    }

    @Test
    @WithMockUser(USER)
    void when_testServiceCalledAndPdpReturnsEmptyStream_then_pdpMethodThrowsAccessDenied() {
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.empty());
        assertThrows(AccessDeniedException.class, () -> service.execute("test"));
        verify(service, times(0)).execute(any());
    }

    @Test
    @WithMockUser(USER)
    void when_testServiceCalledAndPdpReturnsNull_then_pdpMethodThrowsAccessDenied() {
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(null);
        assertThrows(AccessDeniedException.class, () -> service.execute("test"));
        verify(service, times(0)).execute(any());
    }

    @Test
    @WithMockUser(USER)
    void when_testServiceCalledAndDecisionContainsUnenforceableObligation_then_pdpMethodThrowsAccessDenied() {
        var obligations = JSON.arrayNode();
        obligations.add(JSON.textNode(UNKNOWN_CONSTRAINT));
        var decision = AuthorizationDecision.PERMIT.withObligations(obligations);
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));
        assertThrows(AccessDeniedException.class, () -> service.execute("test"));
        verify(service, times(0)).execute(any());
    }

    @Test
    @WithMockUser(USER)
    void when_testServiceCalledAndDecisionContainsFailingObligation_then_pdpMethodThrowsAccessDenied() {
        var obligations = JSON.arrayNode();
        obligations.add(JSON.textNode(FAILING_CONSTRAINT));
        obligations.add(JSON.textNode(KNOWN_CONSTRAINT));
        var decision = AuthorizationDecision.PERMIT.withObligations(obligations);
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));
        assertThrows(AccessDeniedException.class, () -> service.execute("test"));
        verify(service, times(0)).execute(any());
    }

    @Test
    @WithMockUser(USER)
    void when_testServiceCalledAndDecisionContainsUnenforceableAdvice_then_pdpMethodInvoked() {
        var advice = JSON.arrayNode();
        advice.add(JSON.textNode(UNKNOWN_CONSTRAINT));
        var decision = AuthorizationDecision.PERMIT.withAdvice(advice);
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));
        assertEquals("Argument: test", service.execute("test"));
    }

    @Test
    @WithMockUser(USER)
    void when_testServiceCalledAndDecisionContainsFailingAdvice_then_normalAccessGranted() {
        var advice = JSON.arrayNode();
        advice.add(JSON.textNode(FAILING_CONSTRAINT));
        var decision = AuthorizationDecision.PERMIT.withAdvice(advice);
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));
        assertEquals("Argument: test", service.execute("test"));
    }

    @Test
    @WithMockUser(USER)
    void when_testServiceCalledAndDecisionContainsEnforceableObligation_then_pdpMethodReturnsNormallyAndHandlersAreInvoked() {
        var obligations = JSON.arrayNode();
        obligations.add(JSON.textNode(KNOWN_CONSTRAINT));
        var decision = AuthorizationDecision.PERMIT.withObligations(obligations);
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));
        assertEquals("Argument: test", service.execute("test"));
        verify(constraintHandlerTwo).run();
        verify(constraintHandlerOne).run();
    }

    @Test
    @WithMockUser(USER)
    void when_testServiceCalledAndDecisionDenyContainsEnforceableObligation_then_accessDeniedButConstraintsHandled() {
        var obligations = JSON.arrayNode();
        obligations.add(JSON.textNode(KNOWN_CONSTRAINT));
        var decision = AuthorizationDecision.DENY.withObligations(obligations);
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));
        assertThrows(AccessDeniedException.class, () -> service.execute("test"));
        verify(constraintHandlerTwo).run();
        verify(constraintHandlerOne).run();
        verify(service, times(0)).execute(any());
    }

    @Test
    @WithMockUser(USER)
    void when_testServiceCalledAndDecisionContainsEnforceableAdvice_then_pdpMethodReturnsNormallyAndHandlersAreInvoked() {
        var advice = JSON.arrayNode();
        advice.add(JSON.textNode(KNOWN_CONSTRAINT));
        var decision = AuthorizationDecision.PERMIT.withAdvice(advice);
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));
        InOrder inOrder = inOrder(constraintHandlerOne, constraintHandlerTwo);
        assertEquals("Argument: test", service.execute("test"));
        inOrder.verify(constraintHandlerOne).run();
        inOrder.verify(constraintHandlerTwo).run();
    }

    @Test
    @WithMockUser(USER)
    void when_testServiceCalledAndDecisionContainsEnforceableObligationsAndAdvice_then_pdpMethodReturnsNormallyAndHandlersAreInvoked() {
        var advice = JSON.arrayNode();
        advice.add(JSON.textNode(KNOWN_CONSTRAINT));
        var obligations = JSON.arrayNode();
        obligations.add(JSON.textNode(KNOWN_CONSTRAINT));
        var decision = AuthorizationDecision.PERMIT.withObligations(obligations).withAdvice(advice);
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));
        assertEquals("Argument: test", service.execute("test"));
        verify(constraintHandlerOne, times(2)).run();
        verify(constraintHandlerTwo, times(2)).run();
    }

    @Test
    @WithMockUser(USER)
    void when_testServiceCalledAndDecisionContainsEnforceableMethodInvocationManipulatingObligation_then_pdpMethodReturnsNormallyWithModifiedArgument() {
        var obligations = JSON.arrayNode();
        obligations.add(JSON.textNode(SUCCESSFUL_METHOD_INVOCATION_CONSTRAINT));
        var decision = AuthorizationDecision.PERMIT.withObligations(obligations);
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));
        assertEquals("Argument: replaced", service.execute("test"));
        verify(service, times(1)).execute("replaced");
    }

    @Test
    @WithMockUser(USER)
    void when_testServiceCalledAndDecisionContainsFailingMethodInvocationMaipulatingObligation_then_accessDenied() {
        var obligations = JSON.arrayNode();
        obligations.add(JSON.textNode(FAILING_METHOD_INVOCATION_CONSTRAINT));
        var decision = AuthorizationDecision.PERMIT.withObligations(obligations);
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));
        assertThrows(AccessDeniedException.class, () -> service.execute("test"));
        verify(service, times(0)).execute(any());
    }

    @Test
    @WithMockUser(USER)
    void when_testDecisionHasResource_then_replaced() {
        var decision = AuthorizationDecision.PERMIT.withResource(JSON.textNode("replacement"));
        when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(decision));
        assertEquals("replacement", service.execute("test"));
        verify(service, times(1)).execute(any());
    }
}
