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
package io.sapl.pdp.interceptors;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.pdp.EmbeddedPolicyDecisionPoint;
import io.sapl.pdp.config.PDPConfiguration;
import io.sapl.pdp.config.PDPConfigurationProvider;
import io.sapl.prp.Document;
import io.sapl.prp.DocumentMatch;
import io.sapl.prp.PolicyRetrievalPoint;
import io.sapl.prp.PolicyRetrievalResult;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ReportingDecisionInterceptorTests {

    private static class TestingPolicyRetrievalPoint implements PolicyRetrievalPoint {
        private static final String          PERMIT_ALL_DOCUMENT = "policy \"permitAll\" permit";
        private static final String          ERROR_DOCUMENT      = "policy \"errAll\" permit where 1/0;";
        private static final String          ATTRIBUTE_DOCUMENT  = "policy \"attribute\" permit where \"left\".<test.test> == \"Attribute Result\";";
        private static final String          SET_ONE             = "set \"set one\" first-applicable policy \"p1 in set one\" permit";
        private static final SAPLInterpreter INTERPERTER         = new DefaultSAPLInterpreter();

        @Override
        public Mono<PolicyRetrievalResult> retrievePolicies() {
            final var permitAll = INTERPERTER.parseDocument(PERMIT_ALL_DOCUMENT);
            final var error     = INTERPERTER.parseDocument(ERROR_DOCUMENT);
            final var attribute = INTERPERTER.parseDocument(ATTRIBUTE_DOCUMENT);
            final var one       = INTERPERTER.parseDocument(SET_ONE);
            return Mono.just(new PolicyRetrievalResult().withMatch(new DocumentMatch(permitAll, Val.TRUE))
                    .withMatch(new DocumentMatch(error, Val.TRUE)).withMatch(new DocumentMatch(attribute, Val.TRUE))
                    .withMatch(new DocumentMatch(one, Val.TRUE)));
        }

        @Override
        public Collection<Document> allDocuments() {
            return List.of();
        }

        @Override
        public boolean isConsistent() {
            return true;
        }

    }

    @UtilityClass
    @PolicyInformationPoint(name = "test")
    static class ReportingTestPIP {

        @Attribute(name = "test")
        public Flux<Val> test(Val leftHand) {
            return Flux.just(Val.of("Attribute Result"));
        }
    }

    private static class TestingPDPConfigurationProvider implements PDPConfigurationProvider {

        @Override
        @SneakyThrows
        public Flux<PDPConfiguration> pdpConfiguration() {
            final var dInterceptor = new ReportingDecisionInterceptor(new ObjectMapper(), false, true, true, true);
            return Flux.just(new PDPConfiguration("testConfiguration",
                    new AnnotationAttributeContext(List::of, () -> List.of(ReportingTestPIP.class)),
                    new AnnotationFunctionContext(), Map.of(), PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES,
                    dInterceptor, x -> x, new TestingPolicyRetrievalPoint()));
        }
    }

    @Test
    void runReportingTest() {
        final var pdp = new EmbeddedPolicyDecisionPoint(new TestingPDPConfigurationProvider());
        final var sub = AuthorizationSubscription.of("subject", "action", "resource");
        StepVerifier.create(pdp.decide(sub)).expectNextMatches(d -> d.getDecision() == Decision.INDETERMINATE)
                .verifyComplete();
    }

}
