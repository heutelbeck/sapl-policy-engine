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
package io.sapl.pdp.configuration;

import java.util.Set;
import java.util.List;
import io.sapl.api.model.Value;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import lombok.val;

@DisplayName("ConfigurationIds")
class ConfigurationIdsTests {

    private static Map<String, byte[]> contents(String... namesAndValues) {
        val map = new LinkedHashMap<String, byte[]>();
        for (var i = 0; i < namesAndValues.length; i += 2) {
            map.put(namesAndValues[i], namesAndValues[i + 1].getBytes(StandardCharsets.UTF_8));
        }
        return map;
    }

    @Nested
    @DisplayName("derivation")
    class Derivation {

        @Test
        @DisplayName("identical content yields the identical id regardless of insertion order")
        void whenSameContentInDifferentInsertionOrdersThenIdenticalId() {
            val firstOrder  = contents("alpha.sapl", "policy \"a\" permit true", "beta.sapl", "policy \"b\" deny true");
            val secondOrder = contents("beta.sapl", "policy \"b\" deny true", "alpha.sapl", "policy \"a\" permit true");

            assertThat(ConfigurationIds.derive("dir:policies", firstOrder))
                    .isEqualTo(ConfigurationIds.derive("dir:policies", secondOrder))
                    .isEqualTo(ConfigurationIds.derive("dir:policies", firstOrder));
        }

        @Test
        @DisplayName("derived id has the format <label>@<16 lowercase hex chars>")
        void whenDerivingThenFormatIsLabelAtSixteenLowercaseHex() {
            val id = ConfigurationIds.derive("dir:x", contents("a.sapl", "policy \"a\" permit true"));

            assertThat(id).matches("^dir:x@[0-9a-f]{16}$");
        }

        @Test
        @DisplayName("changing content flips the id")
        void whenContentChangesThenIdChanges() {
            val before = ConfigurationIds.derive("dir:x", contents("a.sapl", "policy \"a\" permit true"));
            val after  = ConfigurationIds.derive("dir:x", contents("a.sapl", "policy \"a\" deny true"));

            assertThat(before).isNotEqualTo(after);
        }

        static Stream<Arguments> boundaryShiftedContents() {
            return Stream.of(arguments("name/content boundary shift", contents("ab", "c"), contents("a", "bc")),
                    arguments("entry boundary shift", contents("a", "b", "c", "d"), contents("ac", "bd")),
                    arguments("content moved between entries", contents("a", "bc", "d", "e"),
                            contents("a", "b", "d", "ce")),
                    arguments("empty content vs shifted name", contents("ab", ""), contents("a", "b")));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("boundaryShiftedContents")
        @DisplayName("length prefixing keeps the encoding injective across boundary shifts")
        void whenBoundariesShiftBetweenNamesAndContentsThenIdsDiffer(String description, Map<String, byte[]> left,
                Map<String, byte[]> right) {
            assertThat(ConfigurationIds.contentHash16(left)).isNotEqualTo(ConfigurationIds.contentHash16(right));
        }
    }

    @Nested
    @DisplayName("validity")
    class Validity {

