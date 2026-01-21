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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class PDPConfigurationSourceTests {

    @ParameterizedTest
    @NullAndEmptySource
    void whenValidatePdpIdWithNullOrEmpty_thenThrowsException(String pdpId) {
        assertThatThrownBy(() -> PDPConfigurationSource.validatePdpId(pdpId))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("must not be null or empty");
    }

    @Test
    void whenValidatePdpIdExceedsMaxLength_thenThrowsException() {
        var longId = "a".repeat(PDPConfigurationSource.MAX_PDP_ID_LENGTH + 1);

        assertThatThrownBy(() -> PDPConfigurationSource.validatePdpId(longId))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("exceeds maximum length");
    }

    @Test
    void whenValidatePdpIdAtMaxLength_thenSucceeds() {
        var maxLengthId = "a".repeat(PDPConfigurationSource.MAX_PDP_ID_LENGTH);

        PDPConfigurationSource.validatePdpId(maxLengthId);
        // No exception thrown
    }

    @ParameterizedTest
    @ValueSource(strings = { "invalid id", // space
            "invalid/id", // slash
            "invalid\\id", // backslash
            "invalid:id", // colon
            "invalid*id", // asterisk
            "invalid?id", // question mark
            "invalid<id", // less than
            "invalid>id", // greater than
            "invalid|id", // pipe
            "invalid\"id", // double quote
            "invalid'id", // single quote
            "invalid@id", // at sign
            "invalid#id", // hash
            "invalid$id", // dollar
            "invalid%id", // percent
            "invalid^id", // caret
            "invalid&id", // ampersand
            "invalid(id", // parenthesis
            "invalid)id", // parenthesis
            "invalid+id", // plus
            "invalid=id", // equals
            "invalid[id", // bracket
            "invalid]id", // bracket
            "invalid{id", // brace
            "invalid}id", // brace
            "invalid,id", // comma
            "invalid;id", // semicolon
            "invalid`id", // backtick
            "invalid~id", // tilde
            "invalid!id" // exclamation
    })
    void whenValidatePdpIdWithInvalidCharacters_thenThrowsException(String pdpId) {
        assertThatThrownBy(() -> PDPConfigurationSource.validatePdpId(pdpId))
                .isInstanceOf(PDPConfigurationException.class).hasMessageContaining("invalid characters");
    }

    @ParameterizedTest
    @ValueSource(strings = { "valid-id", "valid_id", "valid.id", "ValidId", "VALID_ID", "valid123", "123valid", "a",
            "A", "0", "my-pdp-security", "tenant_a.production", "v1.0.0-beta", "PDP-2024-01" })
    void whenValidatePdpIdWithValidCharacters_thenSucceeds(String pdpId) {
        PDPConfigurationSource.validatePdpId(pdpId);
        // No exception thrown
    }

    @ParameterizedTest
    @NullAndEmptySource
    void whenIsValidPdpIdWithNullOrEmpty_thenReturnsFalse(String pdpId) {
        assertThat(PDPConfigurationSource.isValidPdpId(pdpId)).isFalse();
    }

    @Test
    void whenIsValidPdpIdExceedsMaxLength_thenReturnsFalse() {
        var longId = "a".repeat(PDPConfigurationSource.MAX_PDP_ID_LENGTH + 1);

        assertThat(PDPConfigurationSource.isValidPdpId(longId)).isFalse();
    }

    @Test
    void whenIsValidPdpIdAtMaxLength_thenReturnsTrue() {
        var maxLengthId = "a".repeat(PDPConfigurationSource.MAX_PDP_ID_LENGTH);

        assertThat(PDPConfigurationSource.isValidPdpId(maxLengthId)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("invalidPdpIdCases")
    void whenIsValidPdpIdWithInvalidCharacters_thenReturnsFalse(String pdpId, String description) {
        assertThat(PDPConfigurationSource.isValidPdpId(pdpId)).as("PDP ID with %s should be invalid", description)
                .isFalse();
    }

    static Stream<Arguments> invalidPdpIdCases() {
        return Stream.of(arguments("invalid id", "space"), arguments("invalid/id", "slash"),
                arguments("invalid\\id", "backslash"), arguments("invalid:id", "colon"),
                arguments("../traversal", "path traversal"));
    }

    @ParameterizedTest
    @MethodSource("validPdpIdCases")
    void whenIsValidPdpIdWithValidCharacters_thenReturnsTrue(String pdpId, String description) {
        assertThat(PDPConfigurationSource.isValidPdpId(pdpId)).as("PDP ID with %s should be valid", description)
                .isTrue();
    }

    static Stream<Arguments> validPdpIdCases() {
        return Stream.of(arguments("default", "default value"), arguments("my-pdp", "hyphen"),
                arguments("my_pdp", "underscore"), arguments("my.pdp", "dot"),
                arguments("MyPdp123", "mixed alphanumeric"));
    }

    @Test
    void whenDefaultPdpIdConstant_thenIsValid() {
        assertThat(PDPConfigurationSource.isValidPdpId(PDPConfigurationSource.DEFAULT_PDP_ID)).isTrue();
    }

    @Test
    void whenResolveHomeFolderWithTildePath_thenResolvesToUserHome() {
        var userHome = System.getProperty("user.home");
        var result   = PDPConfigurationSource.resolveHomeFolderIfPresent("~/sapl");

        assertThat(result).isEqualTo(Paths.get(userHome, "sapl"));
    }

    @Test
    void whenResolveHomeFolderWithTildeNestedPath_thenResolvesToUserHome() {
        var userHome = System.getProperty("user.home");
        var result   = PDPConfigurationSource.resolveHomeFolderIfPresent("~/policies/production");

        assertThat(result).isEqualTo(Paths.get(userHome, "policies", "production"));
    }

    @Test
    void whenResolveHomeFolderWithForwardSlashes_thenNormalizesToSystemSeparator() {
        var userHome = System.getProperty("user.home");
        var result   = PDPConfigurationSource.resolveHomeFolderIfPresent("~/path/to/policies");

        assertThat(result).isEqualTo(Paths.get(userHome, "path", "to", "policies"));
    }

    @Test
    void whenResolveHomeFolderWithAbsolutePath_thenReturnsUnchanged() {
        var absolutePath = "/var/data/policies";
        var result       = PDPConfigurationSource.resolveHomeFolderIfPresent(absolutePath);

        assertThat(result).isEqualTo(Paths.get(absolutePath.replace("/", File.separator)));
    }

    @Test
    void whenResolveHomeFolderWithRelativePath_thenReturnsUnchanged() {
        var relativePath = "policies/sapl";
        var result       = PDPConfigurationSource.resolveHomeFolderIfPresent(relativePath);

        assertThat(result).isEqualTo(Paths.get(relativePath.replace("/", File.separator)));
    }

    @Test
    void whenResolveHomeFolderWithTildeNotAtStart_thenReturnsUnchanged() {
        var pathWithTilde = "data/~user/policies";
        var result        = PDPConfigurationSource.resolveHomeFolderIfPresent(pathWithTilde);

        assertThat(result).isEqualTo(Paths.get(pathWithTilde.replace("/", File.separator)));
    }

    @Test
    void whenResolveHomeFolderWithTildeOnly_thenReturnsUnchanged() {
        var result = PDPConfigurationSource.resolveHomeFolderIfPresent("~");

        assertThat(result).isEqualTo(Paths.get("~"));
    }

    @Test
    void whenResolveHomeFolderWithPathObject_thenResolvesCorrectly() {
        var userHome = System.getProperty("user.home");
        var tilePath = Path.of("~", "sapl");
        var result   = PDPConfigurationSource.resolveHomeFolderIfPresent(tilePath);

        assertThat(result).isEqualTo(Paths.get(userHome, "sapl"));
    }

}
