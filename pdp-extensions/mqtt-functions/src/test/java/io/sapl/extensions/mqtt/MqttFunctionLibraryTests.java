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
package io.sapl.extensions.mqtt;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.test.StepVerifier;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class MqttFunctionLibraryTests {

    private static final String ACTION = "actionName";

    @ParameterizedTest
    @ValueSource(strings = { "first/second/#", "first/+/third", "first/second/third" })
    void when_allTopicsShouldMatchAndMatching_then_returnTrue(String wildcardTopicString) {
        var wildcardTopic = Value.of(wildcardTopicString);
        var matchingTopic = Value.of("first/second/third");

        var result = MqttFunctionLibrary.isMatchingAllTopics(wildcardTopic, matchingTopic);

        assertThat(result).isEqualTo(Value.TRUE);
    }

    private static Stream<Arguments> paramsForTestAllTopicsShouldMatchAndTopicArrayMatchesWildcard() {
        return Stream.of(arguments("first/second/#", "first/second/fourth"),
                arguments("first/+/third", "first/fourth/third"),
                arguments("first/+/third/#", "first/last/third/fourth"));
    }

    @ParameterizedTest
    @MethodSource("paramsForTestAllTopicsShouldMatchAndTopicArrayMatchesWildcard")
    void when_allTopicsShouldMatchAndTopicArrayMatchesWildcard_then_returnTrue(String wildCardTopicString,
            String secondMatchingTopic) {
        var wildcardTopic  = Value.of(wildCardTopicString);
        var matchingTopics = Value.ofArray(Value.of("first/second/third"), Value.of(secondMatchingTopic));

        var result = MqttFunctionLibrary.isMatchingAllTopics(wildcardTopic, matchingTopics);

        assertThat(result).isEqualTo(Value.TRUE);
    }

    private static Stream<Arguments> paramsForTestAllTopicsShouldMatchAndSingleTopicDoesNotMatch() {
        return Stream.of(arguments("first/second/#", "first/third"), arguments("first/+/third", "first/third"),
                arguments("first/+/third/#", "first/third/fourth"));
    }

    @ParameterizedTest
    @MethodSource("paramsForTestAllTopicsShouldMatchAndSingleTopicDoesNotMatch")
    void when_allTopicsShouldMatchAndSingleTopicDoesNotMatch_then_returnFalse(String wildcardTopicString,
            String secondMatchingTopic) {
        var wildcardTopic  = Value.of(wildcardTopicString);
        var matchingTopics = Value.ofArray(Value.of("first/second/third"), Value.of(secondMatchingTopic));

        var result = MqttFunctionLibrary.isMatchingAllTopics(wildcardTopic, matchingTopics);

        assertThat(result).isEqualTo(Value.FALSE);
    }

    @Test
    void when_allTopicsShouldMatchButSpecifiedAWildcardInTopicToMatch_then_returnError() {
        var wildcardTopic = Value.of("first/second/#");
        var matchingTopic = Value.of("first/second/third/#");

        var result = MqttFunctionLibrary.isMatchingAtLeastOneTopic(wildcardTopic, matchingTopic);

        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    @Test
    void when_allTopicsShouldMatchButSpecifiedAWildcardInTopicsToMatch_then_returnError() {
        var wildcardTopic  = Value.of("first/second/#");
        var matchingTopics = Value.ofArray(Value.of("first/second/third"), Value.of("first/second/+/fourth"));

        var result = MqttFunctionLibrary.isMatchingAllTopics(wildcardTopic, matchingTopics);

        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "first/second/#", "first/+/third", "first/second/third" })
    void when_atLeastOneTopicShouldMatchAndMatching_then_returnTrue(String wildcardTopicString) {
        var wildcardTopic = Value.of(wildcardTopicString);
        var matchingTopic = Value.of("first/second/third");

        var result = MqttFunctionLibrary.isMatchingAtLeastOneTopic(wildcardTopic, matchingTopic);

        assertThat(result).isEqualTo(Value.TRUE);
    }

    private static Stream<Arguments> paramsForTestAtLeastOneTopicShouldMatch() {
        return Stream.of(arguments("first/second/#", "first/second/fourth"),
                arguments("first/+/third", "first/fourth/third"), arguments("first/second/#", "first/third"),
                arguments("first/+/third", "first/third"), arguments("first/+/third/#", "first/third/fourth"));
    }

    @ParameterizedTest
    @MethodSource("paramsForTestAtLeastOneTopicShouldMatch")
    void when_atLeastOneTopicShouldMatch_then_returnTrue(String wildcardTopicString, String secondMatchingTopic) {
        var wildcardTopic  = Value.of(wildcardTopicString);
        var matchingTopics = Value.ofArray(Value.of("first/second/third"), Value.of(secondMatchingTopic));

        var result = MqttFunctionLibrary.isMatchingAtLeastOneTopic(wildcardTopic, matchingTopics);

        assertThat(result).isEqualTo(Value.TRUE);
    }

    private static Stream<Arguments> paramsForTestAtLeastOneTopicShouldMatchNoTopicMatches() {
        return Stream.of(arguments("first/second/#", "first/fourth", "first/third"),
                arguments("first/+/third", "first/third", "first/second/third/fourth"),
                arguments("first/+/third/#", "first/second/fourth/third", "first/third/fourth"));
    }

    @ParameterizedTest
    @MethodSource("paramsForTestAtLeastOneTopicShouldMatchNoTopicMatches")
    void when_atLeastOneTopicShouldMatchNoTopicMatches_then_returnFalse(String wildcardTopicString,
            String firstMatchingTopic, String secondMatchingTopic) {
        var wildcardTopic  = Value.of(wildcardTopicString);
        var matchingTopics = Value.ofArray(Value.of(firstMatchingTopic), Value.of(secondMatchingTopic));

        var result = MqttFunctionLibrary.isMatchingAtLeastOneTopic(wildcardTopic, matchingTopics);

        assertThat(result).isEqualTo(Value.FALSE);
    }

    @Test
    void when_atLeastOneTopicShouldMatchWithSingleAndMultiLevelWildcardAndSingleTopicIsMatching_then_returnTrue() {
        var wildcardTopic  = Value.of("first/+/third/#");
        var matchingTopics = Value.ofArray(Value.of("first/second/third"), Value.of("first/third/fourth"));

        var result = MqttFunctionLibrary.isMatchingAtLeastOneTopic(wildcardTopic, matchingTopics);

        assertThat(result).isEqualTo(Value.TRUE);
    }

    @Test
    void when_allTopicsShouldMatchWithMultiLevelWildcardAndSingleTopicMatchesWildcard_then_returnTrue() {
        var components        = PolicyDecisionPointBuilder.withoutDefaults()
                .withFunctionLibrary(MqttFunctionLibrary.class).withResourcesSource("/functionsPolicies").build();
        var pdp               = components.pdp();
        var authzSubscription = AuthorizationSubscription.of("firstSubject", ACTION, "first/second/#");

        var pdpDecisionFlux = pdp.decide(authzSubscription);

        StepVerifier.create(pdpDecisionFlux)
                .expectNextMatches(authzDecision -> authzDecision.decision() == Decision.PERMIT).thenCancel().verify();
    }

    @Test
    void when_atLeastOneTopicShouldMatchWithSingleLevelWildcardAndSingleTopicDoesNotMatchWildcard_then_returnTrue() {
        var components        = PolicyDecisionPointBuilder.withoutDefaults()
                .withFunctionLibrary(MqttFunctionLibrary.class).withResourcesSource("/functionsPolicies").build();
        var pdp               = components.pdp();
        var authzSubscription = AuthorizationSubscription.of("secondSubject", ACTION, "first/+/third");

        var pdpDecisionFlux = pdp.decide(authzSubscription);

        StepVerifier.create(pdpDecisionFlux)
                .expectNextMatches(authzDecision -> authzDecision.decision() == Decision.PERMIT).thenCancel().verify();
    }
}
