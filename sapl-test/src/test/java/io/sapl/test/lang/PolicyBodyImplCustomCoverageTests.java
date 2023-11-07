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
package io.sapl.test.lang;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.HashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.interpreter.DocumentEvaluationResult;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.test.coverage.api.CoverageHitRecorder;
import io.sapl.test.coverage.api.model.PolicyConditionHit;
import reactor.test.StepVerifier;

class PolicyBodyImplCustomCoverageTests {

    CoverageHitRecorder recorder;

    private SAPLInterpreter INTERPRETER;

    @BeforeEach
    void setup() {
        this.recorder    = mock(CoverageHitRecorder.class);
        this.INTERPRETER = new TestSaplInterpreter(this.recorder);
    }

    @Test
    void trueReturnsEntitlement() {
        var policy   = INTERPRETER.parse("policy \"p\" permit true where true; true; true;");
        var expected = AuthorizationDecision.PERMIT;
        StepVerifier
                .create(policy.evaluate().map(DocumentEvaluationResult::getAuthorizationDecision).contextWrite(ctx -> {
                    ctx = AuthorizationContext.setAttributeContext(ctx, new AnnotationAttributeContext());
                    ctx = AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext());
                    ctx = AuthorizationContext.setVariables(ctx, new HashMap<>());
                    return ctx;
                })).expectNext(expected).verifyComplete();

        ArgumentCaptor<PolicyConditionHit> captor = ArgumentCaptor.forClass(PolicyConditionHit.class);
        verify(this.recorder, times(3)).recordPolicyConditionHit(captor.capture());
        assertThat(captor.getAllValues().get(0).getConditionStatementId()).isZero();
        assertThat(captor.getAllValues().get(1).getConditionStatementId()).isEqualTo(1);
        assertThat(captor.getAllValues().get(2).getConditionStatementId()).isEqualTo(2);
    }

    @Test
    void trueReturnsEntitlementInSet() {
        var policy   = INTERPRETER.parse("set \"set\" deny-overrides policy \"p\" permit true where true; true; true;");
        var expected = AuthorizationDecision.PERMIT;
        StepVerifier
                .create(policy.evaluate().map(DocumentEvaluationResult::getAuthorizationDecision).contextWrite(ctx -> {
                    ctx = AuthorizationContext.setAttributeContext(ctx, new AnnotationAttributeContext());
                    ctx = AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext());
                    ctx = AuthorizationContext.setVariables(ctx, new HashMap<>());
                    return ctx;
                })).expectNext(expected).verifyComplete();

        ArgumentCaptor<PolicyConditionHit> captor = ArgumentCaptor.forClass(PolicyConditionHit.class);
        verify(this.recorder, times(3)).recordPolicyConditionHit(captor.capture());
        assertThat(captor.getAllValues().get(0).getConditionStatementId()).isZero();
        assertThat(captor.getAllValues().get(1).getConditionStatementId()).isEqualTo(1);
        assertThat(captor.getAllValues().get(2).getConditionStatementId()).isEqualTo(2);
    }

    @Test
    void test_evaluateConditionThrowsError() {
        var policy   = INTERPRETER.parse(
                "set \"set\" deny-overrides policy \"p\" permit true where true == subject.<pip.attr>; true; true;");
        var expected = AuthorizationDecision.INDETERMINATE;
        StepVerifier
                .create(policy.evaluate().map(DocumentEvaluationResult::getAuthorizationDecision).contextWrite(ctx -> {
                    ctx = AuthorizationContext.setAttributeContext(ctx, new AnnotationAttributeContext());
                    ctx = AuthorizationContext.setFunctionContext(ctx, new AnnotationFunctionContext());
                    ctx = AuthorizationContext.setVariables(ctx, new HashMap<>());
                    return ctx;
                })).expectNext(expected).verifyComplete();
        verify(this.recorder, never()).recordPolicyConditionHit(any());
    }

}
