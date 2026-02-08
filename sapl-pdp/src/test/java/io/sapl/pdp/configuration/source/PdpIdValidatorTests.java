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
package io.sapl.pdp.configuration.source;

import io.sapl.pdp.configuration.PDPConfigurationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static io.sapl.pdp.configuration.source.PdpIdValidator.isValidPdpId;
import static io.sapl.pdp.configuration.source.PdpIdValidator.resolveHomeFolderIfPresent;
import static io.sapl.pdp.configuration.source.PdpIdValidator.validatePdpId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("PdpIdValidator")
class PdpIdValidatorTests {

    @ParameterizedTest(name = "[{index}] {0}")
    @NullAndEmptySource
    void whenValidatePdpIdWithNullOrEmptyThenThrowsException(String pdpId) {
        assertThatThrownBy(() -> validatePdpId(pdpId)).isInstanceOf(PDPConfigurationException.class)
                .hasMessageContaining("must not be null or empty");
    }

    @Test
    void whenValidatePdpIdExceedsMaxLengthThenThrowsException() {
        var longId = "a".repeat(PdpIdValidator.MAX_PDP_ID_LENGTH + 1);

        assertThatThrownBy(() -> validatePdpId(longId)).isInstanceOf(PDPConfigurationException.class)
                .hasMessageContaining("exceeds maximum length");
    }

    @Test
    void whenValidatePdpIdAtMaxLengthThenSucceeds() {
        var maxLengthId = "a".repeat(PdpIdValidator.MAX_PDP_ID_LENGTH);

        validatePdpId(maxLengthId);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "invalid id", "invalid/id", "invalid\\id", "invalid:id", "invalid*id", "invalid?id",
            "invalid<id", "invalid>id", "invalid|id", "invalid\"id", "invalid'id", "invalid@id", "invalid#id",
            "invalid$id", "invalid%id", "invalid^id", "invalid&id", "invalid(id", "invalid)id", "invalid+id",
            "invalid=id", "invalid[id", "invalid]id", "invalid{id", "invalid}id", "invalid,id", "invalid;id",
            "invalid`id", "invalid~id", "invalid!id" })
    void whenValidatePdpIdWithInvalidCharactersThenThrowsException(String pdpId) {
        assertThatThrownBy(() -> validatePdpId(pdpId)).isInstanceOf(PDPConfigurationException.class)
                .hasMessageContaining("invalid characters");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "valid-id", "valid_id", "valid.id", "ValidId", "VALID_ID", "valid123", "123valid", "a",
            "A", "0", "my-pdp-security", "tenant_a.production", "v1.0.0-beta", "PDP-2024-01" })
    void whenValidatePdpIdWithValidCharactersThenSucceeds(String pdpId) {
        validatePdpId(pdpId);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @NullAndEmptySource
    void whenIsValidPdpIdWithNullOrEmptyThenReturnsFalse(String pdpId) {
        assertThat(isValidPdpId(pdpId)).isFalse();
    }

    @Test
    void whenIsValidPdpIdExceedsMaxLengthThenReturnsFalse() {
        var longId = "a".repeat(PdpIdValidator.MAX_PDP_ID_LENGTH + 1);

        assertThat(isValidPdpId(longId)).isFalse();
    }

    @Test
    void whenIsValidPdpIdAtMaxLengthThenReturnsTrue() {
        var maxLengthId = "a".repeat(PdpIdValidator.MAX_PDP_ID_LENGTH);

        assertThat(isValidPdpId(maxLengthId)).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPdpIdCases")
    void whenIsValidPdpIdWithInvalidCharactersThenReturnsFalse(String pdpId, String description) {
        assertThat(isValidPdpId(pdpId)).as("PDP ID with %s should be invalid", description).isFalse();
    }

    static Stream<Arguments> invalidPdpIdCases() {
        return Stream.of(arguments("invalid id", "space"), arguments("invalid/id", "slash"),
                arguments("invalid\\id", "backslash"), arguments("invalid:id", "colon"),
                arguments("../traversal", "path traversal"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validPdpIdCases")
    void whenIsValidPdpIdWithValidCharactersThenReturnsTrue(String pdpId, String description) {
        assertThat(isValidPdpId(pdpId)).as("PDP ID with %s should be valid", description).isTrue();
    }

    static Stream<Arguments> validPdpIdCases() {
        return Stream.of(arguments("default", "default value"), arguments("my-pdp", "hyphen"),
                arguments("my_pdp", "underscore"), arguments("my.pdp", "dot"),
                arguments("MyPdp123", "mixed alphanumeric"));
    }

    @Test
    void whenDefaultPdpIdConstantThenIsValid() {
        assertThat(isValidPdpId(PdpIdValidator.DEFAULT_PDP_ID)).isTrue();
    }

    @Test
    void whenResolveHomeFolderWithTildePathThenResolvesToUserHome() {
        var userHome = System.getProperty("user.home");
        var result   = resolveHomeFolderIfPresent("~/sapl");

        assertThat(result).isEqualTo(Paths.get(userHome, "sapl"));
    }

    @Test
    void whenResolveHomeFolderWithTildeNestedPathThenResolvesToUserHome() {
        var userHome = System.getProperty("user.home");
        var result   = resolveHomeFolderIfPresent("~/policies/production");

        assertThat(result).isEqualTo(Paths.get(userHome, "policies", "production"));
    }

    @Test
    void whenResolveHomeFolderWithForwardSlashesThenNormalizesToSystemSeparator() {
        var userHome = System.getProperty("user.home");
        var result   = resolveHomeFolderIfPresent("~/path/to/policies");

        assertThat(result).isEqualTo(Paths.get(userHome, "path", "to", "policies"));
    }

    @Test
    void whenResolveHomeFolderWithAbsolutePathThenReturnsUnchanged() {
        var absolutePath = "/var/data/policies";
        var result       = resolveHomeFolderIfPresent(absolutePath);

        assertThat(result).isEqualTo(Paths.get(absolutePath.replace("/", File.separator)));
    }

    @Test
    void whenResolveHomeFolderWithRelativePathThenReturnsUnchanged() {
        var relativePath = "policies/sapl";
        var result       = resolveHomeFolderIfPresent(relativePath);

        assertThat(result).isEqualTo(Paths.get(relativePath.replace("/", File.separator)));
    }

    @Test
    void whenResolveHomeFolderWithTildeNotAtStartThenReturnsUnchanged() {
        var pathWithTilde = "data/~user/policies";
        var result        = resolveHomeFolderIfPresent(pathWithTilde);

        assertThat(result).isEqualTo(Paths.get(pathWithTilde.replace("/", File.separator)));
    }

    @Test
    void whenResolveHomeFolderWithTildeOnlyThenReturnsUnchanged() {
        var result = resolveHomeFolderIfPresent("~");

        assertThat(result).isEqualTo(Paths.get("~"));
    }

    @Test
    void whenResolveHomeFolderWithPathObjectThenResolvesCorrectly() {
        var userHome = System.getProperty("user.home");
        var tilePath = Path.of("~", "sapl");
        var result   = resolveHomeFolderIfPresent(tilePath);

        assertThat(result).isEqualTo(Paths.get(userHome, "sapl"));
    }

}
