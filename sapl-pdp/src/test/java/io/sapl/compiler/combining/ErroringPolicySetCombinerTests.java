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
package io.sapl.compiler.combining;

import static io.sapl.api.pdp.configuration.CombiningAlgorithm.DefaultDecision.ABSTAIN;
import static io.sapl.api.pdp.configuration.CombiningAlgorithm.ErrorHandling.PROPAGATE;
import static io.sapl.api.pdp.configuration.CombiningAlgorithm.VotingMode.PRIORITY_PERMIT;
import static io.sapl.api.pdp.configuration.CombiningAlgorithm.VotingMode.UNANIMOUS;
import static io.sapl.api.pdp.configuration.CombiningAlgorithm.VotingMode.UNIQUE;
import static io.sapl.util.SaplTesting.compilationContext;
import static io.sapl.util.SaplTesting.evaluationContext;
import static io.sapl.util.SaplTesting.parseSubscription;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.VotingMode;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.attributes.broker.api.TestAttributeBroker;
import io.sapl.compiler.pdp.CompiledPdp;
import io.sapl.compiler.pdp.PdpCompiler;
import io.sapl.util.CoverageEvaluator;
import io.sapl.util.VoterEvaluator;
import lombok.val;

/**
 * A policy set's target is pure by grammar. There is no stream stratum in a set
 * target, so nothing can dominate a target error the way a stream FALSE
 * dominates a pure error inside a policy body. A set whose target errors is
 * therefore terminally INDETERMINATE, and the index backed PDP combiners must
 * carry that error through verbatim. They must not evaluate the set's inner
 * combining, and the error must not be reduced to NOT_APPLICABLE.
 * <p>
 * The set reaches the combiner as an index error match only at the PDP level,
 * because a set is always a top level document. The scenario pairs the erroring
 * set with a NOT_APPLICABLE sibling so the set is the only contributor. A
 * stream
 * sibling forces the stream combiner path that holds the explicit set guard,
 * and
 * a pure sibling forces the pure combiner path. Were the guard absent, the
 * set's
 * inner combining would run and emit PERMIT, so the INDETERMINATE expectation
 * pins the special case. The naive index is the oracle backend, and the
 * coverage
 * path must match production.
 */
@DisplayName("An erroring policy set is terminal INDETERMINATE through the index combiners")
class ErroringPolicySetCombinerTests {

    private static final String SUBSCRIPTION = """
            { "subject": { "broken": 42 }, "action": "read", "resource": "data" }
            """;

    private static final String ERRORING_SET = """
            set "erroringSet"
            priority permit or deny
            for subject.broken
            policy "inner" permit
            """;

    private static final String STREAM_SIBLING = """
            policy "streamSibling" permit <test.attr>;
            """;

    private static final String PURE_SIBLING = """
            policy "pureSibling" permit false;
            """;

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    @DisplayName("the set target error is carried as INDETERMINATE and its inner combining never runs")
    void whenSetTargetErrorsThenCombinerYieldsIndeterminate(String label, VotingMode mode, String siblingSource,
            @Nullable Value streamAttribute) {
        val pdp     = compileNaivePdp(mode, List.of(ERRORING_SET, siblingSource));
        val baseCtx = evaluationContext(parseSubscription(SUBSCRIPTION));
        try (val broker = new TestAttributeBroker()) {
            if (streamAttribute != null) {
                broker.register("test.attr", streamAttribute);
            }
            try (val production = VoterEvaluator.evaluate(pdp.voter(), baseCtx, broker);
                    val coverage = CoverageEvaluator.evaluate(pdp.coverageVoter(), baseCtx, broker)) {
                val productionVote = production.awaitNext();
                val coverageVote   = coverage.awaitNext().voteResult().vote();
                assertThat(productionVote).isNotNull();
                assertThat(productionVote.authorizationDecision().decision()).as("Production path decision")
                        .isEqualTo(Decision.INDETERMINATE);
                assertThat(coverageVote.authorizationDecision().decision()).as("Coverage path must match production")
                        .isEqualTo(Decision.INDETERMINATE);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while awaiting evaluator emissions", e);
        }
    }

    private static CompiledPdp compileNaivePdp(VotingMode mode, List<String> documents) {
        val ctx = compilationContext();
        ctx.setCompilerOptions(ObjectValue.builder().put("indexing", Value.of("NAIVE")).build());
        val config = new PDPConfiguration("test-pdp", "config", new CombiningAlgorithm(mode, ABSTAIN, PROPAGATE),
                documents, new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
        return PdpCompiler.compilePDPConfiguration(config, ctx);
    }

    static Stream<Arguments> scenarios() {
        // The stream sibling resolves to NOT_APPLICABLE yet forces the stream
        // combiner path. The pure sibling forces the pure combiner path.
        return Stream.of(arguments("priority/streamPath", PRIORITY_PERMIT, STREAM_SIBLING, Value.FALSE),
                arguments("priority/purePath", PRIORITY_PERMIT, PURE_SIBLING, null),
                arguments("unanimous/streamPath", UNANIMOUS, STREAM_SIBLING, Value.FALSE),
                arguments("unanimous/purePath", UNANIMOUS, PURE_SIBLING, null),
                arguments("unique/streamPath", UNIQUE, STREAM_SIBLING, Value.FALSE),
                arguments("unique/purePath", UNIQUE, PURE_SIBLING, null));
    }
}
