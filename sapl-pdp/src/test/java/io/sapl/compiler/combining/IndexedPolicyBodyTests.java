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

import static io.sapl.api.pdp.Decision.INDETERMINATE;
import static io.sapl.api.pdp.Decision.NOT_APPLICABLE;
import static io.sapl.api.pdp.configuration.CombiningAlgorithm.DefaultDecision.ABSTAIN;
import static io.sapl.api.pdp.configuration.CombiningAlgorithm.ErrorHandling.PROPAGATE;
import static io.sapl.api.pdp.configuration.CombiningAlgorithm.VotingMode.PRIORITY_DENY;
import static io.sapl.api.pdp.configuration.CombiningAlgorithm.VotingMode.UNANIMOUS;
import static io.sapl.api.pdp.configuration.CombiningAlgorithm.VotingMode.UNIQUE;
import static io.sapl.compiler.pdp.PdpCompiler.compilePDPConfiguration;
import static io.sapl.util.SaplTesting.compilationContext;
import static io.sapl.util.SaplTesting.compileExpression;
import static io.sapl.util.SaplTesting.evaluationContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.VotingMode;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.compiler.pdp.CompiledPdp;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("An indexed policy with an applicability error")
class IndexedPolicyBodyTests {

    @ParameterizedTest(name = "{0}, nested {1}, {2}, guard {3}")
    @MethodSource("guards")
    @DisplayName("waits only for the remaining body condition, which can still rule out the policy")
    void whenAnOfficeConditionCanStillBeFalseThenOnlyItIsRead(VotingMode mode, boolean nested, String index,
            String guard) {
        val pdp = compile(mode, nested, index, """
                policy "access" permit %s; <office.open>; obligation <office.record>""".formatted(guard));
        val ctx = evaluationContext();
        val key = ((StreamOperator) compileExpression("<office.open>")).evaluate(ctx).dependencies().keySet().iterator()
                .next();

        assertRound(pdp, ctx, null, "office.open");
        assertRound(pdp, ctx.withSnapshot(Map.of(key, new AttributeSnapshot(Value.FALSE, Instant.EPOCH))),
                NOT_APPLICABLE, "office.open");
        assertRound(pdp, ctx.withSnapshot(Map.of(key, new AttributeSnapshot(Value.TRUE, Instant.EPOCH))), INDETERMINATE,
                "office.open");
    }

    @ParameterizedTest(name = "{0}, nested {1}, {2}, guard {3}")
    @MethodSource("guards")
    @DisplayName("a policy with no remaining body condition never requests its obligation")
    void whenOnlyTheObligationIsStreamingThenTheGuardErrorIsFinal(VotingMode mode, boolean nested, String index,
            String guard) {
        val pdp = compile(mode, nested, index, """
                policy "access" permit %s; obligation <office.record>""".formatted(guard));

        assertRound(pdp, evaluationContext(), INDETERMINATE);
    }

    private static Stream<Arguments> guards() {
        return Stream.of(PRIORITY_DENY, UNANIMOUS, UNIQUE)
                .flatMap(mode -> Stream.of(false, true)
                        .flatMap(nested -> Stream.of("NAIVE", "CANONICAL", "SMTDD").flatMap(index -> Stream
                                .of("1/0 == 0", "action + true").map(guard -> arguments(mode, nested, index, guard)))));
    }

    private static CompiledPdp compile(VotingMode mode, boolean nested, String index, String policy) {
        val document = nested
                ? """
                        set "offices" %s or abstain errors propagate %s"""
                        .formatted(mode.name().toLowerCase(Locale.ROOT).replace('_', ' '), policy)
                : policy;
        val compiler = compilationContext();
        val options  = ObjectValue.builder().put("indexing", Value.of(index)).build();
        compiler.setCompilerOptions(options);
        val algorithm = new CombiningAlgorithm(mode, ABSTAIN, PROPAGATE);
        val config    = new PDPConfiguration("office", "rules", algorithm, options, List.of(document),
                compiler.getData());

        return compilePDPConfiguration(config, compiler);
    }

    private static void assertRound(CompiledPdp pdp, EvaluationContext ctx, Decision expected, String... attributes) {
        val ordinary = pdp.voter().evaluate(ctx);
        val covered  = pdp.coverageVoter().evaluate(ctx).voteResult();
        for (val round : List.of(ordinary, covered)) {
            val vote     = round.vote();
            val decision = vote == null ? null : vote.authorizationDecision().decision();
            assertThat(decision).isEqualTo(expected);
            assertThat(round.dependencies().keySet()).extracting(key -> key.invocation().attributeName())
                    .containsExactlyInAnyOrder(attributes);
        }
    }
}
