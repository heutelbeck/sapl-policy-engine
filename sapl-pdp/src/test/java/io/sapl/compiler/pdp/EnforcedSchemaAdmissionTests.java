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
package io.sapl.compiler.pdp;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.compiler.expressions.SaplCompilerException;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static io.sapl.api.pdp.configuration.CombiningAlgorithm.DefaultDecision.ABSTAIN;
import static io.sapl.api.pdp.configuration.CombiningAlgorithm.ErrorHandling.PROPAGATE;
import static io.sapl.api.pdp.configuration.CombiningAlgorithm.VotingMode.PRIORITY_DENY;
import static io.sapl.compiler.pdp.PdpCompiler.compilePDPConfiguration;
import static io.sapl.util.SaplTesting.compilationContext;
import static io.sapl.util.SaplTesting.evaluationContext;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("Admission of schema-guarded documents")
class EnforcedSchemaAdmissionTests {

    private static final String POLICY    = "policy \"guarded\" deny";
    private static final String SET       = """
            set "group" first or abstain, errors propagate
            policy "guarded" deny
            """;
    private static final String COMPANION = "policy \"companion\" permit";

    @ValueSource(strings = { POLICY, SET })
    @ParameterizedTest(name = "invalid enforced schema on {0}")
    @DisplayName("An invalid enforced schema refuses the configuration including its companion")
    void whenEnforcedSchemaIsMalformedThenNeitherDocumentIsAdmitted(String body) {
        val config  = configuration("""
                subject enforced schema {"type":123}
                """ + body, Value.EMPTY_OBJECT);
        val context = compilationContext();
        assertThatExceptionOfType(SaplCompilerException.class)
                .isThrownBy(() -> compilePDPConfiguration(config, context))
                .satisfies(error -> assertThat(error.diagnostics()).anySatisfy(diagnostic -> {
                    assertThat(diagnostic.getMessage()).contains("valid JSON Schema", "type");
                    assertThat(diagnostic.getLocation().line()).isOne();
                }));
    }

    @ValueSource(strings = { POLICY, SET })
    @ParameterizedTest(name = "valid enforced schema on {0}")
    @DisplayName("A matching subject reaches the deny and a mismatching subject gets the companion permit")
    void whenEnforcedSchemaIsValidThenItChecksTheSubject(String body) {
        val config   = configuration("""
                subject enforced schema {"type":"string"}
                """ + body, Value.EMPTY_OBJECT);
        val compiled = compilePDPConfiguration(config, compilationContext());
        assertThat(List.of(decision(compiled, Value.of("ok")), decision(compiled, Value.of(123))))
                .containsExactly(Decision.DENY, Decision.PERMIT);
    }

    @ValueSource(strings = { POLICY, SET })
    @ParameterizedTest(name = "annotation on {0}")
    @DisplayName("A schema annotation does not participate in admission or evaluation")
    void whenSchemaIsOnlyAnAnnotationThenTheGuardedDocumentApplies(String body) {
        val config   = configuration("""
                subject schema {"type":123}
                """ + body, Value.EMPTY_OBJECT);
        val compiled = compilePDPConfiguration(config, compilationContext());
        assertThat(decision(compiled, Value.of("ok"))).isEqualTo(Decision.DENY);
    }

    @Test
    @DisplayName("A provided referenced resource uses its declared draft for admission and evaluation")
    void whenAReferencedSchemaUsesDraftSevenThenItsTupleItemsAreValid() {
        val variables = (ObjectValue) Value.ofJson("""
                {"schemas":[{"$id":"urn:sapl:tuple","$schema":"http://json-schema.org/draft-07/schema#",
                             "items":[{"type":"string"}]}]}
                """);
        val config    = configuration("""
                subject enforced schema {"$ref":"urn:sapl:tuple"}
                """ + POLICY, variables);
        val compiled  = compilePDPConfiguration(config, compilationContext(variables));
        assertThat(List.of(decision(compiled, Value.ofJson("[\"ok\"]")), decision(compiled, Value.ofJson("[123]"))))
                .containsExactly(Decision.DENY, Decision.PERMIT);
    }

    @Test
    @DisplayName("A bundled resource keeps its dialect when a policy embeds it")
    void whenATupleResourceIsBundledThenItsDeclaredDialectApplies() {
        val config   = configuration("""
                subject enforced schema {
                    "$defs":{"tuple":{"$id":"urn:sapl:tuple","$schema":"http://json-schema.org/draft-07/schema#",
                                      "items":[{"type":"string"}]}},
                    "$ref":"urn:sapl:tuple"
                }
                """ + POLICY, Value.EMPTY_OBJECT);
        val compiled = compilePDPConfiguration(config, compilationContext());
        assertThat(List.of(decision(compiled, Value.ofJson("[\"ok\"]")), decision(compiled, Value.ofJson("[123]"))))
                .containsExactly(Decision.DENY, Decision.PERMIT);
    }

    @Test
    @DisplayName("An embedded resource must satisfy its own dialect even when its parent dialect would admit it")
    void whenABundledResourceFailsItsDialectThenCompilationFails() {
        val config  = configuration(
                """
                        subject enforced schema {
                            "$defs":{"legacy":{"id":"urn:sapl:legacy","$id":"urn:sapl:legacy","$schema":"http://json-schema.org/draft-04/schema#",
                                               "minimum":0,"exclusiveMinimum":2}},
                            "$ref":"#/$defs/legacy"
                        }
                        """
                        + POLICY,
                Value.EMPTY_OBJECT);
        val context = compilationContext();
        assertThatExceptionOfType(SaplCompilerException.class)
                .isThrownBy(() -> compilePDPConfiguration(config, context))
                .satisfies(error -> assertThat(error.diagnostics())
                        .anySatisfy(diagnostic -> assertThat(diagnostic.getMessage()).contains("exclusiveMinimum")));
    }

    @Test
    @DisplayName("The default draft rejects tuple items in a referenced resource")
    void whenAReferencedSchemaViolatesTheDefaultDialectThenCompilationFails() {
        val variables = (ObjectValue) Value.ofJson("""
                {"schemas":[{"$id":"urn:sapl:tuple","items":[{"type":"string"}]}]}
                """);
        val config    = configuration("""
                subject enforced schema {"$ref":"urn:sapl:tuple"}
                """ + POLICY, variables);
        val context   = compilationContext(variables);
        assertThatExceptionOfType(SaplCompilerException.class)
                .isThrownBy(() -> compilePDPConfiguration(config, context))
                .satisfies(error -> assertThat(error.diagnostics())
                        .anySatisfy(diagnostic -> assertThat(diagnostic.getMessage()).contains("valid JSON Schema")));
    }

    private static Decision decision(CompiledPdp compiled, Value subject) {
        val subscription = AuthorizationSubscription.of(subject, Value.NULL, Value.NULL, Value.NULL);
        val vote         = compiled.voter().evaluate(evaluationContext(subscription)).vote();
        return requireNonNull(vote, "A synchronous guard must produce a decision").authorizationDecision().decision();
    }

    private static PDPConfiguration configuration(String guarded, ObjectValue variables) {
        return new PDPConfiguration("pdp", "config", new CombiningAlgorithm(PRIORITY_DENY, ABSTAIN, PROPAGATE),
                List.of(COMPANION, guarded), new PdpData(variables, Value.EMPTY_OBJECT));
    }

}
