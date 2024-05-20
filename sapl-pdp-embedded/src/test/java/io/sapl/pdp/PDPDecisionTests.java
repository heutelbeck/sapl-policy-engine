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
package io.sapl.pdp;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Trace;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.TracedDecision;
import io.sapl.interpreter.CombinedDecision;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.prp.DocumentMatch;
import io.sapl.prp.PolicyRetrievalResult;

class PDPDecisionTests {
    private static final SAPLInterpreter INTERPERTER = new DefaultSAPLInterpreter();

    @Test
    void constructor() {
        var subscription = mock(AuthorizationSubscription.class);
        var combined     = mock(CombinedDecision.class);

        var sut = PDPDecision.of(subscription, combined);
        assertThat(sut.getAuthorizationSubscription()).isSameAs(subscription);
        assertThat(sut.getCombinedDecision()).isSameAs(combined);

        var document  = INTERPERTER.parseDocument("policy \"x\" permit");
        var match     = new DocumentMatch(document, Val.TRUE);
        var prpResult = new PolicyRetrievalResult().withMatch(match);
        var sut2      = PDPDecision.of(subscription, combined, prpResult);
        assertThat(sut2.getAuthorizationSubscription()).isSameAs(subscription);
        assertThat(sut2.getCombinedDecision()).isSameAs(combined);
        assertThat(sut2.getPrpResult().getMatchingDocuments()).hasSize(1);
    }

    @Test
    void getAuthorizationDecision() {
        var subscription = mock(AuthorizationSubscription.class);
        var combined     = mock(CombinedDecision.class);
        when(combined.getAuthorizationDecision()).thenReturn(AuthorizationDecision.PERMIT);
        var sut = PDPDecision.of(subscription, combined);
        assertThat(sut.getAuthorizationDecision()).isEqualTo(AuthorizationDecision.PERMIT);

        var sut2 = sut.modified(AuthorizationDecision.DENY, "for testing reasons");
        assertThat(sut2.getAuthorizationDecision()).isEqualTo(AuthorizationDecision.DENY);
    }

    @Test
    void getTrace() {
        var subscription = mock(AuthorizationSubscription.class);
        var combined     = mock(CombinedDecision.class);
        var document     = INTERPERTER.parseDocument("policy \"x\" permit");
        var match        = new DocumentMatch(document, Val.TRUE);
        when(combined.getAuthorizationDecision()).thenReturn(AuthorizationDecision.PERMIT);
        var            prpResult       = new PolicyRetrievalResult().withMatch(match);
        TracedDecision sut             = PDPDecision.of(subscription, combined, prpResult);
        var            unmodifiedTrace = sut.getTrace();
        assertThatJson(unmodifiedTrace).inPath("$." + Trace.OPERATOR).isEqualTo("Policy Decision Point");
        assertThatJson(unmodifiedTrace).inPath("$." + Trace.MODIFICATIONS).isAbsent();
        sut = sut.modified(AuthorizationDecision.DENY, "for testing reasons");
        var modifiedTrace = sut.getTrace();
        assertThatJson(modifiedTrace).inPath("$." + Trace.MODIFICATIONS).isArray().isNotEmpty();
    }

}
