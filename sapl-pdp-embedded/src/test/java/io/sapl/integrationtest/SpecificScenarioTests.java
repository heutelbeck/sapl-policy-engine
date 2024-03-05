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
package io.sapl.integrationtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.functions.StandardFunctionLibrary;
import io.sapl.functions.TemporalFunctionLibrary;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.combinators.CombiningAlgorithmFactory;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.pdp.EmbeddedPolicyDecisionPoint;
import io.sapl.pdp.config.PDPConfiguration;
import io.sapl.pdp.config.PDPConfigurationProvider;
import io.sapl.pdp.interceptors.ReportingDecisionInterceptor;
import io.sapl.pip.TimePolicyInformationPoint;
import io.sapl.prp.MatchingDocument;
import io.sapl.prp.PolicyRetrievalPoint;
import io.sapl.prp.PolicyRetrievalResult;
import lombok.SneakyThrows;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class SpecificScenarioTests {

    private final static ObjectMapper MAPPER                = new ObjectMapper();
    private final static String       BOOK3                 = """
            "resource": {
              "id": 3,
              "name": "Dragonlance Chronicles Vol. 1: Dragons of Autumn Twilight",
              "ageRating": 9,
              "content": "Some fantasy story."
            },
            """;
    private final static String       BOOK4                 = """
            "resource":{
              "id":4,
              "name":"The Three-Body Problem",
              "ageRating":14,
              "content":"Space is scary."
            },
            """;
    private final static String       SUBSCRIPTION_TEMPLATE = """
            {
              "subject": {
                "username": "%s",
                "birthday" : "%s"
              },
              "action": "read book",
              %s
              "environment": null
            }""";
    private final static String       SUB_BOB_BOOK3         = String.format(SUBSCRIPTION_TEMPLATE, "bob",
            birthdayForAgeInYears(10), BOOK3);
    private final static String       SUB_BOB_BOOK4         = String.format(SUBSCRIPTION_TEMPLATE, "bob",
            birthdayForAgeInYears(10), BOOK4);

    @Test
    void tutorialAgeCheckScenario() throws JsonProcessingException {
        var policySet = """
                import time.*
                import filter.*

                set "check age set"
                first-applicable
                for action == "read book"
                var birthday    = subject.birthday;
                var today       = dateOf(|<now>);
                var age         = timeBetween(birthday, today, "years");

                policy "check age transform set"
                permit
                where
                    age < resource.ageRating;
                obligation {
                            "type": "logAccess",
                            "message": "Attention, "+subject.username+" accessed the book '"+resource.name+"'."
                           }
                transform
                    resource |- {
                        @.content : blacken(3,0,"\u2588")
                    }

                policy "check age compact set"
                permit
                    age >= resource.ageRating
                """;

        var subBobBook3 = MAPPER.readValue(SUB_BOB_BOOK3, AuthorizationSubscription.class);
        var subBobBook4 = MAPPER.readValue(SUB_BOB_BOOK4, AuthorizationSubscription.class);

        var pdpConfigurationProvider = new SimplePDPConfigurationProvider();
        var policyRetrievalPoint     = new SimplePolicyRetrievalPoint();
        policyRetrievalPoint.load(policySet);
        var pdp = new EmbeddedPolicyDecisionPoint(pdpConfigurationProvider, policyRetrievalPoint);

        var decisionBook3 = pdp.decide(subBobBook3).blockFirst();
        assertThat(decisionBook3.getObligations()).isEmpty();
        var decisionBook4 = pdp.decide(subBobBook4).blockFirst();
        assertThat(decisionBook4.getObligations()).isNotEmpty();
    }

    private static class SimplePDPConfigurationProvider implements PDPConfigurationProvider {

        @Override
        @SneakyThrows
        public Flux<PDPConfiguration> pdpConfiguration() {
            var attributeContext = new AnnotationAttributeContext();
            var timePip          = new TimePolicyInformationPoint(Clock.systemDefaultZone());
            attributeContext.loadPolicyInformationPoint(timePip);
            var functionContext = new AnnotationFunctionContext();
            functionContext.loadLibrary(FilterFunctionLibrary.class);
            functionContext.loadLibrary(TemporalFunctionLibrary.class);
            functionContext.loadLibrary(StandardFunctionLibrary.class);
            var variables                    = Map.<String, Val>of();
            var algorithm                    = CombiningAlgorithmFactory
                    .getCombiningAlgorithm(PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES);
            var jsonReportingInterceptor     = new ReportingDecisionInterceptor(MAPPER, false, true, true, true);
            var subscriptionInterceptorChain = UnaryOperator.<AuthorizationSubscription>identity();
            var config                       = new PDPConfiguration(attributeContext, functionContext, variables,
                    algorithm, jsonReportingInterceptor, subscriptionInterceptorChain);
            return Flux.just(config);

        }

    }

    private static String birthdayForAgeInYears(int age) {
        return LocalDate.now().minusYears(age).minusDays(20).toString();
    }

    private static class SimplePolicyRetrievalPoint implements PolicyRetrievalPoint {

        private final static DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

        private final List<SAPL> parsedDocuments = new ArrayList<>();

        public void load(String document) {
            parsedDocuments.add(INTERPRETER.parse(document));
        }

        @Override
        public Flux<PolicyRetrievalResult> retrievePolicies() {
            return Flux.fromIterable(parsedDocuments)
                    .flatMap(sapl -> Mono.just(sapl)
                            .filterWhen(s -> s.matches().map(match -> match.isBoolean() && match.getBoolean())))
                    .map(m -> new MatchingDocument(m.getPolicyElement().getSaplName(), m, Val.TRUE)).collectList()
                    .map(matches -> new PolicyRetrievalResult(matches, false, true))
                    .onErrorResume(err -> Mono.just(new PolicyRetrievalResult(List.of(), true, false))).flux();
        }

    }

}
