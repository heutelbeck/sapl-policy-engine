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
package io.sapl.pdp;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.grammar.sapl.CombiningAlgorithm;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.pdp.config.PDPConfiguration;
import io.sapl.pdp.config.PDPConfigurationProvider;
import io.sapl.pdp.config.filesystem.FileSystemVariablesAndCombinatorSource;
import io.sapl.pdp.config.fixed.FixedFunctionsAndAttributesPDPConfigurationProvider;
import io.sapl.pdp.interceptors.ReportingDecisionInterceptor;
import io.sapl.prp.PolicyRetrievalPoint;
import io.sapl.prp.PolicyRetrievalPointSource;
import io.sapl.prp.PolicyRetrievalResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class EmbeddedPolicyDecisionPointTests {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private PolicyDecisionPoint pdp;

    @BeforeEach
    void setUp() throws Exception {
        final var reporter = new ReportingDecisionInterceptor(new ObjectMapper(), true, true, true, true);
        pdp = PolicyDecisionPointFactory.resourcesPolicyDecisionPoint("/policies", () -> List.of(new TestPIP()),
                List::of, List::of, List::of, List.of(), List.of(reporter));
    }

    @Test
    void decide_withInvalidConfig_shouldReturnIntermediate() {
        final var prp          = mock(PolicyRetrievalPoint.class);
        final var brokenConfig = new PDPConfiguration("", mock(), mock(), Map.of(),
                PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, UnaryOperator.identity(), UnaryOperator.identity(),
                prp);
        final var providerMock = mock(PDPConfigurationProvider.class);
        final var embeddedPdp  = new EmbeddedPolicyDecisionPoint(providerMock);

        when(providerMock.pdpConfiguration()).thenReturn(Flux.just(brokenConfig));
        when(prp.isConsistent()).thenReturn(Boolean.FALSE);

        final var empty = new AuthorizationSubscription(JSON.nullNode(), JSON.nullNode(), JSON.nullNode(),
                JSON.nullNode());

        StepVerifier.create(embeddedPdp.decide(empty))
                .expectNextMatches(authzDecision -> authzDecision.getDecision() == Decision.INDETERMINATE).thenCancel()
                .verify();

        embeddedPdp.destroy();

        verify(providerMock, times(1)).destroy();
    }

    @Test
    void decide_withEmptyRequest_shouldBeDenyForDenyUnlessPermitAlgorithm() {
        AuthorizationSubscription         emptyAuthzSubscription = new AuthorizationSubscription(JSON.nullNode(),
                JSON.nullNode(), JSON.nullNode(), JSON.nullNode());
        final Flux<AuthorizationDecision> authzDecisionFlux      = pdp.decide(emptyAuthzSubscription);
        StepVerifier.create(authzDecisionFlux)
                .expectNextMatches(authzDecision -> authzDecision.getDecision() == Decision.DENY).thenCancel().verify();
    }

    @Test
    void decide_withAllowedAction_shouldReturnPermit() {
        AuthorizationSubscription         simpleAuthzSubscription = new AuthorizationSubscription(
                JSON.textNode("willi"), JSON.textNode("read"), JSON.textNode("something"), JSON.nullNode());
        final Flux<AuthorizationDecision> authzDecisionFlux       = pdp.decide(simpleAuthzSubscription);
        StepVerifier.create(authzDecisionFlux)
                .expectNextMatches(authzDecision -> authzDecision.getDecision() == Decision.PERMIT).thenCancel()
                .verify();
    }

    @Test
    void decide_withInvalidPrpState_shouldReturnIntermediate() {
        final var prpMock   = mock(PolicyRetrievalPoint.class);
        final var prpResult = mock(PolicyRetrievalResult.class);
        final var prpSource = mock(PolicyRetrievalPointSource.class);

        final var source   = new FileSystemVariablesAndCombinatorSource("src/test/resources/policies");
        final var attrCtx  = new AnnotationAttributeContext();
        final var funcCtx  = new AnnotationFunctionContext();
        final var provider = new FixedFunctionsAndAttributesPDPConfigurationProvider(attrCtx, funcCtx, source,
                List.of(), List.of(), prpSource);

        final var embeddedPdp = new EmbeddedPolicyDecisionPoint(provider);

        when(prpSource.policyRetrievalPoint()).thenReturn(Flux.just(prpMock));
        when(prpMock.retrievePolicies()).thenReturn(Mono.just(prpResult));
        when(prpResult.isPrpInconsistent()).thenReturn(Boolean.TRUE);

        final var simpleAuthzSubscription = new AuthorizationSubscription(JSON.textNode("willi"), JSON.textNode("read"),
                JSON.textNode("something"), JSON.nullNode());
        final var authzDecisionFlux       = embeddedPdp.decide(simpleAuthzSubscription);
        StepVerifier.create(authzDecisionFlux)
                .expectNextMatches(authzDecision -> authzDecision.getDecision() == Decision.INDETERMINATE).thenCancel()
                .verify();
    }

    @Test
    void decide_withForbiddenAction_shouldReturnDeny() {
        AuthorizationSubscription         simpleAuthzSubscription = new AuthorizationSubscription(
                JSON.textNode("willi"), JSON.textNode("write"), JSON.textNode("something"), JSON.nullNode());
        final Flux<AuthorizationDecision> authzDecisionFlux       = pdp.decide(simpleAuthzSubscription);
        StepVerifier.create(authzDecisionFlux)
                .expectNextMatches(authzDecision -> authzDecision.getDecision() == Decision.DENY).thenCancel().verify();
    }

    @Test
    void decide_withEmptyMultiSubscription_shouldReturnIndeterminate() {
        final MultiAuthorizationSubscription multiAuthzSubscription = new MultiAuthorizationSubscription();

        final Flux<IdentifiableAuthorizationDecision> flux = pdp.decide(multiAuthzSubscription);
        StepVerifier.create(flux)
                .expectNextMatches(iad -> iad.getAuthorizationSubscriptionId() == null
                        && iad.getAuthorizationDecision().equals(AuthorizationDecision.INDETERMINATE))
                .thenCancel().verify();
    }

    @Test
    void decideAll_withEmptyMultiSubscription_shouldReturnIndeterminate() {
        final MultiAuthorizationSubscription multiAuthzSubscription = new MultiAuthorizationSubscription();

        final Flux<MultiAuthorizationDecision> flux = pdp.decideAll(multiAuthzSubscription);
        StepVerifier.create(flux).expectNextMatches(
                mad -> mad.getAuthorizationDecisionForSubscriptionWithId("").getDecision() == Decision.INDETERMINATE)
                .thenCancel().verify();
    }

    @Test
    void decide_withMultiSubscription_shouldReturnDecision() {
        final MultiAuthorizationSubscription multiAuthzSubscription = new MultiAuthorizationSubscription()
                .addAuthorizationSubscription("id", JSON.textNode("willi"), JSON.textNode("read"),
                        JSON.textNode("something"));

        final Flux<IdentifiableAuthorizationDecision> flux = pdp.decide(multiAuthzSubscription);
        StepVerifier.create(flux).expectNextMatches(iad -> "id".equals(iad.getAuthorizationSubscriptionId())
                && iad.getAuthorizationDecision().equals(AuthorizationDecision.PERMIT)).thenCancel().verify();
    }

    @Test
    void decide_withMultiSubscriptionContainingTwoSubscriptions_shouldReturnTwoDecisions() {
        final MultiAuthorizationSubscription multiAuthzSubscription = new MultiAuthorizationSubscription()
                .addAuthorizationSubscription("id1", JSON.textNode("willi"), JSON.textNode("read"),
                        JSON.textNode("something"))
                .addAuthorizationSubscription("id2", JSON.textNode("willi"), JSON.textNode("write"),
                        JSON.textNode("something"));

        final Flux<IdentifiableAuthorizationDecision> flux = pdp.decide(multiAuthzSubscription);
        StepVerifier.create(flux).expectNextMatches(iad -> {
            if ("id1".equals(iad.getAuthorizationSubscriptionId())) {
                return iad.getAuthorizationDecision().equals(AuthorizationDecision.PERMIT);
            } else if ("id2".equals(iad.getAuthorizationSubscriptionId())) {
                return iad.getAuthorizationDecision().equals(AuthorizationDecision.DENY);
            } else {
                throw new IllegalStateException("Invalid subscription id: " + iad.getAuthorizationSubscriptionId());
            }
        }).expectNextMatches(iad -> {
            if ("id1".equals(iad.getAuthorizationSubscriptionId())) {
                return iad.getAuthorizationDecision().equals(AuthorizationDecision.PERMIT);
            } else if ("id2".equals(iad.getAuthorizationSubscriptionId())) {
                return iad.getAuthorizationDecision().equals(AuthorizationDecision.DENY);
            } else {
                throw new IllegalStateException("Invalid subscription id: " + iad.getAuthorizationSubscriptionId());
            }
        }).thenCancel().verify();
    }

    @Test
    void decideAll_withMultiSubscriptionContainingTwoSubscriptions_shouldReturnTwoDecisions() {
        final MultiAuthorizationSubscription multiAuthzSubscription = new MultiAuthorizationSubscription()
                .addAuthorizationSubscription("id1", JSON.textNode("willi"), JSON.textNode("read"),
                        JSON.textNode("something"))
                .addAuthorizationSubscription("id2", JSON.textNode("willi"), JSON.textNode("write"),
                        JSON.textNode("something"));

        final Flux<MultiAuthorizationDecision> flux = pdp.decideAll(multiAuthzSubscription);
        StepVerifier.create(flux).expectNextMatches(mad -> {
            AuthorizationDecision ad1 = mad.getAuthorizationDecisionForSubscriptionWithId("id1");
            AuthorizationDecision ad2 = mad.getAuthorizationDecisionForSubscriptionWithId("id2");
            if (ad1 == null || ad2 == null)
                return false;

            if (ad1.getDecision() != Decision.PERMIT)
                return false;
            return ad2.getDecision() == Decision.DENY;
        }).thenCancel().verify();
    }

    @Test
    void when_invalidPDPConfiguration_then_returnError1() {
        final var prp            = mock(PolicyRetrievalPoint.class);
        final var configProvider = mock(PDPConfigurationProvider.class);
        final var brokenConfig   = new PDPConfiguration("", mock(), mock(), Map.of(),
                PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, UnaryOperator.identity(), UnaryOperator.identity(),
                prp);
        when(configProvider.pdpConfiguration()).thenReturn(Flux.just(brokenConfig));
        final var subscription = new AuthorizationSubscription(JSON.textNode("willi"), JSON.textNode("read"),
                JSON.textNode("something"), JSON.nullNode());
        final var sut          = new EmbeddedPolicyDecisionPoint(configProvider);
        StepVerifier.create(sut.decide(subscription))
                .expectNextMatches(combinedDecision -> combinedDecision.getDecision() == Decision.INDETERMINATE)
                .verifyComplete();
    }

    @Test
    void when_invalidPDPConfiguration_then_returnError2() {
        final var prp            = mock(PolicyRetrievalPoint.class);
        final var configProvider = mock(PDPConfigurationProvider.class);
        final var mockAlgorithm  = mock(CombiningAlgorithm.class);
        when(mockAlgorithm.getName()).thenReturn("test alg");
        final var brokenConfig = new PDPConfiguration("", mock(), mock(), Map.of(),
                PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, UnaryOperator.identity(), UnaryOperator.identity(),
                prp);
        when(configProvider.pdpConfiguration()).thenReturn(Flux.just(brokenConfig));
        final var subscription = new AuthorizationSubscription(JSON.textNode("willi"), JSON.textNode("read"),
                JSON.textNode("something"), JSON.nullNode());
        final var sut          = new EmbeddedPolicyDecisionPoint(configProvider);
        StepVerifier.create(sut.decide(subscription))
                .expectNextMatches(combinedDecision -> combinedDecision.getDecision() == Decision.INDETERMINATE)
                .verifyComplete();
    }

    @Test
    void when_errorsInTarget_then_returnError() {
        final var prp              = mock(PolicyRetrievalPoint.class);
        final var configProvider   = mock(PDPConfigurationProvider.class);
        final var mockAlgorithm    = PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES;
        final var attributeContext = mock(AttributeContext.class);
        final var functionContext  = mock(FunctionContext.class);
        final var validConfig      = new PDPConfiguration("", attributeContext, functionContext, Map.of(),
                mockAlgorithm, UnaryOperator.identity(), UnaryOperator.identity(), prp);
        when(configProvider.pdpConfiguration()).thenReturn(Flux.just(validConfig));

        final var retrievalResult = mock(PolicyRetrievalResult.class);
        when(retrievalResult.isPrpInconsistent()).thenReturn(Boolean.FALSE);
        when(retrievalResult.isRetrievalWithErrors()).thenReturn(Boolean.TRUE);
        when(prp.retrievePolicies()).thenReturn(Mono.just(retrievalResult));

        final var subscription = new AuthorizationSubscription(JSON.textNode("willi"), JSON.textNode("read"),
                JSON.textNode("something"), JSON.nullNode());
        final var sut          = new EmbeddedPolicyDecisionPoint(configProvider);
        StepVerifier.create(sut.decide(subscription))
                .expectNextMatches(combinedDecision -> combinedDecision.getDecision() == Decision.INDETERMINATE)
                .verifyComplete();
    }
}
