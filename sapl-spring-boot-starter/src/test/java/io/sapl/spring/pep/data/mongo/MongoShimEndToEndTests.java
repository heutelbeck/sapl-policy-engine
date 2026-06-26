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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
import org.springframework.data.mongodb.core.ReactiveFindOperation.FindWithQuery;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.mapreduce.MapReduceOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.query.UpdateDefinition;
import org.springframework.security.access.AccessDeniedException;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.spring.pep.constraints.ConstraintHandler;
import io.sapl.spring.pep.constraints.ConstraintHandlerProvider;
import io.sapl.spring.pep.constraints.EnforcementPlanContext;
import io.sapl.spring.pep.constraints.EnforcementPlanner;
import io.sapl.spring.pep.constraints.ScopedConstraintHandler;
import io.sapl.spring.pep.constraints.Signal.MongoDbQueryShimSignal;
import io.sapl.spring.pep.constraints.SignalType;
import io.sapl.spring.pep.constraints.providers.MongoDbQueryRewritingProvider;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Real-world end-to-end tests for the Mongo shim path. Same scenario set as the
 * R2DBC suite, adapted for the
 * {@code find(Query, Class)} entry point. The mock template captures what
 * reaches it so the assertions are on the
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
        planner         = new EnforcementPlanner(List.of(new MongoDbQueryRewritingProvider()), MAPPER);
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
                      "type": "mongo:queryRewriting",
                      "criteria": [{"column": "tenantId", "op": "=", "value": 7}]
                    }
                    """);
            val plan     = planner.plan(decision, Set.<SignalType>of(MongoDbQueryShimSignal.SIGNAL_TYPE));

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
                      "type": "mongo:queryRewriting",
                      "criteria": [{"column": "tenantId", "op": "=", "value": 7}]
                    }
                    """);
            val plan     = planner.plan(decision, Set.<SignalType>of(MongoDbQueryShimSignal.SIGNAL_TYPE));

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
                      "type": "relational:queryRewriting",
                      "criteria": [{"column": "tenant_id", "op": "=", "value": 7}]
                    }
                    """);
            val plan     = planner.plan(decision, Set.<SignalType>of(MongoDbQueryShimSignal.SIGNAL_TYPE));

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
                                     public List<ScopedConstraintHandler> getConstraintHandlers(Value constraint,
                                             Set<SignalType> supportedSignals) {
                                         if (!ConstraintHandlerProvider.constraintIsOfType(constraint,
                                                 "test:throwingShimHandler")) {
                                             return List.of();
                                         }
                                         ConstraintHandler.Mapper<Query> bad = q -> {
                                                                  throw new RuntimeException(
                                                                          "simulated handler failure");
                                                              };
                                         return List.of(new ScopedConstraintHandler(bad,
                                                 MongoDbQueryShimSignal.SIGNAL_TYPE, 30));
                                     }
                                 };
            val throwingPlanner  = new EnforcementPlanner(List.of(throwingProvider), MAPPER);
            val decision         = permitWithObligation("""
                    {"type": "test:throwingShimHandler"}
                    """);
            val plan             = throwingPlanner.plan(decision,
                    Set.<SignalType>of(MongoDbQueryShimSignal.SIGNAL_TYPE));

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
                      "type": "mongo:queryRewriting",
                      "criteria": [{"column": "tenantId", "op": "=", "value": 7}]
                    }
                    """);
            val plan     = planner.plan(decision, Set.<SignalType>of(MongoDbQueryShimSignal.SIGNAL_TYPE));

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
                      "type": "mongo:queryRewriting",
                      "criteria": [{"column": "tenantId", "op": "=", "value": 7}]
                    }
                    """);
            val plan     = planner.plan(decision, Set.<SignalType>of(MongoDbQueryShimSignal.SIGNAL_TYPE));

            when(delegate.find(any(Query.class), eq(Citizen.class), eq("citizens"))).thenReturn(Flux.empty());

            val originalQuery = new Query();

            StepVerifier.create(EnforcementPlanContext.withReactor(plan,
                    shimmedTemplate.find(originalQuery, Citizen.class, "citizens").then())).verifyComplete();

            val captor = ArgumentCaptor.forClass(Query.class);
            verify(delegate).find(captor.capture(), eq(Citizen.class), eq("citizens"));
            assertThat(captor.getValue().getQueryObject().toJson()).contains("\"tenantId\": 7");
        }
    }

    @Nested
    @DisplayName("Fail-closed on unexpected terminating return type")
    class UnexpectedTerminatingReturnType {

        @Test
        @DisplayName("A terminating method whose return type is neither Flux nor Mono is rejected, never run with an unenforced query")
        void givenNonReactiveTerminatingReturnTypeWhenDispatchedThenFailsClosed() throws Exception {
            @SuppressWarnings("unchecked")
            val          delegateChain = (FindWithQuery<Citizen>) org.mockito.Mockito.mock(FindWithQuery.class);
            val          originalQuery = new Query();
            final Method dispatch      = MongoShimMethodInterceptor.class.getDeclaredMethod("dispatchTerminatingFind",
                    FindWithQuery.class, Query.class, Method.class, Object[].class);
            dispatch.setAccessible(true);
            final Method nonReactive = Object.class.getMethod("toString");

            assertThatThrownBy(() -> dispatch.invoke(null, delegateChain, originalQuery, nonReactive, new Object[0]))
                    .isInstanceOf(InvocationTargetException.class).cause()
                    .isInstanceOf(UnsupportedOperationException.class);

            verifyNoInteractions(delegateChain);
        }
    }

    @Nested
    @DisplayName("Write-selection narrowing via the generic Query-first rule")
    class WriteSelectionNarrowing {

        private static final String TENANT_OBLIGATION = """
                {
                  "type": "mongo:queryRewriting",
                  "criteria": [{"column": "tenantId", "op": "=", "value": 7}]
                }
                """;

        @Test
        @DisplayName("updateMulti(Query, Update, Class) selection is narrowed before the delegate runs")
        void givenObligationWhenUpdateMultiThenSelectionNarrowed() {
            val plan   = planner.plan(permitWithObligation(TENANT_OBLIGATION),
                    Set.<SignalType>of(MongoDbQueryShimSignal.SIGNAL_TYPE));
            val update = new Update().set("name", "Vetinari");
            when(delegate.updateMulti(any(Query.class), any(UpdateDefinition.class), eq(Citizen.class)))
                    .thenReturn(Mono.empty());

            val callChain = shimmedTemplate.updateMulti(new Query(), update, Citizen.class);
            StepVerifier.create(EnforcementPlanContext.withReactor(plan, callChain.then())).verifyComplete();

            val captor = ArgumentCaptor.forClass(Query.class);
            verify(delegate).updateMulti(captor.capture(), any(UpdateDefinition.class), eq(Citizen.class));
            assertThat(captor.getValue().getQueryObject().toJson()).contains("\"tenantId\": 7");
        }

        @Test
        @DisplayName("findAndModify(Query, Update, Class) selection is narrowed before the delegate runs")
        void givenObligationWhenFindAndModifyThenSelectionNarrowed() {
            val plan   = planner.plan(permitWithObligation(TENANT_OBLIGATION),
                    Set.<SignalType>of(MongoDbQueryShimSignal.SIGNAL_TYPE));
            val update = new Update().set("name", "Vetinari");
            when(delegate.findAndModify(any(Query.class), any(UpdateDefinition.class), eq(Citizen.class)))
                    .thenReturn(Mono.empty());

            val callChain = shimmedTemplate.findAndModify(new Query(), update, Citizen.class);
            StepVerifier.create(EnforcementPlanContext.withReactor(plan, callChain.then())).verifyComplete();

            val captor = ArgumentCaptor.forClass(Query.class);
            verify(delegate).findAndModify(captor.capture(), any(UpdateDefinition.class), eq(Citizen.class));
            assertThat(captor.getValue().getQueryObject().toJson()).contains("\"tenantId\": 7");
        }

        @Test
        @DisplayName("remove(Query, Class) selection is narrowed before the delegate runs")
        void givenObligationWhenRemoveThenSelectionNarrowed() {
            val plan = planner.plan(permitWithObligation(TENANT_OBLIGATION),
                    Set.<SignalType>of(MongoDbQueryShimSignal.SIGNAL_TYPE));
            when(delegate.remove(any(Query.class), eq(Citizen.class))).thenReturn(Mono.empty());

            val callChain = shimmedTemplate.remove(new Query(), Citizen.class);
            StepVerifier.create(EnforcementPlanContext.withReactor(plan, callChain.then())).verifyComplete();

            val captor = ArgumentCaptor.forClass(Query.class);
            verify(delegate).remove(captor.capture(), eq(Citizen.class));
            assertThat(captor.getValue().getQueryObject().toJson()).contains("\"tenantId\": 7");
        }
    }

    @Nested
    @DisplayName("findAll is rerouted through a narrowed find")
    class FindAllReroute {

        @Test
        @DisplayName("findAll(Class) reaches the delegate as a narrowed find, never the unfiltered findAll")
        void givenObligationWhenFindAllThenNarrowedFind() {
            val decision = permitWithObligation("""
                    {
                      "type": "mongo:queryRewriting",
                      "criteria": [{"column": "tenantId", "op": "=", "value": 7}]
                    }
                    """);
            val plan     = planner.plan(decision, Set.<SignalType>of(MongoDbQueryShimSignal.SIGNAL_TYPE));
            when(delegate.find(any(Query.class), eq(Citizen.class))).thenReturn(Flux.empty());

            StepVerifier.create(EnforcementPlanContext.withReactor(plan, shimmedTemplate.findAll(Citizen.class).then()))
                    .verifyComplete();

            val captor = ArgumentCaptor.forClass(Query.class);
            verify(delegate).find(captor.capture(), eq(Citizen.class));
            assertThat(captor.getValue().getQueryObject().toJson()).contains("\"tenantId\": 7");
        }
    }

    @Nested
    @DisplayName("Fail-closed on operations that cannot be narrowed by a row-level query")
    class DenyUnsupportedOperations {

        @Test
        @DisplayName("aggregate is denied while a narrowing obligation is in scope, without touching the delegate")
        void givenObligationWhenAggregateThenAccessDenied() {
            val decision = permitWithObligation("""
                    {
                      "type": "mongo:queryRewriting",
                      "criteria": [{"column": "tenantId", "op": "=", "value": 7}]
                    }
                    """);
            val plan     = planner.plan(decision, Set.<SignalType>of(MongoDbQueryShimSignal.SIGNAL_TYPE));
            val pipeline = Aggregation.newAggregation(Aggregation.match(Criteria.where("name").is("Vimes")));

            StepVerifier
                    .create(EnforcementPlanContext.withReactor(plan,
                            shimmedTemplate.aggregate(pipeline, Citizen.class, Citizen.class).then()))
                    .expectError(AccessDeniedException.class).verify();

            verifyNoInteractions(delegate);
        }

        @Test
        @SuppressWarnings("deprecation")
        @DisplayName("query-first mapReduce is denied while a narrowing obligation is in scope, not narrowed-and-run")
        void givenObligationWhenQueryFirstMapReduceThenAccessDenied() {
            val decision = permitWithObligation("""
                    {
                      "type": "mongo:queryRewriting",
                      "criteria": [{"column": "tenantId", "op": "=", "value": 7}]
                    }
                    """);
            val plan     = planner.plan(decision, Set.<SignalType>of(MongoDbQueryShimSignal.SIGNAL_TYPE));

            val callChain = shimmedTemplate.mapReduce(new Query(), Citizen.class, Citizen.class, "function () {}",
                    "function () {}", MapReduceOptions.options());

            StepVerifier.create(EnforcementPlanContext.withReactor(plan, callChain.then()))
                    .expectError(AccessDeniedException.class).verify();

            verifyNoInteractions(delegate);
        }

        @Test
        @DisplayName("aggregate proceeds untouched when no obligation is in scope")
        void givenNoObligationWhenAggregateThenProceeds() {
            val pipeline = Aggregation.newAggregation(Aggregation.match(Criteria.where("name").is("Vimes")));
            when(delegate.aggregate(any(Aggregation.class), eq(Citizen.class), eq(Citizen.class)))
                    .thenReturn(Flux.empty());

            StepVerifier.create(shimmedTemplate.aggregate(pipeline, Citizen.class, Citizen.class).then())
                    .verifyComplete();

            verify(delegate).aggregate(any(Aggregation.class), eq(Citizen.class), eq(Citizen.class));
        }

        @Test
        @DisplayName("direct findById is denied while a narrowing obligation is in scope, without touching the delegate")
        void givenObligationWhenFindByIdThenAccessDenied() {
            val decision = permitWithObligation("""
                    {
                      "type": "mongo:queryRewriting",
                      "criteria": [{"column": "tenantId", "op": "=", "value": 7}]
                    }
                    """);
            val plan     = planner.plan(decision, Set.<SignalType>of(MongoDbQueryShimSignal.SIGNAL_TYPE));

            StepVerifier
                    .create(EnforcementPlanContext.withReactor(plan,
                            shimmedTemplate.findById("id-1", Citizen.class).then()))
                    .expectError(AccessDeniedException.class).verify();

            verifyNoInteractions(delegate);
        }

        @Test
        @DisplayName("direct findById proceeds untouched when no obligation is in scope")
        void givenNoObligationWhenFindByIdThenProceeds() {
            when(delegate.findById("id-1", Citizen.class)).thenReturn(Mono.empty());

            StepVerifier.create(shimmedTemplate.findById("id-1", Citizen.class).then()).verifyComplete();

            verify(delegate).findById("id-1", Citizen.class);
        }
    }

    @Nested
    @DisplayName("Obligation application is pure across repeated subscriptions of a cold publisher")
    class RepeatedSubscriptionPurity {

        @Test
        @DisplayName("Resubscribing the same narrowed find publisher narrows again cleanly, never double-applying or failing")
        void givenObligationWhenFindPublisherResubscribedThenEachSubscriptionNarrowsIndependently() {
            val decision = permitWithObligation("""
                    {
                      "type": "mongo:queryRewriting",
                      "criteria": [{"column": "tenantId", "op": "=", "value": 7}]
                    }
                    """);
            val plan     = planner.plan(decision, Set.<SignalType>of(MongoDbQueryShimSignal.SIGNAL_TYPE));

            when(delegate.find(any(Query.class), eq(Citizen.class))).thenReturn(Flux.empty());

            val originalQuery = new Query(Criteria.where("name").is("Vimes"));
            val callChain     = shimmedTemplate.find(originalQuery, Citizen.class);

            StepVerifier.create(EnforcementPlanContext.withReactor(plan, callChain.then())).verifyComplete();
            StepVerifier.create(EnforcementPlanContext.withReactor(plan, callChain.then())).verifyComplete();

            val captor = ArgumentCaptor.forClass(Query.class);
            verify(delegate, times(2)).find(captor.capture(), eq(Citizen.class));
            assertThat(captor.getAllValues()).hasSize(2).allSatisfy(
                    query -> assertThat(query.getQueryObject().toJson()).contains("Vimes").contains("\"tenantId\": 7"));
            assertThat(originalQuery.getQueryObject().toJson()).contains("Vimes").doesNotContain("tenantId");
        }
    }

    record Citizen(String id, String name, Integer tenantId) {}
}
