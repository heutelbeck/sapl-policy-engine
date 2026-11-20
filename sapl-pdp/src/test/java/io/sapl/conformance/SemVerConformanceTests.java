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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.semver.Version;
import io.sapl.api.semver.VersionRange;
import lombok.val;

@Tag("conformance")
@DisplayName("Semantic versions against the conformance corpus")
class SemVerConformanceTests {
    @MethodSource("versions")
    @ParameterizedTest(name = "{0}")
    @DisplayName("Valid versions preserve identifiers and distinguish initial development from prereleases")
    void whenVersionIsValidThenPartsAndReleaseKindArePreserved(String name, String text, String normalized,
            boolean stable, boolean prerelease) {
        assertThat(Version.parse(text))
                .extracting(Version::text, Version::isStable, version -> !version.preRelease().isEmpty())
                .containsExactly(normalized, stable, prerelease);
    }

    @MethodSource("invalidVersions")
    @ParameterizedTest(name = "{0}")
    @DisplayName("Malformed and out of bounds versions produce a clear error")
    void whenVersionIsInvalidThenParsingFailsClearly(String name, String text) {
        assertThatIllegalArgumentException().isThrownBy(() -> Version.parse(text))
                .withMessage("Invalid semantic version: '%s'.", text);
    }

    @MethodSource("precedence")
    @ParameterizedTest(name = "{0}")
    @DisplayName("Version precedence follows section eleven and comparisons have a unit sign")
    void whenVersionsAreComparedThenPrecedenceFollowsSectionEleven(String name, String first, String second,
            int expected) {
        val left  = Version.parse(first);
        val right = Version.parse(second);
        assertThat(new int[] { left.comparePrecedenceTo(right), right.comparePrecedenceTo(left) })
                .containsExactly(expected, -expected);
    }

    @MethodSource("ranges")
    @ParameterizedTest(name = "{0}")
    @DisplayName("Node ranges apply all comparators and require explicit prerelease opt in")
    void whenVersionIsMatchedThenNodeRangesAndPrereleaseOptInApply(String name, String text, String expression,
            boolean expected) {
        val version = Version.parse(text);
        assertThat(VersionRange.parse(expression).contains(version)).isEqualTo(expected);
    }

    @MethodSource("invalidRanges")
    @ParameterizedTest(name = "{0}")
    @DisplayName("Invalid tokens invalidate the entire range")
    void whenAnyRangeTokenIsInvalidThenEntireRangeIsRefused(String name, String expression) {
        assertThatIllegalArgumentException().isThrownBy(() -> VersionRange.parse(expression))
                .withMessage("Invalid range: '%s'.", expression);
    }

    @MethodSource("coercions")
    @ParameterizedTest(name = "{0}")
    @DisplayName("Coercion extracts the first version sequence without suffixes")
    void whenTextIsCoercedThenFirstVersionSequenceHasNoSuffixes(String name, String text, String expected) {
        assertThat(Optional.ofNullable(Version.coerce(text)).map(Version::text).orElse("null")).isEqualTo(expected);
    }

    private static Stream<Arguments> versions() throws IOException {
        return specifications("versions", 5);
    }

    private static Stream<Arguments> invalidVersions() throws IOException {
        return specifications("invalid_versions", 2);
    }

    private static Stream<Arguments> precedence() throws IOException {
        return specifications("precedence", 4);
    }

    private static Stream<Arguments> ranges() throws IOException {
        return specifications("ranges", 4);
    }

    private static Stream<Arguments> invalidRanges() throws IOException {
        return specifications("invalid_ranges", 2);
    }

    private static Stream<Arguments> coercions() throws IOException {
        return specifications("coercions", 3);
    }

    private static Stream<Arguments> specifications(String name, int columns) throws IOException {
        val path  = ConformanceCorpus.pathOf("semver/" + name + ".tsv");
        val lines = Files.readAllLines(path);
        assertThat(lines).as("The specification file '%s' must hold rows.", path).isNotEmpty();
        return IntStream.range(0, lines.size()).mapToObj(index -> {
            val fields = lines.get(index).split("\t", -1);
            assertThat(fields).as("The specification row at '%s:%s' must have %s columns.", path, index + 1, columns)
                    .hasSize(columns);
            return arguments((Object[]) fields);
        });
    }
}
