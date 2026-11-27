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
package io.sapl.api.semver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import lombok.val;

@DisplayName("Semantic versions and node style ranges")
class SemVerTests {
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

    @NullSource
    @ValueSource(strings = { "", "1.2", "1.2.3-\u00e9", "\u0661.2.3", "1.2.3\n" })
    @ParameterizedTest(name = "Invalid optional version {0}")
    @DisplayName("An absent or invalid optional version is not a parsed version")
    void whenOptionalVersionIsAbsentOrInvalidThenParsingReturnsNull(String text) {
        assertThat(Version.tryParse(text)).isNull();
    }

    @MethodSource("rangeWhitespace")
    @ParameterizedTest(name = "{0}")
    @DisplayName("Ranges ignore ASCII whitespace and refuse other control characters")
    void whenRangeContainsWhitespaceThenOnlyWhitespaceIsIgnored(String name, String expression, boolean valid) {
        val version = Version.parse("1.5.0");
        if (valid) {
            assertThat(VersionRange.parse(expression).contains(version)).isTrue();
        } else {
            assertThatIllegalArgumentException().isThrownBy(() -> VersionRange.parse(expression))
                    .withMessage("Invalid range: '%s'.", expression);
        }
    }

    private static Stream<Arguments> rangeWhitespace() {
        return Stream.of(arguments("vertical tab separates comparators", ">=1.0.0\013<2.0.0", true),
                arguments("line endings separate comparators", "\r\n>=1.0.0\t<2.0.0\f", true),
                arguments("zero bytes are not whitespace", "\0>=1.0.0", false),
                arguments("control bytes are not whitespace", ">=1.0.0\037", false),
                arguments("non ASCII space is refused", "\u00a0>=1.0.0", false));
    }

    private static Stream<Arguments> versions() {
        return Stream.of(arguments("release", "1.2.3", "1.2.3", true, false),
                arguments("initial development", "0.1.0", "0.1.0", false, false),
                arguments("tagged prerelease", "v1.2.3-alpha.1", "1.2.3-alpha.1", false, true),
                arguments("maximum component", "9007199254740991.0.0", "9007199254740991.0.0", true, false),
                arguments("build identifiers keep leading zeros", "1.2.3+00001", "1.2.3+00001", true, false));
    }

    private static Stream<Arguments> invalidVersions() {
        return Stream.of(arguments("empty version", ""), arguments("missing patch", "1.2"),
                arguments("leading zero in core", "01.2.3"), arguments("leading zero in prerelease", "1.2.3-01"),
                arguments("empty identifier", "1.2.3-alpha..1"),
                arguments("component beyond maximum", "9007199254740992.0.0"));
    }

    private static Stream<Arguments> precedence() {
        return Stream.of(arguments("prerelease before release", "1.0.0-rc.1", "1.0.0", -1),
                arguments("numeric identifiers compare numerically", "1.0.0-beta.2", "1.0.0-beta.11", -1),
                arguments("mixed identifiers compare in ASCII order", "1.0.0-alpha10", "1.0.0-alpha9", -1),
                arguments("build metadata has no precedence", "1.0.0+a", "1.0.0+b", 0),
                arguments("comparison has a unit sign", "1.0.0-a1b2c3", "1.0.0-a1", 1));
    }

    private static Stream<Arguments> ranges() {
        return Stream.of(arguments("comparator conjunction", "1.5.0", ">=1.0.0 <2.0.0", true),
                arguments("zero major caret boundary", "1.0.0", "^0.x", false),
                arguments("wildcard boundary", "2.0.0", "1.x", false),
                arguments("tilde patch update", "1.2.9", "~1.2", true),
                arguments("inclusive hyphen endpoint", "2.3.4", "1.2 - 2.3.4", true),
                arguments("prerelease requires opt in", "1.2.3-beta", ">=1.0.0 <2.0.0", false),
                arguments("prerelease opt in for the same core", "1.2.3-beta", ">=1.2.3-alpha <2.0.0", true));
    }

    private static Stream<Arguments> invalidRanges() {
        return Stream.of(arguments("invalid trailing token", ">=1.0.0 garbage"),
                arguments("reversed comparator", "=>1.0.0"), arguments("Ivy interval", "[1.0,2.0)"),
                arguments("latest is not a range", "latest"),
                arguments("component beyond maximum", ">=9007199254740992.0.0"),
                arguments("leading zero in range", "1.02.x"));
    }

    private static Stream<Arguments> coercions() {
        return Stream.of(arguments("missing components", "1", "1.0.0"),
                arguments("suffixes removed", "v1.2.3-alpha+build", "1.2.3"),
                arguments("first match wins", "release 1.2.3 and 4.5.6", "1.2.3"),
                arguments("leading zeros refused", "01.002.0003", "null"), arguments("empty input", "", "null"),
                arguments("component beyond maximum", "9007199254740992", "null"));
    }
}
