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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.sapl.api.interpreter.Val;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.sapl.assertj.SaplAssertions.assertThatVal;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive test suite for SemVerFunctionLibrary.
 * Tests semantic version parsing, comparison, validation, and range operations.
 */
class SemVerFunctionLibraryTests {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    @ParameterizedTest
    @ValueSource(strings = { "1.0.0", "2.3.5", "0.0.1", "10.20.30", "1.0.0-alpha", "1.0.0-beta.1",
            "1.0.0-rc.2+build.456", "v1.0.0", "1.2.3-alpha.beta+build.123" })
    void when_isValid_withValidVersions_then_returnsTrue(String version) {
        val result = SemVerFunctionLibrary.isValid(Val.of(version));
        assertThatVal(result).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "1.0", "1", "a.b.c", "1.0.0.0", "", "1.0.0-", "1.0.0+", "v", "vv1.0.0", "1.2.3.4",
            "V1.0.0" })
    void when_isValid_withInvalidVersions_then_returnsFalse(String version) {
        val result = SemVerFunctionLibrary.isValid(Val.of(version));
        assertThatVal(result).isFalse();
    }

    @Test
    void when_uppercaseVPrefix_then_notSupported() {
        assertThatVal(SemVerFunctionLibrary.isValid(Val.of("V1.0.0"))).isFalse();
        assertThatVal(SemVerFunctionLibrary.parse(Val.of("V1.0.0"))).isError();
    }

    @Test
    void when_parse_withSimpleVersion_then_returnsCorrectComponents() {
        val result = SemVerFunctionLibrary.parse(Val.of("2.3.5"));

        assertThatVal(result).hasValue();
        val parsed = result.get();
        assertThat(parsed.get("version").asText()).isEqualTo("2.3.5");
        assertThat(parsed.get("major").asInt()).isEqualTo(2);
        assertThat(parsed.get("minor").asInt()).isEqualTo(3);
        assertThat(parsed.get("patch").asInt()).isEqualTo(5);
        assertThat(parsed.get("isStable").asBoolean()).isTrue();
        assertThat(parsed.get("isPreRelease").asBoolean()).isFalse();
        assertThat(parsed.get("preRelease").size()).isZero();
        assertThat(parsed.get("buildMetadata").size()).isZero();
    }

    @Test
    void when_parse_withPreReleaseVersion_then_returnsPreReleaseArray() {
        val result = SemVerFunctionLibrary.parse(Val.of("1.0.0-alpha.beta.1"));

        assertThatVal(result).hasValue();
        val parsed = result.get();
        assertThat(parsed.get("isStable").asBoolean()).isFalse();
        assertThat(parsed.get("isPreRelease").asBoolean()).isTrue();

        val preReleaseArray = parsed.get("preRelease");
        assertThat(preReleaseArray.isArray()).isTrue();
        assertThat(preReleaseArray.size()).isEqualTo(3);
        assertThat(preReleaseArray.get(0).asText()).isEqualTo("alpha");
        assertThat(preReleaseArray.get(1).asText()).isEqualTo("beta");
        assertThat(preReleaseArray.get(2).asText()).isEqualTo("1");
    }

    @Test
    void when_parse_withBuildMetadata_then_returnsBuildMetadataArray() {
        val result = SemVerFunctionLibrary.parse(Val.of("1.0.0+build.123.sha.5114f85"));

        assertThatVal(result).hasValue();
        val parsed = result.get();

        val buildMetadataArray = parsed.get("buildMetadata");
        assertThat(buildMetadataArray.isArray()).isTrue();
        assertThat(buildMetadataArray.size()).isEqualTo(4);
        assertThat(buildMetadataArray.get(0).asText()).isEqualTo("build");
        assertThat(buildMetadataArray.get(1).asText()).isEqualTo("123");
        assertThat(buildMetadataArray.get(2).asText()).isEqualTo("sha");
        assertThat(buildMetadataArray.get(3).asText()).isEqualTo("5114f85");
    }

    @Test
    void when_parse_withFullVersion_then_returnsAllComponents() {
        val result = SemVerFunctionLibrary.parse(Val.of("1.2.3-rc.1+build.456"));

        assertThatVal(result).hasValue();
        val parsed = result.get();
        assertThat(parsed.get("version").asText()).isEqualTo("1.2.3-rc.1+build.456");
        assertThat(parsed.get("major").asInt()).isEqualTo(1);
        assertThat(parsed.get("minor").asInt()).isEqualTo(2);
        assertThat(parsed.get("patch").asInt()).isEqualTo(3);
        assertThat(parsed.get("isStable").asBoolean()).isFalse();
        assertThat(parsed.get("isPreRelease").asBoolean()).isTrue();

        val preRelease = parsed.get("preRelease");
        assertThat(preRelease.size()).isEqualTo(2);
        assertThat(preRelease.get(0).asText()).isEqualTo("rc");
        assertThat(preRelease.get(1).asText()).isEqualTo("1");

        val buildMetadata = parsed.get("buildMetadata");
        assertThat(buildMetadata.size()).isEqualTo(2);
        assertThat(buildMetadata.get(0).asText()).isEqualTo("build");
        assertThat(buildMetadata.get(1).asText()).isEqualTo("456");
    }

    @Test
    void when_parse_withVPrefix_then_stripsPrefix() {
        val result = SemVerFunctionLibrary.parse(Val.of("v2.5.8"));

        assertThatVal(result).hasValue();
        val parsed = result.get();
        assertThat(parsed.get("version").asText()).isEqualTo("2.5.8");
        assertThat(parsed.get("major").asInt()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = { "1.0", "invalid", "1.0.0.0", "", "abc" })
    void when_parse_withInvalidVersion_then_returnsError(String invalidVersion) {
        val result = SemVerFunctionLibrary.parse(Val.of(invalidVersion));
        assertThatVal(result).isError();
        assertThat(result.getMessage()).contains("Invalid semantic version");
    }

    @ParameterizedTest
    @CsvSource({ "1.0.0, 1.0.0, 0", "1.0.0, 2.0.0, -1", "2.0.0, 1.0.0, 1", "1.0.0, 1.1.0, -1", "1.1.0, 1.0.0, 1",
            "1.0.0, 1.0.1, -1", "1.0.1, 1.0.0, 1", "1.0.0, 1.0.0-alpha, 1", "1.0.0-alpha, 1.0.0, -1",
            "1.0.0-alpha, 1.0.0-beta, -1", "1.0.0-beta, 1.0.0-alpha, 1", "1.0.0+build.1, 1.0.0+build.2, 0",
            "v1.0.0, 1.0.0, 0" })
    void when_compare_withVersions_then_returnsCorrectResult(String version1, String version2, int expected) {
        val result = SemVerFunctionLibrary.compare(Val.of(version1), Val.of(version2));

        assertThatVal(result).hasValue();
        assertThat(result.get().asInt()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = { "1.0", "invalid", "", "a.b.c" })
    void when_compare_withInvalidVersion_then_returnsError(String invalidVersion) {
        val result = SemVerFunctionLibrary.compare(Val.of(invalidVersion), Val.of("1.0.0"));
        assertThatVal(result).isError();
        assertThat(result.getMessage()).contains(SemVerFunctionLibrary.INVALID_VERSION_IN_COMPARISON);
    }

    @ParameterizedTest
    @CsvSource({ "1.0.0, 1.0.0, true", "1.0.0, 2.0.0, false", "1.0.0+build.1, 1.0.0+build.2, true",
            "1.0.0-alpha, 1.0.0-alpha, true", "1.0.0-alpha, 1.0.0-beta, false", "v1.0.0, 1.0.0, true" })
    void when_equals_withVersions_then_returnsCorrectResult(String version1, String version2, boolean expected) {
        val result = SemVerFunctionLibrary.equals(Val.of(version1), Val.of(version2));

        assertThatVal(result).hasValue();
        assertThat(result.get().booleanValue()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({ "1.0.0, 2.0.0, true", "2.0.0, 1.0.0, false", "1.0.0, 1.0.0, false", "1.0.0-alpha, 1.0.0, true",
            "1.0.0-alpha, 1.0.0-beta, true" })
    void when_isLower_withVersions_then_returnsCorrectResult(String version1, String version2, boolean expected) {
        val result = SemVerFunctionLibrary.isLower(Val.of(version1), Val.of(version2));

        assertThatVal(result).hasValue();
        assertThat(result.get().booleanValue()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({ "2.0.0, 1.0.0, true", "1.0.0, 2.0.0, false", "1.0.0, 1.0.0, false", "1.0.0, 1.0.0-alpha, true",
            "1.0.0-beta, 1.0.0-alpha, true" })
    void when_isHigher_withVersions_then_returnsCorrectResult(String version1, String version2, boolean expected) {
        val result = SemVerFunctionLibrary.isHigher(Val.of(version1), Val.of(version2));

        assertThatVal(result).hasValue();
        assertThat(result.get().booleanValue()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({ "1.0.0, 2.0.0, true", "2.0.0, 1.0.0, false", "1.0.0, 1.0.0, true", "1.0.0-alpha, 1.0.0, true" })
    void when_isLowerOrEqual_withVersions_then_returnsCorrectResult(String version1, String version2,
            boolean expected) {
        val result = SemVerFunctionLibrary.isLowerOrEqual(Val.of(version1), Val.of(version2));

        assertThatVal(result).hasValue();
        assertThat(result.get().booleanValue()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({ "2.0.0, 1.0.0, true", "1.0.0, 2.0.0, false", "1.0.0, 1.0.0, true", "1.0.0, 1.0.0-alpha, true" })
    void when_isHigherOrEqual_withVersions_then_returnsCorrectResult(String version1, String version2,
            boolean expected) {
        val result = SemVerFunctionLibrary.isHigherOrEqual(Val.of(version1), Val.of(version2));

        assertThatVal(result).hasValue();
        assertThat(result.get().booleanValue()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({ "1.0.0, 1.5.3, true", "1.0.0, 2.0.0, false", "2.3.5, 2.8.1, true", "1.0.0-alpha, 1.0.0-beta, true" })
    void when_haveSameMajor_withVersions_then_returnsCorrectResult(String version1, String version2, boolean expected) {
        val result = SemVerFunctionLibrary.haveSameMajor(Val.of(version1), Val.of(version2));

        assertThatVal(result).hasValue();
        assertThat(result.get().booleanValue()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({ "1.2.0, 1.2.5, true", "1.2.0, 1.3.0, false", "1.2.0, 2.2.0, false", "2.3.1, 2.3.9, true" })
    void when_haveSameMinor_withVersions_then_returnsCorrectResult(String version1, String version2, boolean expected) {
        val result = SemVerFunctionLibrary.haveSameMinor(Val.of(version1), Val.of(version2));

        assertThatVal(result).hasValue();
        assertThat(result.get().booleanValue()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({ "1.2.3, 1.2.3, true", "1.2.3, 1.2.4, false", "1.2.3, 1.3.3, false", "1.2.3-alpha, 1.2.3-beta, true" })
    void when_haveSamePatch_withVersions_then_returnsCorrectResult(String version1, String version2, boolean expected) {
        val result = SemVerFunctionLibrary.haveSamePatch(Val.of(version1), Val.of(version2));

        assertThatVal(result).hasValue();
        assertThat(result.get().booleanValue()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({ "2.5.0, 2.0.0, true", "2.0.0, 3.0.0, false", "0.2.0, 0.2.5, true", "0.2.0, 0.3.0, false",
            "1.5.0, 1.0.0, true" })
    void when_isCompatibleWith_withVersions_then_returnsCorrectResult(String version1, String version2,
            boolean expected) {
        val result = SemVerFunctionLibrary.isCompatibleWith(Val.of(version1), Val.of(version2));

        assertThatVal(result).hasValue();
        assertThat(result.get().booleanValue()).isEqualTo(expected);
    }

    @Test
    void when_isCompatibleWith_majorZero_then_requiresSameMinor() {
        assertThatVal(SemVerFunctionLibrary.isCompatibleWith(Val.of("0.2.5"), Val.of("0.2.0"))).isTrue();
        assertThatVal(SemVerFunctionLibrary.isCompatibleWith(Val.of("0.3.0"), Val.of("0.2.0"))).isFalse();
    }

    @Test
    void when_isCompatibleWith_majorNonZero_then_requiresSameMajor() {
        assertThatVal(SemVerFunctionLibrary.isCompatibleWith(Val.of("1.5.0"), Val.of("1.0.0"))).isTrue();
        assertThatVal(SemVerFunctionLibrary.isCompatibleWith(Val.of("2.0.0"), Val.of("1.9.9"))).isFalse();
    }

    @ParameterizedTest
    @CsvSource({ "2.0.0, 1.0.0, true", "1.0.0, 2.0.0, false", "2.0.0, 2.0.0, true" })
    void when_isAtLeast_withVersions_then_returnsCorrectResult(String version, String minimum, boolean expected) {
        val result = SemVerFunctionLibrary.isAtLeast(Val.of(version), Val.of(minimum));

        assertThatVal(result).hasValue();
        assertThat(result.get().booleanValue()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({ "1.0.0, 2.0.0, true", "2.0.0, 1.0.0, false", "2.0.0, 2.0.0, true" })
    void when_isAtMost_withVersions_then_returnsCorrectResult(String version, String maximum, boolean expected) {
        val result = SemVerFunctionLibrary.isAtMost(Val.of(version), Val.of(maximum));

        assertThatVal(result).hasValue();
        assertThat(result.get().booleanValue()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({ "2.0.0, 1.0.0, 3.0.0, true", "1.0.0, 1.0.0, 3.0.0, true", "3.0.0, 1.0.0, 3.0.0, true",
            "0.5.0, 1.0.0, 3.0.0, false", "4.0.0, 1.0.0, 3.0.0, false" })
    void when_isBetween_withVersions_then_returnsCorrectResult(String version, String minimum, String maximum,
            boolean expected) {
        val result = SemVerFunctionLibrary.isBetween(Val.of(version), Val.of(minimum), Val.of(maximum));

        assertThatVal(result).hasValue();
        assertThat(result.get().booleanValue()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = { "1.0", "invalid" })
    void when_isBetween_withInvalidVersion_then_returnsError(String invalidVersion) {
        val result = SemVerFunctionLibrary.isBetween(Val.of(invalidVersion), Val.of("1.0.0"), Val.of("2.0.0"));
        assertThatVal(result).isError();
        assertThat(result.getMessage()).contains(SemVerFunctionLibrary.INVALID_VERSION_IN_COMPARISON);
    }

    @ParameterizedTest
    @CsvSource({ "1.0.0-alpha, true", "1.0.0-beta.1, true", "1.0.0, false", "1.0.0+build.123, false" })
    void when_isPreRelease_withVersions_then_returnsCorrectResult(String version, boolean expected) {
        val result = SemVerFunctionLibrary.isPreRelease(Val.of(version));

        assertThatVal(result).hasValue();
        assertThat(result.get().booleanValue()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({ "1.0.0, true", "1.0.0+build.123, true", "1.0.0-alpha, false", "1.0.0-beta.1, false" })
    void when_isStable_withVersions_then_returnsCorrectResult(String version, boolean expected) {
        val result = SemVerFunctionLibrary.isStable(Val.of(version));

        assertThatVal(result).hasValue();
        assertThat(result.get().booleanValue()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = { "1.0", "invalid" })
    void when_isPreRelease_withInvalidVersion_then_returnsError(String invalidVersion) {
        val result = SemVerFunctionLibrary.isPreRelease(Val.of(invalidVersion));
        assertThatVal(result).isError();
        assertThat(result.getMessage()).contains(SemVerFunctionLibrary.INVALID_VERSION);
    }

    @ParameterizedTest
    @CsvSource({ "1.0.0, 1", "2.3.5, 2", "10.20.30, 10", "0.1.0, 0" })
    void when_getMajor_withVersions_then_returnsCorrectValue(String version, int expected) {
        val result = SemVerFunctionLibrary.getMajor(Val.of(version));

        assertThatVal(result).hasValue();
        assertThat(result.get().asInt()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({ "1.0.0, 0", "2.3.5, 3", "10.20.30, 20", "0.1.0, 1" })
    void when_getMinor_withVersions_then_returnsCorrectValue(String version, int expected) {
        val result = SemVerFunctionLibrary.getMinor(Val.of(version));

        assertThatVal(result).hasValue();
        assertThat(result.get().asInt()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({ "1.0.0, 0", "2.3.5, 5", "10.20.30, 30", "0.1.2, 2" })
    void when_getPatch_withVersions_then_returnsCorrectValue(String version, int expected) {
        val result = SemVerFunctionLibrary.getPatch(Val.of(version));

        assertThatVal(result).hasValue();
        assertThat(result.get().asInt()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = { "1.0", "invalid" })
    void when_getMajor_withInvalidVersion_then_returnsError(String invalidVersion) {
        val result = SemVerFunctionLibrary.getMajor(Val.of(invalidVersion));
        assertThatVal(result).isError();
        assertThat(result.getMessage()).contains(SemVerFunctionLibrary.INVALID_VERSION);
    }

    @ParameterizedTest
    @CsvSource({ "2.5.0, '>=2.0.0', true", "1.5.0, '>=2.0.0', false", "2.5.0, '>=2.0.0 <3.0.0', true",
            "3.0.0, '>=2.0.0 <3.0.0', false", "1.2.5, 1.2.x, true", "1.3.0, 1.2.x, false", "1.2.5, '~1.2.3', true",
            "1.3.0, '~1.2.3', false", "1.5.0, '^1.2.3', true", "2.0.0, '^1.2.3', false" })
    void when_satisfies_withRanges_then_returnsCorrectResult(String version, String range, boolean expected) {
        val result = SemVerFunctionLibrary.satisfies(Val.of(version), Val.of(range));

        assertThatVal(result).hasValue();
        assertThat(result.get().booleanValue()).isEqualTo(expected);
    }

    @Test
    void when_satisfies_withHyphenRange_then_worksCorrectly() {
        assertThatVal(SemVerFunctionLibrary.satisfies(Val.of("1.2.3"), Val.of("1.2.3 - 2.3.4"))).isTrue();
        assertThatVal(SemVerFunctionLibrary.satisfies(Val.of("2.0.0"), Val.of("1.2.3 - 2.3.4"))).isTrue();
        assertThatVal(SemVerFunctionLibrary.satisfies(Val.of("3.0.0"), Val.of("1.2.3 - 2.3.4"))).isFalse();
    }

    @Test
    void when_satisfies_withLogicalOr_then_worksCorrectly() {
        assertThatVal(SemVerFunctionLibrary.satisfies(Val.of("1.0.0"), Val.of(">=1.0.0 || >=2.0.0"))).isTrue();
        assertThatVal(SemVerFunctionLibrary.satisfies(Val.of("2.5.0"), Val.of(">=1.0.0 || >=2.0.0"))).isTrue();
        assertThatVal(SemVerFunctionLibrary.satisfies(Val.of("0.5.0"), Val.of(">=1.0.0 || >=2.0.0"))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "1.0", "invalid" })
    void when_satisfies_withInvalidVersion_then_returnsError(String invalidVersion) {
        val result = SemVerFunctionLibrary.satisfies(Val.of(invalidVersion), Val.of(">=1.0.0"));
        assertThatVal(result).isError();
        assertThat(result.getMessage()).contains("Invalid version or range");
    }

    @Test
    void when_satisfies_withInvalidRange_then_returnsFalse() {
        val result = SemVerFunctionLibrary.satisfies(Val.of("1.0.0"), Val.of(">>invalid<<"));
        assertThatVal(result).isFalse();
    }

    @Test
    void when_maxSatisfying_withMatchingVersions_then_returnsHighest() {
        val versions = createStringArray("1.5.0", "2.1.0", "2.5.0", "3.0.0");
        val result   = SemVerFunctionLibrary.maxSatisfying(Val.of(versions), Val.of(">=2.0.0 <3.0.0"));

        assertThatVal(result).hasValue();
        assertThat(result.get().asText()).isEqualTo("2.5.0");
    }

    @Test
    void when_maxSatisfying_withNoMatches_then_returnsNull() {
        val versions = createStringArray("1.5.0", "1.6.0", "1.7.0");
        val result   = SemVerFunctionLibrary.maxSatisfying(Val.of(versions), Val.of(">=2.0.0"));

        assertThatVal(result).hasValue();
        assertThat(result.get().isNull()).isTrue();
    }

    @Test
    void when_maxSatisfying_withEmptyArray_then_returnsNull() {
        val emptyArray = JSON.arrayNode();
        val result     = SemVerFunctionLibrary.maxSatisfying(Val.of(emptyArray), Val.of(">=1.0.0"));

        assertThatVal(result).hasValue();
        assertThat(result.get().isNull()).isTrue();
    }

    @Test
    void when_maxSatisfying_withMixedTypes_then_filtersOnlyStrings() {
        val array = JSON.arrayNode();
        array.add("1.0.0");
        array.add(123);
        array.add("2.0.0");
        array.add(true);
        array.add("1.5.0");

        val result = SemVerFunctionLibrary.maxSatisfying(Val.of(array), Val.of(">=1.0.0"));

        assertThatVal(result).hasValue();
        assertThat(result.get().asText()).isEqualTo("2.0.0");
    }

    @Test
    void when_maxSatisfying_withInvalidRange_then_returnsNull() {
        val versions = createStringArray("1.0.0", "2.0.0");
        val result   = SemVerFunctionLibrary.maxSatisfying(Val.of(versions), Val.of(">>invalid<<"));

        assertThatVal(result).hasValue();
        assertThat(result.get().isNull()).isTrue();
    }

    @Test
    void when_minSatisfying_withMatchingVersions_then_returnsLowest() {
        val versions = createStringArray("1.5.0", "2.1.0", "2.5.0", "3.0.0");
        val result   = SemVerFunctionLibrary.minSatisfying(Val.of(versions), Val.of(">=2.0.0 <3.0.0"));

        assertThatVal(result).hasValue();
        assertThat(result.get().asText()).isEqualTo("2.1.0");
    }

    @Test
    void when_minSatisfying_withNoMatches_then_returnsNull() {
        val versions = createStringArray("1.5.0", "1.6.0", "1.7.0");
        val result   = SemVerFunctionLibrary.minSatisfying(Val.of(versions), Val.of(">=2.0.0"));

        assertThatVal(result).hasValue();
        assertThat(result.get().isNull()).isTrue();
    }

    @Test
    void when_minSatisfying_withEmptyArray_then_returnsNull() {
        val emptyArray = JSON.arrayNode();
        val result     = SemVerFunctionLibrary.minSatisfying(Val.of(emptyArray), Val.of(">=1.0.0"));

        assertThatVal(result).hasValue();
        assertThat(result.get().isNull()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({ "1.2.3, 1.2.3", "1.2, 1.2.0", "1, 1.0.0", "v1.2.3, 1.2.3", "v1.2, 1.2.0" })
    void when_coerce_withPartialVersions_then_normalizes(String input, String expected) {
        val result = SemVerFunctionLibrary.coerce(Val.of(input));

        assertThatVal(result).hasValue();
        assertThat(result.get().asText()).isEqualTo(expected);
    }

    @Test
    void when_coerce_withWhitespace_then_normalizes() {
        val result = SemVerFunctionLibrary.coerce(Val.of("   1.2.3   "));
        assertThatVal(result).hasValue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "abc", "not-a-version" })
    void when_coerce_withInvalidInput_then_returnsError(String invalidInput) {
        val result = SemVerFunctionLibrary.coerce(Val.of(invalidInput));
        assertThatVal(result).isError();
    }

    @ParameterizedTest
    @CsvSource({ "1.0.0, 1.0.0, none", "1.0.0, 2.0.0, major", "1.0.0, 1.1.0, minor", "1.0.0, 1.0.1, patch",
            "1.0.0, 1.0.0-alpha, prerelease", "1.0.0-alpha, 1.0.0, prerelease", "1.0.0-alpha, 1.0.0-beta, prerelease",
            "2.3.5, 2.3.5+build, none" })
    void when_diff_withVersions_then_returnsChangeType(String version1, String version2, String expected) {
        val result = SemVerFunctionLibrary.diff(Val.of(version1), Val.of(version2));

        assertThatVal(result).hasValue();
        assertThat(result.get().asText()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = { "1.0", "invalid" })
    void when_diff_withInvalidVersion_then_returnsError(String invalidVersion) {
        val result = SemVerFunctionLibrary.diff(Val.of(invalidVersion), Val.of("1.0.0"));
        assertThatVal(result).isError();
        assertThat(result.getMessage()).contains(SemVerFunctionLibrary.INVALID_VERSION);
    }

    @Test
    void when_buildMetadataPresent_then_ignoredInComparison() {
        val version1 = "1.0.0+build.1";
        val version2 = "1.0.0+build.2";
        val version3 = "1.0.0";

        assertThatVal(SemVerFunctionLibrary.equals(Val.of(version1), Val.of(version2))).isTrue();
        assertThatVal(SemVerFunctionLibrary.equals(Val.of(version1), Val.of(version3))).isTrue();

        val compareResult = SemVerFunctionLibrary.compare(Val.of(version1), Val.of(version2));
        assertThatVal(compareResult).hasValue();
        assertThat(compareResult.get().asInt()).isZero();
    }

    @Test
    void when_preReleaseVersions_then_comparedCorrectly() {
        assertThatVal(SemVerFunctionLibrary.isLower(Val.of("1.0.0-alpha"), Val.of("1.0.0-beta"))).isTrue();
        assertThatVal(SemVerFunctionLibrary.isLower(Val.of("1.0.0-beta"), Val.of("1.0.0-rc.1"))).isTrue();
        assertThatVal(SemVerFunctionLibrary.isLower(Val.of("1.0.0-rc.1"), Val.of("1.0.0"))).isTrue();
    }

    @Test
    void when_vPrefixPresent_then_handledConsistently() {
        assertThatVal(SemVerFunctionLibrary.equals(Val.of("v1.2.3"), Val.of("1.2.3"))).isTrue();

        val compareResult = SemVerFunctionLibrary.compare(Val.of("v1.2.3"), Val.of("1.2.3"));
        assertThatVal(compareResult).hasValue();
        assertThat(compareResult.get().asInt()).isZero();
    }

    @Test
    void when_rangeWorkflow_then_worksEndToEnd() {
        val versions = createStringArray("1.5.0", "2.1.0", "2.5.0", "3.0.0");
        val range    = ">=2.0.0 <3.0.0";

        val maxVersion = SemVerFunctionLibrary.maxSatisfying(Val.of(versions), Val.of(range));
        val minVersion = SemVerFunctionLibrary.minSatisfying(Val.of(versions), Val.of(range));

        assertThatVal(maxVersion).hasValue();
        assertThatVal(minVersion).hasValue();
        assertThat(maxVersion.get().asText()).isEqualTo("2.5.0");
        assertThat(minVersion.get().asText()).isEqualTo("2.1.0");

        assertThatVal(SemVerFunctionLibrary.satisfies(Val.of("2.3.0"), Val.of(range))).isTrue();
        assertThatVal(SemVerFunctionLibrary.satisfies(Val.of("3.0.0"), Val.of(range))).isFalse();
    }

    @Test
    void when_coerceAndValidateWorkflow_then_worksCorrectly() {
        val input   = "v1.2";
        val coerced = SemVerFunctionLibrary.coerce(Val.of(input));

        assertThatVal(coerced).hasValue();
        assertThat(coerced.get().asText()).isEqualTo("1.2.0");

        val isValid = SemVerFunctionLibrary.isValid(coerced);
        assertThatVal(isValid).isTrue();

        val satisfies = SemVerFunctionLibrary.satisfies(coerced, Val.of(">=1.0.0"));
        assertThatVal(satisfies).isTrue();
    }

    @Test
    void when_diffForDeploymentDecision_then_providesCorrectChangeType() {
        val current = "1.5.0";

        assertThat(SemVerFunctionLibrary.diff(Val.of(current), Val.of("1.5.1")).get().asText()).isEqualTo("patch");
        assertThat(SemVerFunctionLibrary.diff(Val.of(current), Val.of("1.6.0")).get().asText()).isEqualTo("minor");
        assertThat(SemVerFunctionLibrary.diff(Val.of(current), Val.of("2.0.0")).get().asText()).isEqualTo("major");
    }

    @Test
    void when_rangeExpression_withNoMatches_then_returnsNull() {
        val versions = createStringArray("1.0.0", "1.5.0", "1.9.9");
        val result   = SemVerFunctionLibrary.maxSatisfying(Val.of(versions), Val.of(">=2.0.0"));

        assertThatVal(result).hasValue();
        assertThat(result.get().isNull()).isTrue();
    }

    @Test
    void when_satisfies_withComplexRange_then_evaluatesCorrectly() {
        assertThatVal(SemVerFunctionLibrary.satisfies(Val.of("2.5.0"), Val.of(">=2.0.0 <3.0.0"))).isTrue();
        assertThatVal(SemVerFunctionLibrary.satisfies(Val.of("3.0.0"), Val.of(">=2.0.0 <3.0.0"))).isFalse();
        assertThatVal(SemVerFunctionLibrary.satisfies(Val.of("1.9.9"), Val.of(">=2.0.0 <3.0.0"))).isFalse();
    }

    @Test
    void when_arrayContainsNonStrings_then_filtersCorrectly() {
        val array = JSON.arrayNode();
        array.add("1.0.0");
        array.add(42);
        array.add("2.0.0");
        array.add(JSON.objectNode());
        array.add("1.5.0");
        array.add(JSON.arrayNode());

        val result = SemVerFunctionLibrary.maxSatisfying(Val.of(array), Val.of("*"));

        assertThatVal(result).hasValue();
        assertThat(result.get().asText()).isEqualTo("2.0.0");
    }

    @Test
    void when_versionWithOnlyBuildMetadata_then_handledCorrectly() {
        val version1 = "1.0.0+build123";
        val version2 = "1.0.0+build456";

        assertThatVal(SemVerFunctionLibrary.equals(Val.of(version1), Val.of(version2))).isTrue();
        assertThatVal(SemVerFunctionLibrary.diff(Val.of(version1), Val.of(version2))).hasValue();
        assertThat(SemVerFunctionLibrary.diff(Val.of(version1), Val.of(version2)).get().asText()).isEqualTo("none");
    }

    @Test
    void when_zeroMajorVersionCompatibility_then_strictMinorMatch() {
        assertThatVal(SemVerFunctionLibrary.isCompatibleWith(Val.of("0.1.0"), Val.of("0.1.5"))).isTrue();
        assertThatVal(SemVerFunctionLibrary.isCompatibleWith(Val.of("0.1.9"), Val.of("0.1.0"))).isTrue();
        assertThatVal(SemVerFunctionLibrary.isCompatibleWith(Val.of("0.2.0"), Val.of("0.1.0"))).isFalse();
        assertThatVal(SemVerFunctionLibrary.isCompatibleWith(Val.of("0.1.0"), Val.of("0.2.0"))).isFalse();
    }

    @Test
    void when_multiplePrereleaseIdentifiers_then_comparedCorrectly() {
        assertThatVal(SemVerFunctionLibrary.isLower(Val.of("1.0.0-alpha"), Val.of("1.0.0-alpha.1"))).isTrue();
        assertThatVal(SemVerFunctionLibrary.isLower(Val.of("1.0.0-alpha.1"), Val.of("1.0.0-alpha.2"))).isTrue();
        assertThatVal(SemVerFunctionLibrary.isLower(Val.of("1.0.0-1"), Val.of("1.0.0-2"))).isTrue();
    }

    private static JsonNode createStringArray(String... values) {
        val array = JSON.arrayNode();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }
}
