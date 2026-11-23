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

import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.ast.PolicySet;
import io.sapl.compiler.document.DocumentCompiler;
import io.sapl.compiler.model.Coverage;
import io.sapl.compiler.policy.PolicyCompiler;
import io.sapl.compiler.policyset.PolicySetCompiler;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.sapl.util.SaplTesting.compilationContext;
import static io.sapl.util.SaplTesting.evaluationContext;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Policy set schema coverage")
class VoterAndCoverageTests {

    @ParameterizedTest(name = "subject {0}, target {1}, schema hit {2}")
    @CsvSource(textBlock = """
            true,  for action == true,  true
            false, for action == true,  false
            true,  for action == false,
            true,  for true,            true
            false, for true,            false
            true,  for false,
            true,  '',                  true
            """)
    void whenSetHasEnforcedSchemaThenCoverageReportsOnlyReachedValidation(boolean validSubject, String target,
            Boolean expectedHit) {
        val subject = validSubject ? Value.TRUE : Value.of("visitor");
        assertSchemaCoverage("subject enforced schema {\"type\":\"boolean\"}", target, subject, expectedHit);
    }

    @ParameterizedTest(name = "additional declaration {0}")
    @ValueSource(strings = { "subject enforced schema {\"type\":\"number\"}",
            "action enforced schema {\"type\":\"boolean\"}" })
    void whenSetCombinesEnforcedSchemasThenCoverageReportsTheBlockResult(String additionalSchema) {
        val schemas = "subject enforced schema {\"type\":\"boolean\"}\n" + additionalSchema;
        assertSchemaCoverage(schemas, "for true", Value.TRUE, true);
    }

    private static void assertSchemaCoverage(String schemas, String target, Value subject, Boolean expectedHit) {
        val source     = """
                %s
                set "office" priority deny or abstain
                %s
                policy "entry" permit
                """.formatted(schemas, target);
        val ctx        = compilationContext();
        val set        = (PolicySet) DocumentCompiler.parseDocument(source).saplDocument();
        val compiled   = PolicySetCompiler.compilePolicySet(set, compilationContext());
        val members    = set.policies().stream().map(policy -> PolicyCompiler.compilePolicy(policy, ctx)).toList();
        val voter      = PriorityVoteCompiler.compilePolicySet(set, members, compiled.isApplicable(), set.metadata(),
                Decision.DENY, set.algorithm().defaultDecision(), set.algorithm().errorHandling(), ctx);
        val request    = evaluationContext(AuthorizationSubscription.of(subject, Value.TRUE, Value.NULL));
        val direct     = voter.coverageVoter().evaluate(request);
        val production = compiled.coverageVoter().evaluate(request);
        val expected   = expectedHit == null ? null
                : new Coverage.ConditionHit(Value.of(expectedHit), set.match().location(), -1, true);

        assertThat(((Coverage.PolicySetCoverage) direct.coverage()).schemaHit()).isEqualTo(expected);
        assertThat(direct).isEqualTo(production);
    }
}
