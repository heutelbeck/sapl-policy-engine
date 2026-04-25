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
package io.sapl.spring.pep.data.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.AccessDeniedException;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.spring.constraints.providers.ConstraintResponsibility;
import io.sapl.spring.pep.constraints.ConstraintHandler;
import io.sapl.spring.pep.constraints.ConstraintHandlerProvider;
import io.sapl.spring.pep.constraints.EnforcementPlanContext;
import io.sapl.spring.pep.constraints.EnforcementPlanner;
import io.sapl.spring.pep.constraints.ScopedConstraintHandler;
import io.sapl.spring.pep.constraints.Signal.MongoDbQueryShimSignal;
import io.sapl.spring.pep.constraints.SignalType;
import io.sapl.spring.pep.constraints.providers.MongoDbQueryManipulationProvider;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Real-world end-to-end tests for the Mongo shim path. Same scenario set as
 * the R2DBC suite, adapted for the {@code find(Query, Class)} entry point.
 * The mock template captures what reaches it so the assertions are on the
 * rewritten {@link Query}, not on intermediate machinery.
 */
@DisplayName("Mongo shim end-to-end")
@ExtendWith(MockitoExtension.class)
class MongoShimEndToEndTests {

    private static final ObjectMapper MAPPER = JsonMapper.builder().addModule(new SaplJacksonModule()).build();

    @Mock
    private ReactiveMongoTemplate delegate;

    private ReactiveMongoTemplate shimmedTemplate;
    private EnforcementPlanner    planner;

    private static Value v(String json) {
        return MAPPER.readValue(json, Value.class);
    }

    @BeforeEach
    void setUp() {
        shimmedTemplate = (ReactiveMongoTemplate) new MongoShimBeanPostProcessor()
                .postProcessAfterInitialization(delegate, "reactiveMongoTemplate");
        planner         = new EnforcementPlanner(List.of(new MongoDbQueryManipulationProvider()), MAPPER);
    }

    private static AuthorizationDecision permitWithObligation(String obligationJson) {
        val obligations = ArrayValue.builder().add(v(obligationJson)).build();
        return new AuthorizationDecision(Decision.PERMIT, obligations, Value.EMPTY_ARRAY, Value.UNDEFINED);
    }

    @Nested
    @DisplayName("Tenant isolation obligation")
    class TenantIsolation {

        @Test
        @DisplayName("Repository find gets the obligation's criteria injected before the template sees it")
        void givenObligationWhenFindThenInjectedQueryReachesDelegate() {
            val decision = permitWithObligation("""
                    {
                      "type": "mongo:queryManipulation",
                      "criteria": [{"column": "tenantId", "op": "=", "value": 7}]
                    }
                    """);
            val plan     = planner.plan(decision, Set.<SignalType>of(MongoDbQueryShimSignal.TYPE));

            when(delegate.find(any(Query.class), eq(Citizen.class))).thenReturn(Flux.empty());

            val originalQuery = new Query();
            val callChain     = shimmedTemplate.find(originalQuery, Citizen.class);

            StepVerifier.create(EnforcementPlanContext.withReactor(plan, callChain.then())).verifyComplete();

            val captor = ArgumentCaptor.forClass(Query.class);
            verify(delegate).find(captor.capture(), eq(Citizen.class));
            assertThat(captor.getValue().getQueryObject().toJson()).contains("\"tenantId\": 7");
        }

        @Test
        @DisplayName("Original query criteria are preserved alongside the obligation")
        void givenOriginalCriteriaWhenObligationAppliesThenBothApply() {
            val decision = permitWithObligation("""
                    {
                      "type": "mongo:queryManipulation",
                      "criteria": [{"column": "tenantId", "op": "=", "value": 7}]
                    }
                    """);
            val plan     = planner.plan(decision, Set.<SignalType>of(MongoDbQueryShimSignal.TYPE));

            when(delegate.find(any(Query.class), eq(Citizen.class))).thenReturn(Flux.empty());

            val originalQuery = new Query(Criteria.where("name").is("Vimes"));
            val callChain     = shimmedTemplate.find(originalQuery, Citizen.class);

            StepVerifier.create(EnforcementPlanContext.withReactor(plan, callChain.then())).verifyComplete();

            val captor = ArgumentCaptor.forClass(Query.class);
            verify(delegate).find(captor.capture(), eq(Citizen.class));
            val rendered = captor.getValue().getQueryObject().toJson();
            assertThat(rendered).contains("name").contains("Vimes").contains("tenantId");
        }
    }

    @Nested
    @DisplayName("Pass-through when no plan is in scope")
    class PassThroughNoPlan {

        @Test
        @DisplayName("Repository call outside any PEP scope reaches the delegate with the original query")
        void givenNoPlanInContextWhenFindThenOriginalQueryReachesDelegate() {
            when(delegate.find(any(Query.class), eq(Citizen.class))).thenReturn(Flux.empty());

            val originalQuery = new Query(Criteria.where("name").is("Vimes"));

            StepVerifier.create(shimmedTemplate.find(originalQuery, Citizen.class).then()).verifyComplete();

            val captor = ArgumentCaptor.forClass(Query.class);
            verify(delegate).find(captor.capture(), eq(Citizen.class));
            assertThat(captor.getValue()).isSameAs(originalQuery);
        }
    }

