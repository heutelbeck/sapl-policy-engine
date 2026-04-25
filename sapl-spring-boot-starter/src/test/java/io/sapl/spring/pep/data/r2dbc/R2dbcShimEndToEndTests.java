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
package io.sapl.spring.pep.data.r2dbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.security.access.AccessDeniedException;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.spring.pep.constraints.EnforcementPlanContext;
import io.sapl.spring.pep.constraints.EnforcementPlanner;
import io.sapl.spring.pep.constraints.Signal.RelationalQueryShimSignal;
import io.sapl.spring.pep.constraints.SignalType;
import io.sapl.spring.pep.constraints.providers.RelationalQueryManipulationProvider;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Real-world end-to-end tests for the R2DBC shim path: planner builds the
 * plan from an obligation, the shim-wrapped template intercepts the
 * Query-bearing call, and the obligation's criteria are injected before the
 * underlying template sees the query. No Spring context, no database — the
 * mock template captures what it actually receives so the assertion is on
 * the rewritten Query, not on intermediate machinery.
 */
@DisplayName("R2DBC shim end-to-end")
@ExtendWith(MockitoExtension.class)
class R2dbcShimEndToEndTests {

    private static final ObjectMapper MAPPER = JsonMapper.builder().addModule(new SaplJacksonModule()).build();

    @Mock
    private R2dbcEntityTemplate delegate;

    private R2dbcEntityTemplate shimmedTemplate;
    private EnforcementPlanner  planner;

    private static Value v(String json) {
        return MAPPER.readValue(json, Value.class);
    }

    @BeforeEach
    void setUp() {
        shimmedTemplate = (R2dbcEntityTemplate) new R2dbcShimBeanPostProcessor()
                .postProcessAfterInitialization(delegate, "r2dbcEntityTemplate");
        planner         = new EnforcementPlanner(List.of(new RelationalQueryManipulationProvider()), MAPPER);
    }

    private static AuthorizationDecision permitWithObligation(String obligationJson) {
        val obligations = ArrayValue.builder().add(v(obligationJson)).build();
        return new AuthorizationDecision(Decision.PERMIT, obligations, Value.EMPTY_ARRAY, Value.UNDEFINED);
    }

    @Nested
    @DisplayName("Tenant isolation obligation")
    class TenantIsolation {

        @Test
        @DisplayName("Repository query gets the obligation's criteria injected before the template sees it")
        void givenObligationWhenSelectThenInjectedQueryReachesDelegate() {
            val decision = permitWithObligation("""
                    {
                      "type": "relational:queryManipulation",
                      "criteria": [{"column": "tenant_id", "op": "=", "value": 7}]
                    }
                    """);
            val plan     = planner.plan(decision, java.util.Set.<SignalType>of(RelationalQueryShimSignal.TYPE));

            when(delegate.select(any(Query.class), eq(Citizen.class))).thenReturn(Flux.empty());

            val originalQuery = Query.empty();
            val callChain     = shimmedTemplate.select(originalQuery, Citizen.class);

            StepVerifier.create(EnforcementPlanContext.withReactor(plan, callChain.then())).verifyComplete();

            val captor = ArgumentCaptor.forClass(Query.class);
            verify(delegate).select(captor.capture(), eq(Citizen.class));
            assertThat(captor.getValue().getCriteria())
                    .hasValueSatisfying(c -> assertThat(c.toString()).contains("tenant_id = 7"));
        }

        @Test
        @DisplayName("Original query criteria are preserved and AND-combined with the obligation")
        void givenOriginalCriteriaWhenObligationAppliesThenBothApply() {
            val decision = permitWithObligation("""
                    {
                      "type": "relational:queryManipulation",
                      "criteria": [{"column": "tenant_id", "op": "=", "value": 7}]
                    }
                    """);
            val plan     = planner.plan(decision, java.util.Set.<SignalType>of(RelationalQueryShimSignal.TYPE));

            when(delegate.select(any(Query.class), eq(Citizen.class))).thenReturn(Flux.empty());

            val originalQuery = Query.query(Criteria.where("name").is("Vimes"));
            val callChain     = shimmedTemplate.select(originalQuery, Citizen.class);

            StepVerifier.create(EnforcementPlanContext.withReactor(plan, callChain.then())).verifyComplete();

            val captor = ArgumentCaptor.forClass(Query.class);
            verify(delegate).select(captor.capture(), eq(Citizen.class));
            assertThat(captor.getValue().getCriteria()).hasValueSatisfying(
                    c -> assertThat(c.toString()).contains("name = 'Vimes'").contains("tenant_id = 7").contains("AND"));
        }
    }

    @Nested
    @DisplayName("Pass-through when no plan is in scope")
    class PassThroughNoPlan {