        @ParameterizedTest(name = "accepts \"{0}\"")
        @ValueSource(strings = { "bundle@abc", "dir:x@1", "release-77", "a" })
        void whenIdIsValidThenAcceptedByIsValidAndRequireValid(String configurationId) {
            assertThat(ConfigurationIds.isValid(configurationId)).isTrue();
            assertThatCode(() -> ConfigurationIds.requireValid(configurationId)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("accepts an id of exactly 256 characters")
        void whenIdHasMaximumLengthThenAccepted() {
            val id = "x".repeat(ConfigurationIds.MAX_CONFIGURATION_ID_LENGTH);

            assertThat(ConfigurationIds.isValid(id)).isTrue();
        }

        static Stream<Arguments> invalidIds() {
            return Stream.of(arguments("null", null), arguments("empty", ""), arguments("blank", " "),
                    arguments("257 characters", "x".repeat(257)), arguments("inner space", "has space"),
                    arguments("tab", "has\ttab"), arguments("forward slash", "has/slash"),
                    arguments("backslash", "has\\backslash"), arguments("non-ASCII", "hasümlaut"),
                    arguments("control character", "has\u0007bell"));
        }

        @ParameterizedTest(name = "rejects {0}")
        @MethodSource("invalidIds")
        void whenIdIsInvalidThenRejectedByIsValidAndRequireValid(String description, String configurationId) {
            assertThat(ConfigurationIds.isValid(configurationId)).isFalse();
            assertThatThrownBy(() -> ConfigurationIds.requireValid(configurationId))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("invalid");
        }
    }

    @Nested
    @DisplayName("entriesOf closure")
    class EntriesOfClosure {

        private static final String POLICY_A = "policy \"a\" permit";
        private static final String POLICY_B = "policy \"b\" deny";

        private static PDPConfiguration base() {
            return configurationWith(List.of(POLICY_A, POLICY_B), Value.EMPTY_OBJECT);
        }

        private static PDPConfiguration configurationWith(List<String> documents, ObjectValue variables) {
            return new PDPConfiguration("pdp-a", "id-a", CombiningAlgorithm.DEFAULT, Value.EMPTY_OBJECT, documents,
                    new PdpData(variables, Value.EMPTY_OBJECT), Map.of(), Map.of(), Set.of());
        }

        @Test
        @DisplayName("document order does not change the derived id")
        void whenDocumentsPermutedThenDerivedIdIsIdentical() {
            val forward  = configurationWith(List.of(POLICY_A, POLICY_B), Value.EMPTY_OBJECT);
            val backward = configurationWith(List.of(POLICY_B, POLICY_A), Value.EMPTY_OBJECT);
            assertThat(ConfigurationIds.derive("embedded", ConfigurationIds.entriesOf(forward)))
                    .isEqualTo(ConfigurationIds.derive("embedded", ConfigurationIds.entriesOf(backward)));
        }

        @Test
        @DisplayName("pdpId and configurationId are excluded from the closure")
        void whenIdentityComponentsDifferThenEntriesAreEqual() {
            val one   = base();
            val other = new PDPConfiguration("pdp-b", "id-b", one.combiningAlgorithm(), one.compilerOptions(),
                    one.saplDocuments(), one.data(), one.extensions(), one.extensionSecrets(),
                    one.criticalExtensions());
            assertThat(ConfigurationIds.contentHash16(ConfigurationIds.entriesOf(one)))
                    .isEqualTo(ConfigurationIds.contentHash16(ConfigurationIds.entriesOf(other)));
        }

        @ParameterizedTest(name = "changing {0} changes the derived id")
        @MethodSource("contentMutations")
        void whenContentComponentChangesThenDerivedIdChanges(String description, PDPConfiguration mutated) {
            val baseId    = ConfigurationIds.contentHash16(ConfigurationIds.entriesOf(base()));
            val mutatedId = ConfigurationIds.contentHash16(ConfigurationIds.entriesOf(mutated));
            assertThat(mutatedId).isNotEqualTo(baseId);
        }

        static Stream<Arguments> contentMutations() {
            val base      = base();
            val variables = ObjectValue.builder().put("region", Value.of("eu")).build();
            val secrets   = ObjectValue.builder().put("token", Value.of("t")).build();
            return Stream.of(arguments("a document",
                    new PDPConfiguration(base.pdpId(), base.configurationId(), base.combiningAlgorithm(),
                            base.compilerOptions(), List.of(POLICY_A), base.data(), Map.of(), Map.of(), Set.of())),
                    arguments("the algorithm",
                            new PDPConfiguration(base.pdpId(), base.configurationId(),
                                    new CombiningAlgorithm(CombiningAlgorithm.VotingMode.UNANIMOUS,
                                            CombiningAlgorithm.DefaultDecision.DENY,
                                            CombiningAlgorithm.ErrorHandling.PROPAGATE),
                                    base.compilerOptions(), base.saplDocuments(), base.data(), Map.of(), Map.of(),
                                    Set.of())),
                    arguments("the variables",
                            new PDPConfiguration(base.pdpId(), base.configurationId(), base.combiningAlgorithm(),
                                    base.compilerOptions(), base.saplDocuments(),
                                    new PdpData(variables, Value.EMPTY_OBJECT), Map.of(), Map.of(), Set.of())),
                    arguments("the secrets",
                            new PDPConfiguration(base.pdpId(), base.configurationId(), base.combiningAlgorithm(),
                                    base.compilerOptions(), base.saplDocuments(),
                                    new PdpData(Value.EMPTY_OBJECT, secrets), Map.of(), Map.of(), Set.of())),
                    arguments("an extension config",
                            new PDPConfiguration(base.pdpId(), base.configurationId(), base.combiningAlgorithm(),
                                    base.compilerOptions(), base.saplDocuments(), base.data(), Map.of("geo", variables),
                                    Map.of(), Set.of())),
                    arguments("an extension secret",
                            new PDPConfiguration(base.pdpId(), base.configurationId(), base.combiningAlgorithm(),
                                    base.compilerOptions(), base.saplDocuments(), base.data(), Map.of(),
                                    Map.of("geo", secrets), Set.of())),
                    arguments("the critical extensions",
                            new PDPConfiguration(base.pdpId(), base.configurationId(), base.combiningAlgorithm(),
                                    base.compilerOptions(), base.saplDocuments(), base.data(), Map.of("geo", variables),
                                    Map.of(), Set.of("geo"))));
        }
    }
}