    @Nested
    @DisplayName("Pass-through when plan has no matching obligation")
    class PassThroughNoMatchingObligation {

        @Test
        @DisplayName("A plan with an unrelated obligation does not transform the query")
        void givenPlanWithoutMatchingObligationWhenFindThenOriginalQueryReachesDelegate() {
            val decision = permitWithObligation("""
                    {
                      "type": "relational:queryManipulation",
                      "criteria": [{"column": "tenant_id", "op": "=", "value": 7}]
                    }
                    """);
            val plan     = planner.plan(decision, Set.<SignalType>of(MongoDbQueryShimSignal.TYPE));

            when(delegate.find(any(Query.class), eq(Citizen.class))).thenReturn(Flux.empty());

            val originalQuery = new Query(Criteria.where("name").is("Vimes"));

            StepVerifier.create(
                    EnforcementPlanContext.withReactor(plan, shimmedTemplate.find(originalQuery, Citizen.class).then()))
                    .verifyComplete();

            val captor = ArgumentCaptor.forClass(Query.class);
            verify(delegate).find(captor.capture(), eq(Citizen.class));
            val rendered = captor.getValue().getQueryObject().toJson();
            assertThat(rendered).contains("Vimes").doesNotContain("tenant");
        }
    }

    @Nested
    @DisplayName("Fail-closed at the shim path")
    class FailClosed {

        @Test
        @DisplayName("Obligation handler that throws raises AccessDeniedException at the wrapper without calling the delegate")
        void givenMapperThatThrowsWhenFindThenAccessDenied() {
            val throwingProvider = new ConstraintHandlerProvider() {
                                     @Override
                                     public Optional<ScopedConstraintHandler> getConstraintHandler(Value constraint,
                                             Set<SignalType> supportedSignals) {
                                         if (!ConstraintResponsibility.isResponsible(constraint,
                                                 "test:throwingShimHandler")) {
                                             return Optional.empty();
                                         }
                                         ConstraintHandler.Mapper<Query> bad = q -> {
                                                                  throw new RuntimeException(
                                                                          "simulated handler failure");
                                                              };
                                         return Optional
                                                 .of(new ScopedConstraintHandler(bad, MongoDbQueryShimSignal.TYPE, 30));
                                     }
                                 };
            val throwingPlanner  = new EnforcementPlanner(List.of(throwingProvider), MAPPER);
            val decision         = permitWithObligation("""
                    {"type": "test:throwingShimHandler"}
                    """);
            val plan             = throwingPlanner.plan(decision, Set.<SignalType>of(MongoDbQueryShimSignal.TYPE));

            StepVerifier
                    .create(EnforcementPlanContext.withReactor(plan,
                            shimmedTemplate.find(new Query(), Citizen.class).then()))
                    .expectError(AccessDeniedException.class).verify();

            verifyNoInteractions(delegate);
        }
    }

    @Nested
    @DisplayName("Plan propagation across reactive operator boundaries")
    class PlanPropagation {

        @Test
        @DisplayName("Plan reaches the shim wrapper across nested flatMap operators")
        void givenNestedFlatMapWhenFindInsideThenPlanStillSeen() {
            val decision = permitWithObligation("""
                    {
                      "type": "mongo:queryManipulation",
                      "criteria": [{"column": "tenantId", "op": "=", "value": 7}]
                    }
                    """);
            val plan     = planner.plan(decision, Set.<SignalType>of(MongoDbQueryShimSignal.TYPE));

            when(delegate.find(any(Query.class), eq(Citizen.class))).thenReturn(Flux.empty());

            val deeplyNested = Mono.just("trigger").flatMap(s -> Mono.just(s + "-1")).flatMap(s -> Mono.just(s + "-2"))
                    .flatMap(s -> shimmedTemplate.find(new Query(), Citizen.class).then());

            StepVerifier.create(EnforcementPlanContext.withReactor(plan, deeplyNested)).verifyComplete();

            val captor = ArgumentCaptor.forClass(Query.class);
            verify(delegate).find(captor.capture(), eq(Citizen.class));
            assertThat(captor.getValue().getQueryObject().toJson()).contains("tenantId");
        }
    }

    @Nested
    @DisplayName("find with collection-name overload")
    class FindWithCollectionName {

        @Test
        @DisplayName("find(Query, Class, String) is also intercepted")
        void givenObligationWhenFindWithCollectionNameThenInjectedQueryReachesDelegate() {
            val decision = permitWithObligation("""
                    {
                      "type": "mongo:queryManipulation",
                      "criteria": [{"column": "tenantId", "op": "=", "value": 7}]
                    }
                    """);
            val plan     = planner.plan(decision, Set.<SignalType>of(MongoDbQueryShimSignal.TYPE));

            when(delegate.find(any(Query.class), eq(Citizen.class), eq("citizens"))).thenReturn(Flux.empty());

            val originalQuery = new Query();

            StepVerifier.create(EnforcementPlanContext.withReactor(plan,
                    shimmedTemplate.find(originalQuery, Citizen.class, "citizens").then())).verifyComplete();

            val captor = ArgumentCaptor.forClass(Query.class);
            verify(delegate).find(captor.capture(), eq(Citizen.class), eq("citizens"));
            assertThat(captor.getValue().getQueryObject().toJson()).contains("\"tenantId\": 7");
        }
    }

    record Citizen(String id, String name, Integer tenantId) {}
}
