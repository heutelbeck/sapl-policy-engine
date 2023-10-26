/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.interpreter.InitializationException;
import io.sapl.pdp.PolicyDecisionPointFactory;
import reactor.test.StepVerifier;

class MqttFunctionLibraryTests {

    private final static String ACTION = "actionName";

    private final static JsonNodeFactory JSON = JsonNodeFactory.instance;

    MqttFunctionLibrary mqttFunctions = new MqttFunctionLibrary();

    // Tests for matching all topics

    @ParameterizedTest
    @ValueSource(strings = { "first/second/#", "first/+/third", "first/second/third" })
    void when_allTopicsShouldMatchAndMatching_then_returnTrue() {
        // GIVEN
        Val wildcardTopic = Val.of("first/second/#");
        Val matchingTopic = Val.of("first/second/third");

        // WHEN
        boolean isMatching = mqttFunctions.isMatchingAllTopics(wildcardTopic, matchingTopic).getBoolean();

        // THEN
        assertTrue(isMatching);
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
        // GIVEN
        Val    wildcardTopic      = Val.of(wildCardTopicString);
        String firstMatchingTopic = "first/second/third";
        Val    matchingTopics     = Val.of(JSON.arrayNode().add(firstMatchingTopic).add(secondMatchingTopic));

        // WHEN
        boolean isMatching = mqttFunctions.isMatchingAllTopics(wildcardTopic, matchingTopics).getBoolean();

        // THEN
        assertTrue(isMatching);
    }

    private static Stream<Arguments> paramsForTestAllTopicsShouldMatchAndSingleTopicDoesNotMatch() {
        return Stream.of(arguments("first/second/#", "first/third"), arguments("first/+/third", "first/third"),
                arguments("first/+/third/#", "first/third/fourth"));
    }

    @ParameterizedTest
    @MethodSource("paramsForTestAllTopicsShouldMatchAndSingleTopicDoesNotMatch")
    void when_allTopicsShouldMatchAndSingleTopicDoesNotMatch_then_returnFalse(String wildcardTopicString,
            String secondMatchingTopic) {
        // GIVEN
        Val    wildcardTopic      = Val.of(wildcardTopicString);
        String firstMatchingTopic = "first/second/third";
        Val    matchingTopics     = Val.of(JSON.arrayNode().add(firstMatchingTopic).add(secondMatchingTopic));

        // WHEN
        boolean isMatching = mqttFunctions.isMatchingAllTopics(wildcardTopic, matchingTopics).getBoolean();

        // THEN
        assertFalse(isMatching);
    }

    @Test
    void when_allTopicsShouldMatchButSpecifiedAWildcardInTopicToMatch_then_returnValError() {
        // GIVEN
        Val wildcardTopic = Val.of("first/second/#");
        Val matchingTopic = Val.of("first/second/third/#");

        // WHEN
        boolean isValError = mqttFunctions.isMatchingAllTopics(wildcardTopic, matchingTopic).isError();

        // THEN
        assertTrue(isValError);
    }

    @Test
    void when_allTopicsShouldMatchButSpecifiedAWildcardInTopicsToMatch_then_returnValError() {
        // GIVEN
        Val    wildcardTopic       = Val.of("first/second/#");
        String firstMatchingTopic  = "first/second/third";
        String secondMatchingTopic = "first/second/+/fourth";
        Val    matchingTopics      = Val.of(JSON.arrayNode().add(firstMatchingTopic).add(secondMatchingTopic));

        // WHEN
        boolean isValError = mqttFunctions.isMatchingAllTopics(wildcardTopic, matchingTopics).isError();

        // THEN
        assertTrue(isValError);
    }

    // Tests for matching at least one topic

    @ParameterizedTest
    @ValueSource(strings = { "first/second/#", "first/+/third", "first/second/third" })
    void when_atLeastOneTopicShouldMatchAndMatching_then_returnTrue(String wildcardTopicString) {
        // GIVEN
        Val wildcardTopic = Val.of(wildcardTopicString);
        Val matchingTopic = Val.of("first/second/third");

        // WHEN
        boolean isMatching = mqttFunctions.isMatchingAtLeastOneTopic(wildcardTopic, matchingTopic).getBoolean();

        // THEN
        assertTrue(isMatching);
    }

    private static Stream<Arguments> paramsForTestAtLeastOneTopicShouldMatch() {
        return Stream.of(arguments("first/second/#", "first/second/fourth"),
                arguments("first/+/third", "first/fourth/third"), arguments("first/second/#", "first/third"),
                arguments("first/+/third", "first/third"), arguments("first/+/third/#", "first/third/fourth"));
    }

