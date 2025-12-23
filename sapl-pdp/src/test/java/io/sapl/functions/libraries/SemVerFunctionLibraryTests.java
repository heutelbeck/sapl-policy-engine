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
package io.sapl.functions.libraries;

import io.sapl.api.model.*;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class SemVerFunctionLibraryTests {

    @Test
    void when_loadedIntoBroker_then_noError() {
        val functionBroker = new DefaultFunctionBroker();
        assertThatCode(() -> functionBroker.loadStaticFunctionLibrary(SemVerFunctionLibrary.class))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = { "1.0.0", "2.3.5", "0.0.1", "10.20.30", "1.0.0-alpha", "1.0.0-beta.1",
            "1.0.0-rc.2+build.456", "v1.0.0", "1.2.3-alpha.beta+build.123" })
    void isValid_whenValidVersion_thenReturnsTrue(String version) {
        val result = SemVerFunctionLibrary.isValid(Value.of(version));

        assertThat(result).isEqualTo(Value.TRUE);
    }

    @ParameterizedTest
    @ValueSource(strings = { "1.0", "1", "a.b.c", "1.0.0.0", "", "1.0.0-", "1.0.0+", "v", "vv1.0.0", "1.2.3.4",
            "V1.0.0" })
    void isValid_whenInvalidVersion_thenReturnsFalse(String version) {
        val result = SemVerFunctionLibrary.isValid(Value.of(version));

        assertThat(result).isEqualTo(Value.FALSE);
    }

    @Test
    void isValid_whenUppercaseVPrefix_thenNotSupported() {
        assertThat(SemVerFunctionLibrary.isValid(Value.of("V1.0.0"))).isEqualTo(Value.FALSE);
        assertThat(SemVerFunctionLibrary.parse(Value.of("V1.0.0"))).isInstanceOf(ErrorValue.class);
    }

    @Test
    void parse_whenSimpleVersion_thenReturnsCorrectComponents() {
        val result = SemVerFunctionLibrary.parse(Value.of("2.3.5"));

        assertThat(result).isInstanceOf(ObjectValue.class);
        val parsed = (ObjectValue) result;
        assertThat(parsed.get("version")).isEqualTo(Value.of("2.3.5"));
        assertThat(parsed.get("major")).isEqualTo(Value.of(2));
        assertThat(parsed.get("minor")).isEqualTo(Value.of(3));
        assertThat(parsed.get("patch")).isEqualTo(Value.of(5));
        assertThat(parsed.get("isStable")).isEqualTo(Value.TRUE);
        assertThat(parsed.get("isPreRelease")).isEqualTo(Value.FALSE);
        assertThat((ArrayValue) parsed.get("preRelease")).isEmpty();
        assertThat((ArrayValue) parsed.get("buildMetadata")).isEmpty();
    }

    @Test
    void parse_whenPreReleaseVersion_thenReturnsPreReleaseArray() {
        val result = SemVerFunctionLibrary.parse(Value.of("1.0.0-alpha.beta.1"));

        assertThat(result).isInstanceOf(ObjectValue.class);
        val parsed = (ObjectValue) result;
        assertThat(parsed.get("isStable")).isEqualTo(Value.FALSE);
        assertThat(parsed.get("isPreRelease")).isEqualTo(Value.TRUE);

        val preReleaseArray = (ArrayValue) parsed.get("preRelease");
        assertThat(preReleaseArray).hasSize(3);
        assertThat(preReleaseArray.get(0)).isEqualTo(Value.of("alpha"));
        assertThat(preReleaseArray.get(1)).isEqualTo(Value.of("beta"));
        assertThat(preReleaseArray.get(2)).isEqualTo(Value.of("1"));
    }

    @Test
    void parse_whenBuildMetadata_thenReturnsBuildMetadataArray() {
        val result = SemVerFunctionLibrary.parse(Value.of("1.0.0+build.123.sha.5114f85"));

        assertThat(result).isInstanceOf(ObjectValue.class);
        val parsed = (ObjectValue) result;

        val buildMetadataArray = (ArrayValue) parsed.get("buildMetadata");
        assertThat(buildMetadataArray).hasSize(4);
        assertThat(buildMetadataArray.get(0)).isEqualTo(Value.of("build"));
        assertThat(buildMetadataArray.get(1)).isEqualTo(Value.of("123"));
        assertThat(buildMetadataArray.get(2)).isEqualTo(Value.of("sha"));
        assertThat(buildMetadataArray.get(3)).isEqualTo(Value.of("5114f85"));
    }

    @Test
    void parse_whenFullVersion_thenReturnsAllComponents() {
        val result = SemVerFunctionLibrary.parse(Value.of("1.2.3-rc.1+build.456"));

        assertThat(result).isInstanceOf(ObjectValue.class);
        val parsed = (ObjectValue) result;
        assertThat(parsed.get("version")).isEqualTo(Value.of("1.2.3-rc.1+build.456"));
        assertThat(parsed.get("major")).isEqualTo(Value.of(1));
        assertThat(parsed.get("minor")).isEqualTo(Value.of(2));
        assertThat(parsed.get("patch")).isEqualTo(Value.of(3));
        assertThat(parsed.get("isStable")).isEqualTo(Value.FALSE);
        assertThat(parsed.get("isPreRelease")).isEqualTo(Value.TRUE);

        val preRelease = (ArrayValue) parsed.get("preRelease");
        assertThat(preRelease).hasSize(2);
        assertThat(preRelease.get(0)).isEqualTo(Value.of("rc"));
        assertThat(preRelease.get(1)).isEqualTo(Value.of("1"));

        val buildMetadata = (ArrayValue) parsed.get("buildMetadata");
        assertThat(buildMetadata).hasSize(2);
        assertThat(buildMetadata.get(0)).isEqualTo(Value.of("build"));
        assertThat(buildMetadata.get(1)).isEqualTo(Value.of("456"));
    }

    @Test
    void parse_whenVPrefix_thenStripsPrefix() {
        val result = SemVerFunctionLibrary.parse(Value.of("v2.5.8"));

        assertThat(result).isInstanceOf(ObjectValue.class);
        val parsed = (ObjectValue) result;
        assertThat(parsed.get("version")).isEqualTo(Value.of("2.5.8"));
        assertThat(parsed.get("major")).isEqualTo(Value.of(2));
    }

    @ParameterizedTest
    @ValueSource(strings = { "1.0", "invalid", "1.0.0.0", "", "abc" })
    void parse_whenInvalidVersion_thenReturnsError(String invalidVersion) {
        val result = SemVerFunctionLibrary.parse(Value.of(invalidVersion));

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Invalid semantic version");
    }

    @ParameterizedTest
    @CsvSource({ "1.0.0, 1.0.0, 0", "1.0.0, 2.0.0, -1", "2.0.0, 1.0.0, 1", "1.0.0, 1.1.0, -1", "1.1.0, 1.0.0, 1",
            "1.0.0, 1.0.1, -1", "1.0.1, 1.0.0, 1", "1.0.0, 1.0.0-alpha, 1", "1.0.0-alpha, 1.0.0, -1",
            "1.0.0-alpha, 1.0.0-beta, -1", "1.0.0-beta, 1.0.0-alpha, 1", "1.0.0+build.1, 1.0.0+build.2, 0",
            "v1.0.0, 1.0.0, 0" })
    void compare_whenValidVersions_thenReturnsCorrectResult(String version1, String version2, int expected) {
        val result = SemVerFunctionLibrary.compare(Value.of(version1), Value.of(version2));

        assertThat(result).isNotInstanceOf(ErrorValue.class);
        assertThat(((NumberValue) result).value().intValue()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = { "1.0", "invalid", "", "a.b.c" })
    void compare_whenInvalidVersion_thenReturnsError(String invalidVersion) {
        val result = SemVerFunctionLibrary.compare(Value.of(invalidVersion), Value.of("1.0.0"));

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Invalid version in comparison");
    }

    @ParameterizedTest
    @CsvSource({ "1.0.0, 1.0.0, true", "1.0.0, 2.0.0, false", "1.0.0+build.1, 1.0.0+build.2, true",
            "1.0.0-alpha, 1.0.0-alpha, true", "1.0.0-alpha, 1.0.0-beta, false", "v1.0.0, 1.0.0, true" })
    void equals_whenValidVersions_thenReturnsCorrectResult(String version1, String version2, boolean expected) {
        val result = SemVerFunctionLibrary.equals(Value.of(version1), Value.of(version2));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest
    @CsvSource({ "1.0.0, 2.0.0, true", "2.0.0, 1.0.0, false", "1.0.0, 1.0.0, false", "1.0.0-alpha, 1.0.0, true",
            "1.0.0-alpha, 1.0.0-beta, true" })
    void isLower_whenValidVersions_thenReturnsCorrectResult(String version1, String version2, boolean expected) {
        val result = SemVerFunctionLibrary.isLower(Value.of(version1), Value.of(version2));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest
    @CsvSource({ "2.0.0, 1.0.0, true", "1.0.0, 2.0.0, false", "1.0.0, 1.0.0, false", "1.0.0, 1.0.0-alpha, true",
            "1.0.0-beta, 1.0.0-alpha, true" })
    void isHigher_whenValidVersions_thenReturnsCorrectResult(String version1, String version2, boolean expected) {
        val result = SemVerFunctionLibrary.isHigher(Value.of(version1), Value.of(version2));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest
    @CsvSource({ "1.0.0, 2.0.0, true", "2.0.0, 1.0.0, false", "1.0.0, 1.0.0, true", "1.0.0-alpha, 1.0.0, true" })
    void isLowerOrEqual_whenValidVersions_thenReturnsCorrectResult(String version1, String version2, boolean expected) {
        val result = SemVerFunctionLibrary.isLowerOrEqual(Value.of(version1), Value.of(version2));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest
    @CsvSource({ "2.0.0, 1.0.0, true", "1.0.0, 2.0.0, false", "1.0.0, 1.0.0, true", "1.0.0, 1.0.0-alpha, true" })
    void isHigherOrEqual_whenValidVersions_thenReturnsCorrectResult(String version1, String version2,
            boolean expected) {
        val result = SemVerFunctionLibrary.isHigherOrEqual(Value.of(version1), Value.of(version2));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest
    @CsvSource({ "1.0.0, 1.5.3, true", "1.0.0, 2.0.0, false", "2.3.5, 2.8.1, true", "1.0.0-alpha, 1.0.0-beta, true" })
    void haveSameMajor_whenValidVersions_thenReturnsCorrectResult(String version1, String version2, boolean expected) {
        val result = SemVerFunctionLibrary.haveSameMajor(Value.of(version1), Value.of(version2));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest
    @CsvSource({ "1.2.0, 1.2.5, true", "1.2.0, 1.3.0, false", "1.2.0, 2.2.0, false", "2.3.1, 2.3.9, true" })
    void haveSameMinor_whenValidVersions_thenReturnsCorrectResult(String version1, String version2, boolean expected) {
        val result = SemVerFunctionLibrary.haveSameMinor(Value.of(version1), Value.of(version2));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest
    @CsvSource({ "1.2.3, 1.2.3, true", "1.2.3, 1.2.4, false", "1.2.3, 1.3.3, false", "1.2.3-alpha, 1.2.3-beta, true" })
    void haveSamePatch_whenValidVersions_thenReturnsCorrectResult(String version1, String version2, boolean expected) {
        val result = SemVerFunctionLibrary.haveSamePatch(Value.of(version1), Value.of(version2));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest
    @CsvSource({ "2.5.0, 2.0.0, true", "2.0.0, 3.0.0, false", "0.2.0, 0.2.5, true", "0.2.0, 0.3.0, false",
            "1.5.0, 1.0.0, true" })
    void isCompatibleWith_whenValidVersions_thenReturnsCorrectResult(String version1, String version2,
            boolean expected) {
        val result = SemVerFunctionLibrary.isCompatibleWith(Value.of(version1), Value.of(version2));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @Test
    void isCompatibleWith_whenMajorZero_thenRequiresSameMinor() {
        assertThat(SemVerFunctionLibrary.isCompatibleWith(Value.of("0.2.5"), Value.of("0.2.0"))).isEqualTo(Value.TRUE);
        assertThat(SemVerFunctionLibrary.isCompatibleWith(Value.of("0.3.0"), Value.of("0.2.0"))).isEqualTo(Value.FALSE);
    }

    @Test
    void isCompatibleWith_whenMajorNonZero_thenRequiresSameMajor() {
        assertThat(SemVerFunctionLibrary.isCompatibleWith(Value.of("1.5.0"), Value.of("1.0.0"))).isEqualTo(Value.TRUE);
        assertThat(SemVerFunctionLibrary.isCompatibleWith(Value.of("2.0.0"), Value.of("1.9.9"))).isEqualTo(Value.FALSE);
    }

    @ParameterizedTest
    @CsvSource({ "2.0.0, 1.0.0, true", "1.0.0, 2.0.0, false", "2.0.0, 2.0.0, true" })
    void isAtLeast_whenValidVersions_thenReturnsCorrectResult(String version, String minimum, boolean expected) {
        val result = SemVerFunctionLibrary.isAtLeast(Value.of(version), Value.of(minimum));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest
    @CsvSource({ "1.0.0, 2.0.0, true", "2.0.0, 1.0.0, false", "2.0.0, 2.0.0, true" })
    void isAtMost_whenValidVersions_thenReturnsCorrectResult(String version, String maximum, boolean expected) {
        val result = SemVerFunctionLibrary.isAtMost(Value.of(version), Value.of(maximum));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest
    @CsvSource({ "2.0.0, 1.0.0, 3.0.0, true", "1.0.0, 1.0.0, 3.0.0, true", "3.0.0, 1.0.0, 3.0.0, true",
            "0.5.0, 1.0.0, 3.0.0, false", "4.0.0, 1.0.0, 3.0.0, false" })
    void isBetween_whenValidVersions_thenReturnsCorrectResult(String version, String minimum, String maximum,
            boolean expected) {
        val result = SemVerFunctionLibrary.isBetween(Value.of(version), Value.of(minimum), Value.of(maximum));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = { "1.0", "invalid" })
    void isBetween_whenInvalidVersion_thenReturnsError(String invalidVersion) {
        val result = SemVerFunctionLibrary.isBetween(Value.of(invalidVersion), Value.of("1.0.0"), Value.of("2.0.0"));

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Invalid version in comparison");
    }

    @ParameterizedTest
    @CsvSource({ "1.0.0-alpha, true", "1.0.0-beta.1, true", "1.0.0, false", "1.0.0+build.123, false" })
    void isPreRelease_whenValidVersion_thenReturnsCorrectResult(String version, boolean expected) {
        val result = SemVerFunctionLibrary.isPreRelease(Value.of(version));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest
    @CsvSource({ "1.0.0, true", "1.0.0+build.123, true", "1.0.0-alpha, false", "1.0.0-beta.1, false" })
    void isStable_whenValidVersion_thenReturnsCorrectResult(String version, boolean expected) {
        val result = SemVerFunctionLibrary.isStable(Value.of(version));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = { "1.0", "invalid" })
    void isPreRelease_whenInvalidVersion_thenReturnsError(String invalidVersion) {
        val result = SemVerFunctionLibrary.isPreRelease(Value.of(invalidVersion));

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Invalid version");
    }

    @ParameterizedTest
    @CsvSource({ "1.0.0, 1", "2.3.5, 2", "10.20.30, 10", "0.1.0, 0" })
    void getMajor_whenValidVersion_thenReturnsCorrectValue(String version, int expected) {
        val result = SemVerFunctionLibrary.getMajor(Value.of(version));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest
    @CsvSource({ "1.0.0, 0", "2.3.5, 3", "10.20.30, 20", "0.1.0, 1" })
    void getMinor_whenValidVersion_thenReturnsCorrectValue(String version, int expected) {
        val result = SemVerFunctionLibrary.getMinor(Value.of(version));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest
    @CsvSource({ "1.0.0, 0", "2.3.5, 5", "10.20.30, 30", "0.1.2, 2" })
    void getPatch_whenValidVersion_thenReturnsCorrectValue(String version, int expected) {
        val result = SemVerFunctionLibrary.getPatch(Value.of(version));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = { "1.0", "invalid" })
    void getMajor_whenInvalidVersion_thenReturnsError(String invalidVersion) {
        val result = SemVerFunctionLibrary.getMajor(Value.of(invalidVersion));

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Invalid version");
    }

    @ParameterizedTest
    @MethodSource("satisfiesTestCases")
    void satisfies_whenValidInputs_thenReturnsCorrectResult(String version, String range, boolean expected) {
        val result = SemVerFunctionLibrary.satisfies(Value.of(version), Value.of(range));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    private static Stream<Arguments> satisfiesTestCases() {
        return Stream.of(arguments("2.5.0", ">=2.0.0", true), arguments("1.5.0", ">=2.0.0", false),
                arguments("2.5.0", ">=2.0.0 <3.0.0", true), arguments("3.0.0", ">=2.0.0 <3.0.0", false),
                arguments("1.2.5", "1.2.x", true), arguments("1.3.0", "1.2.x", false),
                arguments("1.2.5", "~1.2.3", true), arguments("1.3.0", "~1.2.3", false),
                arguments("1.5.0", "^1.2.3", true), arguments("2.0.0", "^1.2.3", false));
    }

    @Test
    void satisfies_whenHyphenRange_thenWorksCorrectly() {
        assertThat(SemVerFunctionLibrary.satisfies(Value.of("1.2.3"), Value.of("1.2.3 - 2.3.4"))).isEqualTo(Value.TRUE);
        assertThat(SemVerFunctionLibrary.satisfies(Value.of("2.0.0"), Value.of("1.2.3 - 2.3.4"))).isEqualTo(Value.TRUE);
        assertThat(SemVerFunctionLibrary.satisfies(Value.of("3.0.0"), Value.of("1.2.3 - 2.3.4")))
                .isEqualTo(Value.FALSE);
    }

    @Test
    void satisfies_whenLogicalOr_thenWorksCorrectly() {
        assertThat(SemVerFunctionLibrary.satisfies(Value.of("1.0.0"), Value.of(">=1.0.0 || >=2.0.0")))
                .isEqualTo(Value.TRUE);
        assertThat(SemVerFunctionLibrary.satisfies(Value.of("2.5.0"), Value.of(">=1.0.0 || >=2.0.0")))
                .isEqualTo(Value.TRUE);
        assertThat(SemVerFunctionLibrary.satisfies(Value.of("0.5.0"), Value.of(">=1.0.0 || >=2.0.0")))
                .isEqualTo(Value.FALSE);
    }

    @ParameterizedTest
    @ValueSource(strings = { "1.0", "invalid" })
    void satisfies_whenInvalidVersion_thenReturnsError(String invalidVersion) {
        val result = SemVerFunctionLibrary.satisfies(Value.of(invalidVersion), Value.of(">=1.0.0"));

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Invalid version or range");
    }

    @Test
    void satisfies_whenInvalidRange_thenReturnsFalse() {
        val result = SemVerFunctionLibrary.satisfies(Value.of("1.0.0"), Value.of(">>invalid<<"));

        assertThat(result).isEqualTo(Value.FALSE);
    }

    @Test
    void maxSatisfying_whenMatchingVersions_thenReturnsHighest() {
        val versions = createVersionArray("1.5.0", "2.1.0", "2.5.0", "3.0.0");
        val result   = SemVerFunctionLibrary.maxSatisfying(versions, Value.of(">=2.0.0 <3.0.0"));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of("2.5.0"));
    }

    @Test
    void maxSatisfying_whenNoMatches_thenReturnsNull() {
        val versions = createVersionArray("1.5.0", "1.6.0", "1.7.0");
        val result   = SemVerFunctionLibrary.maxSatisfying(versions, Value.of(">=2.0.0"));

        assertThat(result).isEqualTo(Value.NULL);
    }

    @Test
    void maxSatisfying_whenEmptyArray_thenReturnsNull() {
        val result = SemVerFunctionLibrary.maxSatisfying(Value.EMPTY_ARRAY, Value.of(">=1.0.0"));

        assertThat(result).isEqualTo(Value.NULL);
    }

    @Test
    void maxSatisfying_whenMixedTypes_thenFiltersOnlyStrings() {
        val array = ArrayValue.builder().add(Value.of("1.0.0")).add(Value.of(123)).add(Value.of("2.0.0"))
                .add(Value.TRUE).add(Value.of("1.5.0")).build();

        val result = SemVerFunctionLibrary.maxSatisfying(array, Value.of(">=1.0.0"));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of("2.0.0"));
    }

    @Test
    void maxSatisfying_whenInvalidRange_thenReturnsNull() {
        val versions = createVersionArray("1.0.0", "2.0.0");
        val result   = SemVerFunctionLibrary.maxSatisfying(versions, Value.of(">>invalid<<"));

        assertThat(result).isEqualTo(Value.NULL);
    }

    @Test
    void minSatisfying_whenMatchingVersions_thenReturnsLowest() {
        val versions = createVersionArray("1.5.0", "2.1.0", "2.5.0", "3.0.0");
        val result   = SemVerFunctionLibrary.minSatisfying(versions, Value.of(">=2.0.0 <3.0.0"));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of("2.1.0"));
    }

    @Test
    void minSatisfying_whenNoMatches_thenReturnsNull() {
        val versions = createVersionArray("1.5.0", "1.6.0", "1.7.0");
        val result   = SemVerFunctionLibrary.minSatisfying(versions, Value.of(">=2.0.0"));

        assertThat(result).isEqualTo(Value.NULL);
    }

    @Test
    void minSatisfying_whenEmptyArray_thenReturnsNull() {
        val result = SemVerFunctionLibrary.minSatisfying(Value.EMPTY_ARRAY, Value.of(">=1.0.0"));

        assertThat(result).isEqualTo(Value.NULL);
    }

    @ParameterizedTest
    @CsvSource({ "1.2.3, 1.2.3", "1.2, 1.2.0", "1, 1.0.0", "v1.2.3, 1.2.3", "v1.2, 1.2.0" })
    void coerce_whenPartialVersions_thenNormalizes(String input, String expected) {
        val result = SemVerFunctionLibrary.coerce(Value.of(input));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @Test
    void coerce_whenWhitespace_thenNormalizes() {
        val result = SemVerFunctionLibrary.coerce(Value.of("   1.2.3   "));

        assertThat(result).isNotInstanceOf(ErrorValue.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "abc", "not-a-version" })
    void coerce_whenInvalidInput_thenReturnsError(String invalidInput) {
        val result = SemVerFunctionLibrary.coerce(Value.of(invalidInput));

        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    @ParameterizedTest
    @CsvSource({ "1.0.0, 1.0.0, none", "1.0.0, 2.0.0, major", "1.0.0, 1.1.0, minor", "1.0.0, 1.0.1, patch",
            "1.0.0, 1.0.0-alpha, prerelease", "1.0.0-alpha, 1.0.0, prerelease", "1.0.0-alpha, 1.0.0-beta, prerelease",
            "2.3.5, 2.3.5+build, none" })
    void diff_whenValidVersions_thenReturnsChangeType(String version1, String version2, String expected) {
        val result = SemVerFunctionLibrary.diff(Value.of(version1), Value.of(version2));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = { "1.0", "invalid" })
    void diff_whenInvalidVersion_thenReturnsError(String invalidVersion) {
        val result = SemVerFunctionLibrary.diff(Value.of(invalidVersion), Value.of("1.0.0"));

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Invalid version");
    }

    @Test
    void buildMetadataPresent_thenIgnoredInComparison() {
        val version1 = Value.of("1.0.0+build.1");
        val version2 = Value.of("1.0.0+build.2");
        val version3 = Value.of("1.0.0");

        assertThat(SemVerFunctionLibrary.equals(version1, version2)).isEqualTo(Value.TRUE);
        assertThat(SemVerFunctionLibrary.equals(version1, version3)).isEqualTo(Value.TRUE);

        val compareResult = SemVerFunctionLibrary.compare(version1, version2);
        assertThat(compareResult).isNotInstanceOf(ErrorValue.class);
        assertThat(((NumberValue) compareResult).value().intValue()).isZero();
    }

    @Test
    void preReleaseVersions_thenComparedCorrectly() {
        assertThat(SemVerFunctionLibrary.isLower(Value.of("1.0.0-alpha"), Value.of("1.0.0-beta")))
                .isEqualTo(Value.TRUE);
        assertThat(SemVerFunctionLibrary.isLower(Value.of("1.0.0-beta"), Value.of("1.0.0-rc.1"))).isEqualTo(Value.TRUE);
        assertThat(SemVerFunctionLibrary.isLower(Value.of("1.0.0-rc.1"), Value.of("1.0.0"))).isEqualTo(Value.TRUE);
    }

    @Test
    void vPrefixPresent_thenHandledConsistently() {
        assertThat(SemVerFunctionLibrary.equals(Value.of("v1.2.3"), Value.of("1.2.3"))).isEqualTo(Value.TRUE);

        val compareResult = SemVerFunctionLibrary.compare(Value.of("v1.2.3"), Value.of("1.2.3"));
        assertThat(compareResult).isNotInstanceOf(ErrorValue.class);
        assertThat(((NumberValue) compareResult).value().intValue()).isZero();
    }

    @Test
    void rangeWorkflow_thenWorksEndToEnd() {
        val versions = createVersionArray("1.5.0", "2.1.0", "2.5.0", "3.0.0");
        val range    = Value.of(">=2.0.0 <3.0.0");

        val maxVersion = SemVerFunctionLibrary.maxSatisfying(versions, range);
        val minVersion = SemVerFunctionLibrary.minSatisfying(versions, range);

        assertThat(maxVersion).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of("2.5.0"));
        assertThat(minVersion).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of("2.1.0"));

        assertThat(SemVerFunctionLibrary.satisfies(Value.of("2.3.0"), range)).isEqualTo(Value.TRUE);
        assertThat(SemVerFunctionLibrary.satisfies(Value.of("3.0.0"), range)).isEqualTo(Value.FALSE);
    }

    @Test
    void coerceAndValidateWorkflow_thenWorksCorrectly() {
        val input   = Value.of("v1.2");
        val coerced = SemVerFunctionLibrary.coerce(input);

        assertThat(coerced).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of("1.2.0"));

        val isValid = SemVerFunctionLibrary.isValid((TextValue) coerced);
        assertThat(isValid).isEqualTo(Value.TRUE);

        val satisfies = SemVerFunctionLibrary.satisfies((TextValue) coerced, Value.of(">=1.0.0"));
        assertThat(satisfies).isEqualTo(Value.TRUE);
    }

    @Test
    void diffForDeploymentDecision_thenProvidesCorrectChangeType() {
        val current = Value.of("1.5.0");

        assertThat(SemVerFunctionLibrary.diff(current, Value.of("1.5.1"))).isEqualTo(Value.of("patch"));
        assertThat(SemVerFunctionLibrary.diff(current, Value.of("1.6.0"))).isEqualTo(Value.of("minor"));
        assertThat(SemVerFunctionLibrary.diff(current, Value.of("2.0.0"))).isEqualTo(Value.of("major"));
    }

    @Test
    void rangeExpression_whenNoMatches_thenReturnsNull() {
        val versions = createVersionArray("1.0.0", "1.5.0", "1.9.9");
        val result   = SemVerFunctionLibrary.maxSatisfying(versions, Value.of(">=2.0.0"));

        assertThat(result).isEqualTo(Value.NULL);
    }

    @Test
    void satisfies_whenComplexRange_thenEvaluatesCorrectly() {
        assertThat(SemVerFunctionLibrary.satisfies(Value.of("2.5.0"), Value.of(">=2.0.0 <3.0.0")))
                .isEqualTo(Value.TRUE);
        assertThat(SemVerFunctionLibrary.satisfies(Value.of("3.0.0"), Value.of(">=2.0.0 <3.0.0")))
                .isEqualTo(Value.FALSE);
        assertThat(SemVerFunctionLibrary.satisfies(Value.of("1.9.9"), Value.of(">=2.0.0 <3.0.0")))
                .isEqualTo(Value.FALSE);
    }

    @Test
    void arrayContainsNonStrings_thenFiltersCorrectly() {
        val array = ArrayValue.builder().add(Value.of("1.0.0")).add(Value.of(42)).add(Value.of("2.0.0"))
                .add(ObjectValue.builder().build()).add(Value.of("1.5.0")).add(Value.EMPTY_ARRAY).build();

        val result = SemVerFunctionLibrary.maxSatisfying(array, Value.of("*"));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isEqualTo(Value.of("2.0.0"));
    }

    @Test
    void versionWithOnlyBuildMetadata_thenHandledCorrectly() {
        val version1 = Value.of("1.0.0+build123");
        val version2 = Value.of("1.0.0+build456");

        assertThat(SemVerFunctionLibrary.equals(version1, version2)).isEqualTo(Value.TRUE);
        assertThat(SemVerFunctionLibrary.diff(version1, version2)).isNotInstanceOf(ErrorValue.class)
                .isEqualTo(Value.of("none"));
    }

    @Test
    void zeroMajorVersionCompatibility_thenStrictMinorMatch() {
        assertThat(SemVerFunctionLibrary.isCompatibleWith(Value.of("0.1.0"), Value.of("0.1.5"))).isEqualTo(Value.TRUE);
        assertThat(SemVerFunctionLibrary.isCompatibleWith(Value.of("0.1.9"), Value.of("0.1.0"))).isEqualTo(Value.TRUE);
        assertThat(SemVerFunctionLibrary.isCompatibleWith(Value.of("0.2.0"), Value.of("0.1.0"))).isEqualTo(Value.FALSE);
        assertThat(SemVerFunctionLibrary.isCompatibleWith(Value.of("0.1.0"), Value.of("0.2.0"))).isEqualTo(Value.FALSE);
    }

    @Test
    void multiplePrereleaseIdentifiers_thenComparedCorrectly() {
        assertThat(SemVerFunctionLibrary.isLower(Value.of("1.0.0-alpha"), Value.of("1.0.0-alpha.1")))
                .isEqualTo(Value.TRUE);
        assertThat(SemVerFunctionLibrary.isLower(Value.of("1.0.0-alpha.1"), Value.of("1.0.0-alpha.2")))
                .isEqualTo(Value.TRUE);
        assertThat(SemVerFunctionLibrary.isLower(Value.of("1.0.0-1"), Value.of("1.0.0-2"))).isEqualTo(Value.TRUE);
    }

    private static ArrayValue createVersionArray(String... versions) {
        return Value.ofArray(Stream.of(versions).map(Value::of).toArray(Value[]::new));
    }
}
