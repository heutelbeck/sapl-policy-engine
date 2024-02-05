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
package io.sapl.test.lang;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.HashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.test.coverage.api.CoverageHitRecorder;
import io.sapl.test.coverage.api.model.PolicyHit;
import io.sapl.test.coverage.api.model.PolicySetHit;

class PolicySetImplCustomCoverageTests {

    CoverageHitRecorder recorder;

    private SAPLInterpreter INTERPRETER;

    @BeforeEach
    void setup() {
        this.recorder    = mock(CoverageHitRecorder.class);
        this.INTERPRETER = new TestSaplInterpreter(this.recorder);
    }

    @Test
    void test_match() {
        var policy   = INTERPRETER.parse("""
                set "set"

                deny-overrides

                for action == "read"

                policy "set.p1"
                permit
                """);
        var authzSub = AuthorizationSubscription.of("willi", "read", "something");
        assertThat(policy.matches().contextWrite(ctx -> {
            ctx = AuthorizationContext.setAttributeContext(ctx, new AnnotationAttributeContext());
            ctx = AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext());
            ctx = AuthorizationContext.setVariables(ctx, new HashMap<>());
            ctx = AuthorizationContext.setSubscriptionVariables(ctx, authzSub);
            return ctx;
        }).block().getBoolean()).isTrue();
        verify(this.recorder, times(1)).recordPolicySetHit(isA(PolicySetHit.class));
    }

    @Test
    void test_NotMatching() {
        var policy   = INTERPRETER.parse("""
                set "set"

                deny-overrides

                for action == "read"

                policy "set.p1"
                permit
                """);
        var authzSub = AuthorizationSubscription.of("willi", "write", "something");
        assertThat(policy.matches().contextWrite(ctx -> {
            ctx = AuthorizationContext.setAttributeContext(ctx, new AnnotationAttributeContext());
            ctx = AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext());
            ctx = AuthorizationContext.setVariables(ctx, new HashMap<>());
            ctx = AuthorizationContext.setSubscriptionVariables(ctx, authzSub);
            return ctx;
        }).block().getBoolean()).isFalse();
        verify(this.recorder, never()).recordPolicyHit(isA(PolicyHit.class));
    }

    @Test
    void test_matchesThrowsError() {
        var policy   = INTERPRETER.parse("""
                set "set"

                deny-overrides

                for action == 1/0

                policy "set.p1"
                permit
                """);
        var authzSub = AuthorizationSubscription.of("willi", "write", "something");
        assertThat(policy.matches().contextWrite(ctx -> {
            ctx = AuthorizationContext.setAttributeContext(ctx, new AnnotationAttributeContext());
            ctx = AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext());
            ctx = AuthorizationContext.setVariables(ctx, new HashMap<>());
            ctx = AuthorizationContext.setSubscriptionVariables(ctx, authzSub);
            return ctx;
        }).block().isBoolean()).isFalse();
        verify(this.recorder, never()).recordPolicyHit(isA(PolicyHit.class));
    }

}