    @ParameterizedTest
    @MethodSource("paramsForTestAtLeastOneTopicShouldMatch")
    void when_atLeastOneTopicShouldMatch_then_returnTrue(String wildcardTopicString, String secondMatchingTopic) {
        // GIVEN
        Val    wildcardTopic      = Val.of(wildcardTopicString);
        String firstMatchingTopic = "first/second/third";
        Val    matchingTopics     = Val.of(JSON.arrayNode().add(firstMatchingTopic).add(secondMatchingTopic));

        // WHEN
        boolean isMatching = mqttFunctions.isMatchingAtLeastOneTopic(wildcardTopic, matchingTopics).getBoolean();

        // THEN
        assertTrue(isMatching);
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
        // GIVEN
        Val wildcardTopic  = Val.of(wildcardTopicString);
        Val matchingTopics = Val.of(JSON.arrayNode().add(firstMatchingTopic).add(secondMatchingTopic));

        // WHEN
        boolean isMatching = mqttFunctions.isMatchingAtLeastOneTopic(wildcardTopic, matchingTopics).getBoolean();

        // THEN
        assertFalse(isMatching);
    }

    @Test
    void when_atLeastOneTopicShouldMatchWithSingleAndMultiLevelWildcardAndSingleTopicIsMatching_then_returnTrue() {
        // GIVEN
        Val    wildcardTopic       = Val.of("first/+/third/#");
        String firstMatchingTopic  = "first/second/third";
        String secondMatchingTopic = "first/third/fourth";
        Val    matchingTopics      = Val.of(JSON.arrayNode().add(firstMatchingTopic).add(secondMatchingTopic));

        // WHEN
        boolean isMatching = mqttFunctions.isMatchingAtLeastOneTopic(wildcardTopic, matchingTopics).getBoolean();

        // THEN
        assertTrue(isMatching);
    }

    @Test
    void when_atLeastOneTopicShouldMatchButSpecifiedAWildcardInTopicToMatch_then_returnValError() {
        // GIVEN
        Val wildcardTopic = Val.of("first/second/#");
        Val matchingTopic = Val.of("first/second/third/#");

        // WHEN
        boolean isValError = mqttFunctions.isMatchingAtLeastOneTopic(wildcardTopic, matchingTopic).isError();

        // THEN
        assertTrue(isValError);
    }

    @Test
    void when_atLeastOneTopicShouldMatchButSpecifiedAWildcardInTopicsToMatch_then_returnValError() {
        // GIVEN
        Val    wildcardTopic       = Val.of("first/second/#");
        String firstMatchingTopic  = "first/second/third";
        String secondMatchingTopic = "first/second/+/fourth";
        Val    matchingTopics      = Val.of(JSON.arrayNode().add(firstMatchingTopic).add(secondMatchingTopic));

        // WHEN
        boolean isValError = mqttFunctions.isMatchingAtLeastOneTopic(wildcardTopic, matchingTopics).isError();

        // THEN
        assertTrue(isValError);
    }

    @Test
    void when_allTopicsShouldMatchWithMultiLevelWildcardAndSingleTopicMatchesWildcard_then_returnTrue()
            throws InitializationException {
        // GIVEN
        var pdp               = PolicyDecisionPointFactory.filesystemPolicyDecisionPoint(
                "src/test/resources/functionsPolicies", List.of(), List.of(new MqttFunctionLibrary()), List.of(),
                List.of());
        var authzSubscription = AuthorizationSubscription.of("firstSubject", ACTION, "first/second/#");

        // WHEN
        var pdpDecisionFlux = pdp.decide(authzSubscription);

        // THEN
        StepVerifier.create(pdpDecisionFlux)
                .expectNextMatches(authzDecision -> authzDecision.getDecision() == Decision.PERMIT).thenCancel()
                .verify();
    }

    @Test
    void when_atLeastOneTopicShouldMatchWithSingleLevelWildcardAndSingleTopicDoesNotMatchWildcard_then_returnTrue()
            throws InitializationException {
        // GIVEN
        var pdp = PolicyDecisionPointFactory.filesystemPolicyDecisionPoint("src/test/resources/functionsPolicies",
                List.of(), List.of(new MqttFunctionLibrary()), List.of(), List.of());

        var authzSubscription = AuthorizationSubscription.of("secondSubject", ACTION, "first/+/third");

        // WHEN
        var pdpDecisionFlux = pdp.decide(authzSubscription);

        // THEN
        StepVerifier.create(pdpDecisionFlux)
                .expectNextMatches(authzDecision -> authzDecision.getDecision() == Decision.PERMIT).thenCancel()
                .verify();
    }

}