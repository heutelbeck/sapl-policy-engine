/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.functions;

import io.sapl.api.interpreter.Val;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static io.sapl.assertj.SaplAssertions.assertThatVal;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive test suite for SemVerFunctionLibrary.
 */
class SemVerFunctionLibraryTests {

    /* Parsing and Validation Tests */

    @ParameterizedTest
    @ValueSource(strings = { "1.0.0", "2.3.5", "0.0.1", "10.20.30", "1.0.0-alpha", "1.0.0-beta.1",
            "1.0.0-rc.2+build.456", "v1.0.0", "V2.0.0", "1.2.3-alpha.beta+build.123", "1.0.0--alpha", "1.0.0---",
            "1.0.0-alpha-beta" })
    void when_isValid_withValidVersions_then_returnsTrue(String version) {
        val actual = SemVerFunctionLibrary.isValid(Val.of(version));
        assertThatVal(actual).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "1.0", "1", "a.b.c", "1.0.0.0", "", "1.0.0-", "1.0.0+", "v", "vv1.0.0" })
    void when_isValid_withInvalidVersions_then_returnsFalse(String version) {
        val actual = SemVerFunctionLibrary.isValid(Val.of(version));
        assertThatVal(actual).isFalse();
    }

    @ParameterizedTest
    @MethodSource("provideParseTestCases")
    void when_parse_withValidVersion_then_returnsComponents(String version, int major, int minor, int patch,
            String preRelease, String buildMetadata) {
        val actual = SemVerFunctionLibrary.parse(Val.of(version));

        assertThatVal(actual).hasValue();
        val result = actual.get();
        assertThat(result.get("major").asInt()).isEqualTo(major);
        assertThat(result.get("minor").asInt()).isEqualTo(minor);
        assertThat(result.get("patch").asInt()).isEqualTo(patch);
        assertThat(result.get("preRelease").asText()).isEqualTo(preRelease);
        assertThat(result.get("buildMetadata").asText()).isEqualTo(buildMetadata);
    }

    private static Stream<Arguments> provideParseTestCases() {
        return Stream.of(Arguments.of("1.0.0", 1, 0, 0, "", ""), Arguments.of("2.3.5", 2, 3, 5, "", ""),
                Arguments.of("1.0.0-alpha", 1, 0, 0, "alpha", ""), Arguments.of("1.0.0-beta.1", 1, 0, 0, "beta.1", ""),
                Arguments.of("1.0.0+build.123", 1, 0, 0, "", "build.123"),
                Arguments.of("1.0.0-rc.1+build.456", 1, 0, 0, "rc.1", "build.456"),
                Arguments.of("v2.5.8", 2, 5, 8, "", ""), Arguments.of("V3.0.0-alpha", 3, 0, 0, "alpha", ""),
                Arguments.of("10.20.30", 10, 20, 30, "", ""));
    }

    @ParameterizedTest
    @ValueSource(strings = { "1.0", "invalid", "1.0.0.0", "" })
    void when_parse_withInvalidVersion_then_returnsError(String invalidVersion) {
        val actual = SemVerFunctionLibrary.parse(Val.of(invalidVersion));
        assertThatVal(actual).isError();
        assertThat(actual.getMessage()).contains("Invalid semantic version");
    }

    @ParameterizedTest
    @MethodSource("provideVPrefixTestCases")
    void when_vPrefixHandling_then_worksCorrectly(String versionWithPrefix, String versionWithoutPrefix) {
        val resultWithPrefix    = SemVerFunctionLibrary.parse(Val.of(versionWithPrefix));
        val resultWithoutPrefix = SemVerFunctionLibrary.parse(Val.of(versionWithoutPrefix));

        assertThatVal(resultWithPrefix).hasValue();
        assertThatVal(resultWithoutPrefix).hasValue();

        val versionFieldWithPrefix    = resultWithPrefix.get().get("version").asText();
        val versionFieldWithoutPrefix = resultWithoutPrefix.get().get("version").asText();

        assertThat(versionFieldWithPrefix).isEqualTo(versionFieldWithoutPrefix);
    }

    private static Stream<Arguments> provideVPrefixTestCases() {
        return Stream.of(Arguments.of("v1.0.0", "1.0.0"), Arguments.of("V2.3.5", "2.3.5"),
                Arguments.of("v1.0.0-alpha", "1.0.0-alpha"), Arguments.of("V3.2.1+build.123", "3.2.1+build.123"));
    }

    /* Comparison Tests */

    @ParameterizedTest
    @MethodSource("provideCompareTestCases")
    void when_compare_then_returnsCorrectResult(String version1, String version2, int expected) {
        val actual = SemVerFunctionLibrary.compare(Val.of(version1), Val.of(version2));

        assertThatVal(actual).hasValue();
        assertThat(actual.get().asInt()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideCompareTestCases() {
        return Stream.of(Arguments.of("1.0.0", "1.0.0", 0), Arguments.of("1.0.0", "2.0.0", -1),
                Arguments.of("2.0.0", "1.0.0", 1), Arguments.of("1.0.0", "1.1.0", -1),
                Arguments.of("1.1.0", "1.0.0", 1), Arguments.of("1.0.0", "1.0.1", -1),
                Arguments.of("1.0.1", "1.0.0", 1), Arguments.of("1.0.0", "1.0.0-alpha", 1),
                Arguments.of("1.0.0-alpha", "1.0.0", -1), Arguments.of("1.0.0-alpha", "1.0.0-beta", -1),
                Arguments.of("1.0.0-beta", "1.0.0-alpha", 1), Arguments.of("1.0.0-alpha.1", "1.0.0-alpha.2", -1),
                Arguments.of("1.0.0+build.1", "1.0.0+build.2", 0), Arguments.of("v1.0.0", "1.0.0", 0),
                Arguments.of("2.3.5", "v2.3.5", 0));
    }

    @ParameterizedTest
    @MethodSource("provideEqualsTestCases")
    void when_equals_then_returnsCorrectResult(String version1, String version2, boolean expected) {
        val actual = SemVerFunctionLibrary.equals(Val.of(version1), Val.of(version2));

        assertThatVal(actual).hasValue();
        assertThat(actual.get().booleanValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideEqualsTestCases() {
        return Stream.of(Arguments.of("1.0.0", "1.0.0", true), Arguments.of("1.0.0", "2.0.0", false),
                Arguments.of("1.0.0", "1.0.1", false), Arguments.of("1.0.0-alpha", "1.0.0-alpha", true),
                Arguments.of("1.0.0-alpha", "1.0.0-beta", false), Arguments.of("1.0.0+build.1", "1.0.0+build.2", true),
                Arguments.of("v1.0.0", "1.0.0", true), Arguments.of("V2.0.0", "v2.0.0", true));
    }

    @ParameterizedTest
    @MethodSource("provideIsLowerTestCases")
    void when_isLower_then_returnsCorrectResult(String version1, String version2, boolean expected) {
        val actual = SemVerFunctionLibrary.isLower(Val.of(version1), Val.of(version2));

        assertThatVal(actual).hasValue();
        assertThat(actual.get().booleanValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideIsLowerTestCases() {
        return Stream.of(Arguments.of("1.0.0", "2.0.0", true), Arguments.of("2.0.0", "1.0.0", false),
                Arguments.of("1.0.0", "1.0.0", false), Arguments.of("1.0.0", "1.1.0", true),
                Arguments.of("1.0.0-alpha", "1.0.0", true), Arguments.of("1.0.0-alpha", "1.0.0-beta", true),
                Arguments.of("v1.0.0", "v2.0.0", true));
    }

    @ParameterizedTest
    @MethodSource("provideIsHigherTestCases")
    void when_isHigher_then_returnsCorrectResult(String version1, String version2, boolean expected) {
        val actual = SemVerFunctionLibrary.isHigher(Val.of(version1), Val.of(version2));

        assertThatVal(actual).hasValue();
        assertThat(actual.get().booleanValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideIsHigherTestCases() {
        return Stream.of(Arguments.of("2.0.0", "1.0.0", true), Arguments.of("1.0.0", "2.0.0", false),
                Arguments.of("1.0.0", "1.0.0", false), Arguments.of("1.1.0", "1.0.0", true),
                Arguments.of("1.0.0", "1.0.0-alpha", true), Arguments.of("1.0.0-beta", "1.0.0-alpha", true),
                Arguments.of("v2.0.0", "v1.0.0", true));
    }

    @ParameterizedTest
    @MethodSource("provideIsLowerOrEqualTestCases")
    void when_isLowerOrEqual_then_returnsCorrectResult(String version1, String version2, boolean expected) {
        val actual = SemVerFunctionLibrary.isLowerOrEqual(Val.of(version1), Val.of(version2));

        assertThatVal(actual).hasValue();
        assertThat(actual.get().booleanValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideIsLowerOrEqualTestCases() {
        return Stream.of(Arguments.of("1.0.0", "2.0.0", true), Arguments.of("2.0.0", "1.0.0", false),
                Arguments.of("1.0.0", "1.0.0", true), Arguments.of("1.0.0", "1.1.0", true),
                Arguments.of("1.0.0-alpha", "1.0.0", true), Arguments.of("v1.0.0", "v1.0.0", true));
    }

    @ParameterizedTest
    @MethodSource("provideIsHigherOrEqualTestCases")
    void when_isHigherOrEqual_then_returnsCorrectResult(String version1, String version2, boolean expected) {
        val actual = SemVerFunctionLibrary.isHigherOrEqual(Val.of(version1), Val.of(version2));

        assertThatVal(actual).hasValue();
        assertThat(actual.get().booleanValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideIsHigherOrEqualTestCases() {
        return Stream.of(Arguments.of("2.0.0", "1.0.0", true), Arguments.of("1.0.0", "2.0.0", false),
                Arguments.of("1.0.0", "1.0.0", true), Arguments.of("1.1.0", "1.0.0", true),
                Arguments.of("1.0.0", "1.0.0-alpha", true), Arguments.of("v1.0.0", "v1.0.0", true));
    }

    @ParameterizedTest
    @ValueSource(strings = { "1.0", "invalid", "" })
    void when_compare_withInvalidVersion_then_returnsError(String invalidVersion) {
        val actual = SemVerFunctionLibrary.compare(Val.of(invalidVersion), Val.of("1.0.0"));
        assertThatVal(actual).isError();
        assertThat(actual.getMessage()).contains("Invalid version");
    }

    /* Version Component Comparison Tests */

    @ParameterizedTest
    @MethodSource("provideHaveSameMajorTestCases")
    void when_haveSameMajor_then_returnsCorrectResult(String version1, String version2, boolean expected) {
        val actual = SemVerFunctionLibrary.haveSameMajor(Val.of(version1), Val.of(version2));

        assertThatVal(actual).hasValue();
        assertThat(actual.get().booleanValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideHaveSameMajorTestCases() {
        return Stream.of(Arguments.of("1.0.0", "1.5.3", true), Arguments.of("1.0.0", "2.0.0", false),
                Arguments.of("2.3.5", "2.8.1", true), Arguments.of("1.0.0-alpha", "1.0.0-beta", true),
                Arguments.of("v1.0.0", "1.9.9", true), Arguments.of("3.0.0", "v3.5.2", true));
    }

    @ParameterizedTest
    @MethodSource("provideHaveSameMinorTestCases")
    void when_haveSameMinor_then_returnsCorrectResult(String version1, String version2, boolean expected) {
        val actual = SemVerFunctionLibrary.haveSameMinor(Val.of(version1), Val.of(version2));

        assertThatVal(actual).hasValue();
        assertThat(actual.get().booleanValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideHaveSameMinorTestCases() {
        return Stream.of(Arguments.of("1.2.0", "1.2.5", true), Arguments.of("1.2.0", "1.3.0", false),
                Arguments.of("1.2.0", "2.2.0", false), Arguments.of("2.3.1", "2.3.9", true),
                Arguments.of("v1.5.0", "1.5.8", true), Arguments.of("1.0.0-alpha", "1.0.5-beta", true));
    }

    @ParameterizedTest
    @MethodSource("provideHaveSamePatchTestCases")
    void when_haveSamePatch_then_returnsCorrectResult(String version1, String version2, boolean expected) {
        val actual = SemVerFunctionLibrary.haveSamePatch(Val.of(version1), Val.of(version2));

        assertThatVal(actual).hasValue();
        assertThat(actual.get().booleanValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideHaveSamePatchTestCases() {
        return Stream.of(Arguments.of("1.2.3", "1.2.3", true), Arguments.of("1.2.3", "1.2.4", false),
                Arguments.of("1.2.3", "1.3.3", false), Arguments.of("1.2.3", "2.2.3", false),
                Arguments.of("1.2.3-alpha", "1.2.3-beta", true), Arguments.of("v1.2.3", "1.2.3+build", true));
    }

    /* Compatibility Tests */

    @ParameterizedTest
    @MethodSource("provideIsCompatibleWithTestCases")
    void when_isCompatibleWith_then_returnsCorrectResult(String version1, String version2, boolean expected) {
        val actual = SemVerFunctionLibrary.isCompatibleWith(Val.of(version1), Val.of(version2));

        assertThatVal(actual).hasValue();
        assertThat(actual.get().booleanValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideIsCompatibleWithTestCases() {
        return Stream.of(Arguments.of("2.5.0", "2.0.0", true), Arguments.of("2.0.0", "3.0.0", false),
                Arguments.of("1.5.0", "1.0.0", true), Arguments.of("0.2.0", "0.2.5", true),
                Arguments.of("0.2.0", "0.3.0", false), Arguments.of("0.1.0", "0.1.5", true),
                Arguments.of("v1.5.0", "v1.2.0", true), Arguments.of("3.0.0", "2.9.9", false));
    }

    @ParameterizedTest
    @MethodSource("provideIsAtLeastTestCases")
    void when_isAtLeast_then_returnsCorrectResult(String version, String minimum, boolean expected) {
        val actual = SemVerFunctionLibrary.isAtLeast(Val.of(version), Val.of(minimum));

        assertThatVal(actual).hasValue();
        assertThat(actual.get().booleanValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideIsAtLeastTestCases() {
        return Stream.of(Arguments.of("2.0.0", "1.0.0", true), Arguments.of("1.0.0", "2.0.0", false),
                Arguments.of("2.0.0", "2.0.0", true), Arguments.of("2.1.0", "2.0.0", true),
                Arguments.of("v2.5.0", "v2.0.0", true));
    }

    @ParameterizedTest
    @MethodSource("provideIsAtMostTestCases")
    void when_isAtMost_then_returnsCorrectResult(String version, String maximum, boolean expected) {
        val actual = SemVerFunctionLibrary.isAtMost(Val.of(version), Val.of(maximum));

        assertThatVal(actual).hasValue();
        assertThat(actual.get().booleanValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideIsAtMostTestCases() {
        return Stream.of(Arguments.of("1.0.0", "2.0.0", true), Arguments.of("2.0.0", "1.0.0", false),
                Arguments.of("2.0.0", "2.0.0", true), Arguments.of("2.0.0", "2.1.0", true),
                Arguments.of("v1.5.0", "v2.0.0", true));
    }

    @ParameterizedTest
    @MethodSource("provideIsBetweenTestCases")
    void when_isBetween_then_returnsCorrectResult(String version, String minimum, String maximum, boolean expected) {
        val actual = SemVerFunctionLibrary.isBetween(Val.of(version), Val.of(minimum), Val.of(maximum));

        assertThatVal(actual).hasValue();
        assertThat(actual.get().booleanValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideIsBetweenTestCases() {
        return Stream.of(Arguments.of("2.0.0", "1.0.0", "3.0.0", true), Arguments.of("1.0.0", "1.0.0", "3.0.0", true),
                Arguments.of("3.0.0", "1.0.0", "3.0.0", true), Arguments.of("0.5.0", "1.0.0", "3.0.0", false),
                Arguments.of("4.0.0", "1.0.0", "3.0.0", false), Arguments.of("v2.5.0", "v2.0.0", "v3.0.0", true));
    }

    @ParameterizedTest
    @ValueSource(strings = { "1.0", "invalid" })
    void when_isBetween_withInvalidVersion_then_returnsError(String invalidVersion) {
        val actual = SemVerFunctionLibrary.isBetween(Val.of(invalidVersion), Val.of("1.0.0"), Val.of("2.0.0"));
        assertThatVal(actual).isError();
        assertThat(actual.getMessage()).contains("Invalid version");
    }

    /* Pre-release and Stability Tests */

    @ParameterizedTest
    @MethodSource("provideIsPreReleaseTestCases")
    void when_isPreRelease_then_returnsCorrectResult(String version, boolean expected) {
        val actual = SemVerFunctionLibrary.isPreRelease(Val.of(version));

        assertThatVal(actual).hasValue();
        assertThat(actual.get().booleanValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideIsPreReleaseTestCases() {
        return Stream.of(Arguments.of("1.0.0-alpha", true), Arguments.of("1.0.0-beta.1", true),
                Arguments.of("1.0.0-rc.2", true), Arguments.of("1.0.0", false), Arguments.of("1.0.0+build.123", false),
                Arguments.of("v2.0.0-alpha", true));
    }

    @ParameterizedTest
    @MethodSource("provideIsStableTestCases")
    void when_isStable_then_returnsCorrectResult(String version, boolean expected) {
        val actual = SemVerFunctionLibrary.isStable(Val.of(version));

        assertThatVal(actual).hasValue();
        assertThat(actual.get().booleanValue()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideIsStableTestCases() {
        return Stream.of(Arguments.of("1.0.0", true), Arguments.of("1.0.0+build.123", true),
                Arguments.of("1.0.0-alpha", false), Arguments.of("1.0.0-beta.1", false), Arguments.of("v2.0.0", true),
                Arguments.of("v2.0.0-rc.1", false));
    }

    @ParameterizedTest
    @ValueSource(strings = { "1.0", "invalid" })
    void when_isPreRelease_withInvalidVersion_then_returnsError(String invalidVersion) {
        val actual = SemVerFunctionLibrary.isPreRelease(Val.of(invalidVersion));
        assertThatVal(actual).isError();
        assertThat(actual.getMessage()).contains("Invalid version");
    }

    /* Component Extraction Tests */

    @ParameterizedTest
    @MethodSource("provideGetMajorTestCases")
    void when_getMajor_then_returnsCorrectValue(String version, int expected) {
        val actual = SemVerFunctionLibrary.getMajor(Val.of(version));

        assertThatVal(actual).hasValue();
        assertThat(actual.get().asInt()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideGetMajorTestCases() {
        return Stream.of(Arguments.of("1.0.0", 1), Arguments.of("2.3.5", 2), Arguments.of("10.20.30", 10),
                Arguments.of("0.1.0", 0), Arguments.of("v3.0.0", 3), Arguments.of("1.0.0-alpha", 1));
    }

    @ParameterizedTest
    @MethodSource("provideGetMinorTestCases")
    void when_getMinor_then_returnsCorrectValue(String version, int expected) {
        val actual = SemVerFunctionLibrary.getMinor(Val.of(version));

        assertThatVal(actual).hasValue();
        assertThat(actual.get().asInt()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideGetMinorTestCases() {
        return Stream.of(Arguments.of("1.0.0", 0), Arguments.of("2.3.5", 3), Arguments.of("10.20.30", 20),
                Arguments.of("0.1.0", 1), Arguments.of("v3.5.0", 5), Arguments.of("1.2.0-beta", 2));
    }

    @ParameterizedTest
    @MethodSource("provideGetPatchTestCases")
    void when_getPatch_then_returnsCorrectValue(String version, int expected) {
        val actual = SemVerFunctionLibrary.getPatch(Val.of(version));

        assertThatVal(actual).hasValue();
        assertThat(actual.get().asInt()).isEqualTo(expected);
    }

    private static Stream<Arguments> provideGetPatchTestCases() {
        return Stream.of(Arguments.of("1.0.0", 0), Arguments.of("2.3.5", 5), Arguments.of("10.20.30", 30),
                Arguments.of("0.1.2", 2), Arguments.of("v3.5.7", 7), Arguments.of("1.2.3-rc.1", 3));
    }

    @ParameterizedTest
    @ValueSource(strings = { "1.0", "invalid" })
    void when_getMajor_withInvalidVersion_then_returnsError(String invalidVersion) {
        val actual = SemVerFunctionLibrary.getMajor(Val.of(invalidVersion));
        assertThatVal(actual).isError();
        assertThat(actual.getMessage()).contains("Invalid version");
    }

    /* Integration Tests */

    @Test
    void when_versionComparisonWorkflow_then_worksCorrectly() {
        val clientVersion = "2.5.0";
        val minVersion    = "2.0.0";
        val maxVersion    = "3.0.0";

        val isValid      = SemVerFunctionLibrary.isValid(Val.of(clientVersion));
        val isCompatible = SemVerFunctionLibrary.isCompatibleWith(Val.of(clientVersion), Val.of(minVersion));
        val isBetween    = SemVerFunctionLibrary.isBetween(Val.of(clientVersion), Val.of(minVersion),
                Val.of(maxVersion));
        val isStable     = SemVerFunctionLibrary.isStable(Val.of(clientVersion));

        assertThatVal(isValid).isTrue();
        assertThatVal(isCompatible).isTrue();
        assertThatVal(isBetween).isTrue();
        assertThatVal(isStable).isTrue();
    }

    @Test
    void when_preReleaseComparison_then_worksCorrectly() {
        val alpha  = "1.0.0-alpha";
        val beta   = "1.0.0-beta";
        val rc     = "1.0.0-rc.1";
        val stable = "1.0.0";

        assertThatVal(SemVerFunctionLibrary.isLower(Val.of(alpha), Val.of(beta))).isTrue();
        assertThatVal(SemVerFunctionLibrary.isLower(Val.of(beta), Val.of(rc))).isTrue();
        assertThatVal(SemVerFunctionLibrary.isLower(Val.of(rc), Val.of(stable))).isTrue();

        assertThatVal(SemVerFunctionLibrary.isPreRelease(Val.of(alpha))).isTrue();
        assertThatVal(SemVerFunctionLibrary.isPreRelease(Val.of(stable))).isFalse();
        assertThatVal(SemVerFunctionLibrary.isStable(Val.of(stable))).isTrue();
    }

    @Test
    void when_buildMetadataHandling_then_ignoredInComparison() {
        val version1 = "1.0.0+build.1";
        val version2 = "1.0.0+build.2";
        val version3 = "1.0.0";

        assertThatVal(SemVerFunctionLibrary.equals(Val.of(version1), Val.of(version2))).isTrue();
        assertThatVal(SemVerFunctionLibrary.equals(Val.of(version1), Val.of(version3))).isTrue();
        assertThatVal(SemVerFunctionLibrary.compare(Val.of(version1), Val.of(version2))).hasValue();
        assertThat(SemVerFunctionLibrary.compare(Val.of(version1), Val.of(version2)).get().asInt()).isZero();
    }

    @Test
    void when_versionComponentExtraction_then_worksCorrectly() {
        val version = "2.5.8-rc.1+build.456";

        val parsed = SemVerFunctionLibrary.parse(Val.of(version));
        val major  = SemVerFunctionLibrary.getMajor(Val.of(version));
        val minor  = SemVerFunctionLibrary.getMinor(Val.of(version));
        val patch  = SemVerFunctionLibrary.getPatch(Val.of(version));

        assertThatVal(parsed).hasValue();
        assertThat(parsed.get().get("major").asInt()).isEqualTo(2);
        assertThat(parsed.get().get("minor").asInt()).isEqualTo(5);
        assertThat(parsed.get().get("patch").asInt()).isEqualTo(8);
        assertThat(parsed.get().get("preRelease").asText()).isEqualTo("rc.1");
        assertThat(parsed.get().get("buildMetadata").asText()).isEqualTo("build.456");

        assertThatVal(major).hasValue();
        assertThatVal(minor).hasValue();
        assertThatVal(patch).hasValue();

        assertThat(major.get().asInt()).isEqualTo(2);
        assertThat(minor.get().asInt()).isEqualTo(5);
        assertThat(patch.get().asInt()).isEqualTo(8);
    }

    @Test
    void when_compatibilityCheckForMajorZero_then_requiresSameMinor() {
        assertThatVal(SemVerFunctionLibrary.isCompatibleWith(Val.of("0.2.5"), Val.of("0.2.0"))).isTrue();
        assertThatVal(SemVerFunctionLibrary.isCompatibleWith(Val.of("0.3.0"), Val.of("0.2.0"))).isFalse();
        assertThatVal(SemVerFunctionLibrary.isCompatibleWith(Val.of("0.2.9"), Val.of("0.2.1"))).isTrue();
    }

    @Test
    void when_compatibilityCheckForMajorNonZero_then_requiresSameMajor() {
        assertThatVal(SemVerFunctionLibrary.isCompatibleWith(Val.of("1.5.0"), Val.of("1.0.0"))).isTrue();
        assertThatVal(SemVerFunctionLibrary.isCompatibleWith(Val.of("1.9.9"), Val.of("1.0.0"))).isTrue();
        assertThatVal(SemVerFunctionLibrary.isCompatibleWith(Val.of("2.0.0"), Val.of("1.9.9"))).isFalse();
    }

    @Test
    void when_rangeCheckWithBoundaries_then_includesEndpoints() {
        val version = "2.0.0";
        val minimum = "2.0.0";
        val maximum = "3.0.0";

        assertThatVal(SemVerFunctionLibrary.isBetween(Val.of(version), Val.of(minimum), Val.of(maximum))).isTrue();
        assertThatVal(SemVerFunctionLibrary.isBetween(Val.of("3.0.0"), Val.of(minimum), Val.of(maximum))).isTrue();
        assertThatVal(SemVerFunctionLibrary.isBetween(Val.of("3.0.1"), Val.of(minimum), Val.of(maximum))).isFalse();
    }

    @Test
    void when_versionWithLeadingV_then_handledConsistently() {
        val v1 = "v1.2.3";
        val v2 = "1.2.3";
        val v3 = "V1.2.3";

        assertThatVal(SemVerFunctionLibrary.equals(Val.of(v1), Val.of(v2))).isTrue();
        assertThatVal(SemVerFunctionLibrary.equals(Val.of(v1), Val.of(v3))).isTrue();
        assertThatVal(SemVerFunctionLibrary.compare(Val.of(v1), Val.of(v2))).hasValue();
        assertThat(SemVerFunctionLibrary.compare(Val.of(v1), Val.of(v2)).get().asInt()).isZero();
    }
}
