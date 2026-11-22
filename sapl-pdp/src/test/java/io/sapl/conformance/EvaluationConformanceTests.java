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
package io.sapl.conformance;

import io.sapl.conformance.ConformanceCorpus.Case;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Values, errors and document refusals against the evaluation corpus.
 */
@Tag("conformance")
@DisplayName("Evaluation against the conformance corpus")
class EvaluationConformanceTests {

    static Stream<Case> evaluationCases() {
        return ConformanceCorpus.casesOf("evaluation").stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("evaluationCases")
    @DisplayName("an expression yields the stated value, an error or a document refusal")
    void whenExpressionIsEvaluatedThenItsOutcomeAgreesWithTheCorpus(Case evaluationCase) {
        assertThat(EvaluationOutcomes.withOutcome(evaluationCase)).isEqualTo(evaluationCase);
    }

    static Stream<Case> coverageCases() {
        return ConformanceCorpus.casesOf("policy-coverage").stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("coverageCases")
    @DisplayName("a policy records the decision, condition hits and dependencies of each covered round")
    void whenAPolicyRecordsCoverageThenBothLatencyModesMatchItsStatedRounds(Case example) {
        assertThat(PolicyCoverageOutcomes.withOutcome(example)).isEqualTo(example);
    }

    static Stream<Case> streamCases() {
        return ConformanceCorpus.casesOf("stream-rounds").stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("streamCases")
    @DisplayName("stream expressions reach the stated dependencies and values")
    void whenAnExpressionReadsAttributesThenItsRoundsMatchTheCorpus(Case example) {
        assertThat(StreamRoundOutcomes.withOutcome(example).expectation()).isEqualTo(example.expectation());
    }

    static Stream<Case> policyCases() {
        return ConformanceCorpus.casesOf("policy-votes").stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("policyCases")
    @DisplayName("a policy or set returns the stated votes")
    void whenADocumentVotesThenItsRoundsMatchTheCorpus(Case example) {
        assertThat(PolicyVoteOutcomes.withOutcome(example).expectation()).isEqualTo(example.expectation());
    }

    static Stream<Case> configurationCases() {
        return ConformanceCorpus.casesOf("configuration-votes").stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("configurationCases")
    @DisplayName("an entire PDP configuration returns the stated decisions")
    void whenAConfigurationVotesThenItsRoundsMatchTheCorpus(Case example) {
        assertThat(PolicyVoteOutcomes.configurationOutcome(example).expectation()).isEqualTo(example.expectation());
    }

    static Stream<Case> configurationCoverageCases() {
        return ConformanceCorpus.casesOf("configuration-coverage").stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("configurationCoverageCases")
    @DisplayName("PDP coverage identifies reached policy conditions, set targets and incomplete rounds")
    void whenAConfigurationCollectsCoverageThenItsReportsMatchTheCorpus(Case example) {
        assertThat(ConfigurationCoverageOutcomes.withOutcome(example).expectation()).isEqualTo(example.expectation());
    }
}
