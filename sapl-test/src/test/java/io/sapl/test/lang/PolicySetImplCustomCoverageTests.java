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
package io.sapl.test.lang;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.attributes.broker.impl.CachingAttributeStreamBroker;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.test.coverage.api.CoverageHitRecorder;
import io.sapl.test.coverage.api.model.PolicyHit;
import io.sapl.test.coverage.api.model.PolicySetHit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.*;

class PolicySetImplCustomCoverageTests {

    CoverageHitRecorder recorder;

    private SAPLInterpreter interpreter;

    @BeforeEach
    void setup() {
        this.recorder    = mock(CoverageHitRecorder.class);
        this.interpreter = new TestSaplInterpreter(this.recorder);
    }

    @Test
    void test_match() {
        final var policy   = interpreter.parse("""
                set "set"

                deny-overrides

                for action == "read"

                policy "set.p1"
                permit
                """);
        final var authzSub = AuthorizationSubscription.of("willi", "read", "something");
        assertThat(policy.matches().contextWrite(ctx -> {
            ctx = AuthorizationContext.setAttributeStreamBroker(ctx, new CachingAttributeStreamBroker());
            ctx = AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext());
            ctx = AuthorizationContext.setVariables(ctx, new HashMap<>());
            ctx = AuthorizationContext.setSubscriptionVariables(ctx, authzSub);
            return ctx;
        }).block().getBoolean()).isTrue();
        verify(this.recorder, times(1)).recordPolicySetHit(isA(PolicySetHit.class));
    }

    @Test
    void test_NotMatching() {
        final var policy   = interpreter.parse("""
                set "set"

                deny-overrides

                for action == "read"

                policy "set.p1"
                permit
                """);
        final var authzSub = AuthorizationSubscription.of("willi", "write", "something");
        assertThat(policy.matches().contextWrite(ctx -> {
            ctx = AuthorizationContext.setAttributeStreamBroker(ctx, new CachingAttributeStreamBroker());
            ctx = AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext());
            ctx = AuthorizationContext.setVariables(ctx, new HashMap<>());
            ctx = AuthorizationContext.setSubscriptionVariables(ctx, authzSub);
            return ctx;
        }).block().getBoolean()).isFalse();
        verify(this.recorder, never()).recordPolicyHit(isA(PolicyHit.class));
    }

    @Test
    void test_matchesThrowsError() {
        final var policy   = interpreter.parse("""
                set "set"

                deny-overrides

                for action == 1/0

                policy "set.p1"
                permit
                """);
        final var authzSub = AuthorizationSubscription.of("willi", "write", "something");
        assertThat(policy.matches().contextWrite(ctx -> {
            ctx = AuthorizationContext.setAttributeStreamBroker(ctx, new CachingAttributeStreamBroker());
            ctx = AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext());
            ctx = AuthorizationContext.setVariables(ctx, new HashMap<>());
            ctx = AuthorizationContext.setSubscriptionVariables(ctx, authzSub);
            return ctx;
        }).block().isBoolean()).isFalse();
        verify(this.recorder, never()).recordPolicyHit(isA(PolicyHit.class));
    }

}