        @Test
        @DisplayName("Repository call outside any PEP scope reaches the delegate with the original query")
        void givenNoPlanInContextWhenSelectThenOriginalQueryReachesDelegate() {
            when(delegate.select(any(Query.class), eq(Citizen.class))).thenReturn(Flux.empty());

            val originalQuery = Query.query(Criteria.where("name").is("Vimes"));

            StepVerifier.create(shimmedTemplate.select(originalQuery, Citizen.class).then()).verifyComplete();

            val captor = ArgumentCaptor.forClass(Query.class);
            verify(delegate).select(captor.capture(), eq(Citizen.class));
            assertThat(captor.getValue()).isSameAs(originalQuery);
        }
    }

    @Nested
    @DisplayName("Pass-through when plan has no matching obligation")
    class PassThroughNoMatchingObligation {

        @Test
        @DisplayName("A plan with an unrelated obligation does not transform the query")
        void givenPlanWithoutMatchingObligationWhenSelectThenOriginalQueryReachesDelegate() {
            val decision = permitWithObligation("""
                    {
                      "type": "mongo:queryManipulation",
                      "criteria": [{"column": "tenantId", "op": "=", "value": 7}]
                    }
                    """);
            val plan     = planner.plan(decision, java.util.Set.<SignalType>of(RelationalQueryShimSignal.TYPE));

            when(delegate.select(any(Query.class), eq(Citizen.class))).thenReturn(Flux.empty());

            val originalQuery = Query.query(Criteria.where("name").is("Vimes"));

            StepVerifier.create(EnforcementPlanContext.withReactor(plan,
                    shimmedTemplate.select(originalQuery, Citizen.class).then())).verifyComplete();

            val captor = ArgumentCaptor.forClass(Query.class);
            verify(delegate).select(captor.capture(), eq(Citizen.class));
            assertThat(captor.getValue().getCriteria()).hasValueSatisfying(
                    c -> assertThat(c.toString()).contains("name = 'Vimes'").doesNotContain("tenant"));
        }
    }

    @Nested
    @DisplayName("Fail-closed at the shim path")
    class FailClosed {

        @Test
        @DisplayName("Obligation handler that throws when applied to the query raises AccessDeniedException at the wrapper")
        void givenMapperThatThrowsWhenSelectThenAccessDenied() {
            val throwingProvider = new io.sapl.spring.pep.constraints.ConstraintHandlerProvider() {
                                     @Override
                                     public java.util.Optional<io.sapl.spring.pep.constraints.ScopedConstraintHandler> getConstraintHandler(
                                             Value constraint, java.util.Set<SignalType> supportedSignals) {
                                         if (!io.sapl.spring.constraints.providers.ConstraintResponsibility
                                                 .isResponsible(constraint, "test:throwingShimHandler")) {
                                             return java.util.Optional.empty();
                                         }
                                         io.sapl.spring.pep.constraints.ConstraintHandler.Mapper<Query> bad = q -> {
                                                                  throw new RuntimeException(
                                                                          "simulated handler failure");
                                                              };
                                         return java.util.Optional
                                                 .of(new io.sapl.spring.pep.constraints.ScopedConstraintHandler(bad,
                                                         RelationalQueryShimSignal.TYPE, 30));
                                     }
                                 };
            val throwingPlanner  = new EnforcementPlanner(List.of(throwingProvider), MAPPER);
            val decision         = permitWithObligation("""
                    {"type": "test:throwingShimHandler"}
                    """);
            val plan             = throwingPlanner.plan(decision,
                    java.util.Set.<SignalType>of(RelationalQueryShimSignal.TYPE));

            StepVerifier
                    .create(EnforcementPlanContext.withReactor(plan,
                            shimmedTemplate.select(Query.empty(), Citizen.class).then()))
                    .expectError(AccessDeniedException.class).verify();

            org.mockito.Mockito.verifyNoInteractions(delegate);
        }
    }

    @Nested
    @DisplayName("Plan propagation across reactive operator boundaries")
    class PlanPropagation {

        @Test
        @DisplayName("Plan reaches the shim wrapper across nested flatMap operators")
        void givenNestedFlatMapWhenSelectInsideThenPlanStillSeen() {
            val decision = permitWithObligation("""
                    {
                      "type": "relational:queryManipulation",
                      "criteria": [{"column": "tenant_id", "op": "=", "value": 7}]
                    }
                    """);
            val plan     = planner.plan(decision, java.util.Set.<SignalType>of(RelationalQueryShimSignal.TYPE));

            when(delegate.select(any(Query.class), eq(Citizen.class))).thenReturn(Flux.empty());

            val deeplyNested = reactor.core.publisher.Mono.just("trigger")
                    .flatMap(s -> reactor.core.publisher.Mono.just(s + "-1"))
                    .flatMap(s -> reactor.core.publisher.Mono.just(s + "-2"))
                    .flatMap(s -> shimmedTemplate.select(Query.empty(), Citizen.class).then());

            StepVerifier.create(EnforcementPlanContext.withReactor(plan, deeplyNested)).verifyComplete();

            val captor = ArgumentCaptor.forClass(Query.class);
            verify(delegate).select(captor.capture(), eq(Citizen.class));
            assertThat(captor.getValue().getCriteria())
                    .hasValueSatisfying(c -> assertThat(c.toString()).contains("tenant_id = 7"));
        }
    }

    record Citizen(Long id, String name) {}
}
