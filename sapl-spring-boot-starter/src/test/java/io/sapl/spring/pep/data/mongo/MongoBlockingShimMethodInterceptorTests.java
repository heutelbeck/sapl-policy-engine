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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.ExecutableFindOperation.FindWithQuery;
import org.springframework.data.mongodb.core.ExecutableUpdateOperation.FindAndReplaceWithProjection;
import org.springframework.data.mongodb.core.ExecutableUpdateOperation.UpdateWithQuery;
import org.springframework.data.mongodb.core.ExecutableUpdateOperation.UpdateWithUpdate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.spring.pep.constraints.EnforcementPlan;
import io.sapl.spring.pep.constraints.EnforcementPlanContext;
import io.sapl.spring.pep.constraints.EnforcementPlanner;
import io.sapl.spring.pep.constraints.Signal.MongoDbQueryShimSignal;
import io.sapl.spring.pep.constraints.SignalType;
import io.sapl.spring.pep.constraints.providers.MongoDbQueryRewritingProvider;
import lombok.val;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Unit tests for the blocking Mongo shim's fluent-find wrapper. A single fluent
 * builder is a value an operator may legitimately reuse for several terminals
 * (for example {@code count()} and then {@code all()} on the same
 * {@code query(X).matching(criteria)} stage). The shim must narrow each
 * terminal
 * independently and must never let the obligation corrupt the captured query so
 * a later terminal fails. The mock template records the query each terminal
 * actually runs, so the assertions are on the narrowed {@link Query}.
 */
@DisplayName("Mongo blocking shim fluent-find wrapper")
@ExtendWith(MockitoExtension.class)
class MongoBlockingShimMethodInterceptorTests {

    private static final ObjectMapper MAPPER = JsonMapper.builder().addModule(new SaplJacksonModule()).build();

    private static final String TENANT_OBLIGATION = """
            {
              "type": "mongo:queryRewriting",
              "criteria": [{"column": "tenantId", "op": "=", "value": 7}]
            }
            """;

    @Mock
    private FindWithQuery<Object> delegate;

    @Mock
    private UpdateWithQuery<Object> updateBase;

    @Mock
    private FindAndReplaceWithProjection<Object> replaceProjection;

    private static Value v(String json) {
        return MAPPER.readValue(json, Value.class);
    }

    private static EnforcementPlan tenantNarrowingPlan() {
        val obligations = ArrayValue.builder().add(v(TENANT_OBLIGATION)).build();
        val decision    = new AuthorizationDecision(Decision.PERMIT, obligations, Value.EMPTY_ARRAY, Value.UNDEFINED);
        val planner     = new EnforcementPlanner(List.of(new MongoDbQueryRewritingProvider()), MAPPER);
        return planner.plan(decision, Set.<SignalType>of(MongoDbQueryShimSignal.SIGNAL_TYPE));
    }

    private static Object wrapTerminatingFind(FindWithQuery<?> delegate, Query capturedQuery) throws Exception {
        final Method wrap = MongoBlockingShimMethodInterceptor.class.getDeclaredMethod("wrapTerminatingFind",
                FindWithQuery.class, Query.class);
        wrap.setAccessible(true);
        return wrap.invoke(null, delegate, capturedQuery);
    }

    private static Object wrapUpdate(Object base, Query capturedQuery, UpdateDefinition capturedUpdate)
            throws Exception {
        final Method wrap = MongoBlockingShimMethodInterceptor.class.getDeclaredMethod("wrapUpdate", Object.class,
                Query.class, UpdateDefinition.class);
        wrap.setAccessible(true);
        return wrap.invoke(null, base, capturedQuery, capturedUpdate);
    }

    @Test
    @DisplayName("Reusing the same captured builder for a second terminal narrows independently instead of double-applying the obligation")
    void givenObligationWhenSecondTerminalOnSameBuilderThenNarrowedIndependently() throws Exception {
        when(delegate.matching(any(Query.class))).thenReturn(delegate);
        when(delegate.all()).thenReturn(List.of());

        @SuppressWarnings("unchecked")
        val fluent = (FindWithQuery<Object>) wrapTerminatingFind(delegate, new Query());

        assertThatCode(() -> EnforcementPlanContext.withBlocking(tenantNarrowingPlan(), () -> {
            fluent.all();
            fluent.all();
            return null;
        })).doesNotThrowAnyException();

        verify(delegate, times(2)).all();
        val captor = ArgumentCaptor.forClass(Query.class);
        verify(delegate, times(3)).matching(captor.capture());
        assertThat(captor.getAllValues().subList(1, 3))
                .allSatisfy(query -> assertThat(query.getQueryObject().toJson()).contains("tenantId"));
    }

    @Test
    @DisplayName("replaceWith carries the captured query into the matched stage so the replacement targets only matched documents, never the whole collection")
    void givenCapturedQueryWhenReplaceWithThenQueryAppliedBeforeReplace() throws Exception {
        val capturedQuery = Query.query(where("tenantId").is(7));
        val replacement   = new Object();
        when(updateBase.matching(capturedQuery)).thenReturn(updateBase);
        when(updateBase.replaceWith(replacement)).thenReturn(replaceProjection);

        @SuppressWarnings("unchecked")
        val fluent = (UpdateWithUpdate<Object>) wrapUpdate(updateBase, capturedQuery, null);
        fluent.replaceWith(replacement);

        verify(updateBase).matching(capturedQuery);
        verify(updateBase).replaceWith(replacement);
    }
}
