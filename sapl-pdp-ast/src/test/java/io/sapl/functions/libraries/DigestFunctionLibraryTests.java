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

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class DigestFunctionLibraryTests {

    @Test
    void when_loadedIntoBroker_then_noError() {
        val functionBroker = new DefaultFunctionBroker();
        assertThatCode(() -> functionBroker.loadStaticFunctionLibrary(DigestFunctionLibrary.class))
                .doesNotThrowAnyException();
    }

    /**
     * Test data for hash correctness verification. Each row: algorithm, input,
     * expected hash
     */
    private static Stream<Arguments> hashCorrectnessTestCases() {
        return Stream.of(
                // SHA-256
                arguments("sha256", "hello", "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"),
                arguments("sha256", "", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),

                // SHA-384
                arguments("sha384", "hello",
                        "59e1748777448c69de6b800d7a33bbfb9ff1b463e44354c3553bcdb9c666fa90125a3c79f90397bdf5f6a13de828684f"),
                arguments("sha384", "",
                        "38b060a751ac96384cd9327eb1b1e36a21fdb71114be07434c0cc7bf63f6e1da274edebfe76f65fbd51ad2f14898b95b"),

                // SHA-512
                arguments("sha512", "hello",
                        "9b71d224bd62f3785d96d46ad3ea3d73319bfbc2890caadae2dff72519673ca72323c3d99ba5c11d7c7acc6e14b8c5da0c4663475c2e5c3adef46f73bcdec043"),
                arguments("sha512", "",
                        "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e"),

                // SHA3-256
                arguments("sha3_256", "hello", "3338be694f50c5f338814986cdf0686453a888b84f424d792af4b9202398f392"),
                arguments("sha3_256", "", "a7ffc6f8bf1ed76651c14756a061d662f580ff4de43b49fa82d80a4b80f8434a"),

                // SHA3-384
                arguments("sha3_384", "hello",
                        "720aea11019ef06440fbf05d87aa24680a2153df3907b23631e7177ce620fa1330ff07c0fddee54699a4c3ee0ee9d887"),

                // SHA3-512
                arguments("sha3_512", "hello",
                        "75d527c368f2efe848ecf6b073a36767800805e9eef2b1857d5f984f036eb6df891d75f72d9b154518c1cd58835286d1da9a38deba3de98b5a53e5ed78a84976"),

                // MD5
                arguments("md5", "hello", "5d41402abc4b2a76b9719d911017c592"),
                arguments("md5", "", "d41d8cd98f00b204e9800998ecf8427e"),

                // SHA-1
                arguments("sha1", "hello", "aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d"),
                arguments("sha1", "", "da39a3ee5e6b4b0d3255bfef95601890afd80709"));
    }

    @ParameterizedTest(name = "{0}(\"{1}\") should produce correct hash")
    @MethodSource("hashCorrectnessTestCases")
    void digestFunction_whenGivenInput_computesCorrectHash(String algorithm, String input, String expectedHash) {
        var digestFunction = getDigestFunction(algorithm);
        var result         = digestFunction.apply(Value.of(input));

        assertThat(result).isEqualTo(Value.of(expectedHash));
    }

    /**
     * Test data for hash format verification. Each row: algorithm, expected hex
     * length
     */
    @ParameterizedTest(name = "{0} should return lowercase hex of length {1}")
    @CsvSource({ "sha256, 64", "sha384, 96", "sha512, 128", "sha3_256, 64", "sha3_384, 96", "sha3_512, 128", "md5, 32",
            "sha1, 40" })
    void digestFunction_returnsLowercaseHexOfCorrectLength(String algorithm, int expectedLength) {
        var digestFunction = getDigestFunction(algorithm);
        var result         = digestFunction.apply(Value.of("test"));

        assertThat(result).isNotInstanceOf(ErrorValue.class).isInstanceOf(TextValue.class)
                .extracting(v -> ((TextValue) v).value())
                .satisfies(hash -> assertThat(hash).isEqualTo(hash.toLowerCase()).hasSize(expectedLength));
    }

    @Test
    void allDigests_whenSameInput_produceConsistentHashes() {
        var input = Value.of("consistency test");

        assertThat(DigestFunctionLibrary.sha256(input)).isEqualTo(DigestFunctionLibrary.sha256(input));
        assertThat(DigestFunctionLibrary.sha384(input)).isEqualTo(DigestFunctionLibrary.sha384(input));
        assertThat(DigestFunctionLibrary.sha512(input)).isEqualTo(DigestFunctionLibrary.sha512(input));
        assertThat(DigestFunctionLibrary.sha3256(input)).isEqualTo(DigestFunctionLibrary.sha3256(input));
        assertThat(DigestFunctionLibrary.md5(input)).isEqualTo(DigestFunctionLibrary.md5(input));
        assertThat(DigestFunctionLibrary.sha1(input)).isEqualTo(DigestFunctionLibrary.sha1(input));
    }

    @Test
    void allDigests_whenDifferentInput_produceDifferentHashes() {
        var input1 = Value.of("test");
        var input2 = Value.of("test2");

        assertThat(DigestFunctionLibrary.sha256(input1)).isNotEqualTo(DigestFunctionLibrary.sha256(input2));
        assertThat(DigestFunctionLibrary.sha384(input1)).isNotEqualTo(DigestFunctionLibrary.sha384(input2));
        assertThat(DigestFunctionLibrary.sha512(input1)).isNotEqualTo(DigestFunctionLibrary.sha512(input2));
    }

    @Test
    void differentAlgorithms_whenSameInput_produceDifferentHashes() {
        var input  = Value.of("test");
        var sha256 = DigestFunctionLibrary.sha256(input);
        var sha512 = DigestFunctionLibrary.sha512(input);
        var sha3   = DigestFunctionLibrary.sha3256(input);
        var md5    = DigestFunctionLibrary.md5(input);
        var sha1   = DigestFunctionLibrary.sha1(input);

        assertThat(sha256).isNotEqualTo(sha512).isNotEqualTo(sha3).isNotEqualTo(md5).isNotEqualTo(sha1)
                .isNotEqualTo(sha3);
    }

    @Test
    void sha2AndSha3Families_produceDifferentHashesForSameInput() {
        var input = Value.of("hello");

        assertThat(DigestFunctionLibrary.sha256(input)).isNotEqualTo(DigestFunctionLibrary.sha3256(input));
    }

    /**
     * Test data for special characters and edge cases.
     */
    private static Stream<Arguments> specialInputTestCases() {
        return Stream.of(arguments("special characters", "!@#$%^&*(){}[]|\\:;\"'<>,.?/~`"),
                arguments("Unicode - Chinese and emoji", "Hello 世界 🌍"), arguments("Unicode - emoji only", "😀🎉🌟"),
                arguments("Unicode - accented characters", "café résumé naïve"),
                arguments("whitespace - spaces", "   "), arguments("whitespace - tabs", "\t\t\t"),
                arguments("whitespace - newlines", "\n\n\n"), arguments("whitespace - mixed", " \t\n "),
                arguments("long input", "a".repeat(10000)));
    }

    @ParameterizedTest(name = "All algorithms should handle: {0}")
    @MethodSource("specialInputTestCases")
    void allDigests_handleSpecialInputsCorrectly(String description, String input) {
        var value = Value.of(input);

        assertThat(DigestFunctionLibrary.sha256(value)).isNotInstanceOf(ErrorValue.class);
        assertThat(DigestFunctionLibrary.sha384(value)).isNotInstanceOf(ErrorValue.class);
        assertThat(DigestFunctionLibrary.sha512(value)).isNotInstanceOf(ErrorValue.class);
        assertThat(DigestFunctionLibrary.sha3256(value)).isNotInstanceOf(ErrorValue.class);
        assertThat(DigestFunctionLibrary.sha3384(value)).isNotInstanceOf(ErrorValue.class);
        assertThat(DigestFunctionLibrary.sha3512(value)).isNotInstanceOf(ErrorValue.class);
        assertThat(DigestFunctionLibrary.md5(value)).isNotInstanceOf(ErrorValue.class);
        assertThat(DigestFunctionLibrary.sha1(value)).isNotInstanceOf(ErrorValue.class);
    }

    /**
     * Verify that Unicode characters are correctly encoded to UTF-8 before hashing.
     * This test uses known hash values
     * computed with UTF-8 encoding.
     */
    @Test
    void allDigests_whenUnicodeInput_useUtf8Encoding() {
        // "Hello 世界 🌍" encoded as UTF-8 and hashed with SHA-256
        var unicodeInput = Value.of("Hello 世界 🌍");
        var result       = DigestFunctionLibrary.sha256(unicodeInput);

        assertThat(result).isEqualTo(Value.of("a5b004d1b8d68d38117e37f2a83454dde19f1e8fd21ebc39f6c1e23df02dc116"));

        // Verify another Unicode example with SHA-512
        var emojiInput  = Value.of("😀");
        var emojiResult = DigestFunctionLibrary.sha512(emojiInput);

        assertThat(emojiResult).isEqualTo(Value.of(
                "9b1ce8b6649e678e1cb7bca85afeaae750add5cfb0668d25ebba5e7f0038f1b6bdcc4bacd909049e752be2a3a3c0158c0f2bb5a33d8101b2ed5d74a66ece2425"));
    }

    /**
     * Helper method to get the appropriate digest function by algorithm name.
     */
    private Function<TextValue, Value> getDigestFunction(String algorithm) {
        return switch (algorithm) {
        case "sha256"   -> DigestFunctionLibrary::sha256;
        case "sha384"   -> DigestFunctionLibrary::sha384;
        case "sha512"   -> DigestFunctionLibrary::sha512;
        case "sha3_256" -> DigestFunctionLibrary::sha3256;
        case "sha3_384" -> DigestFunctionLibrary::sha3384;
        case "sha3_512" -> DigestFunctionLibrary::sha3512;
        case "md5"      -> DigestFunctionLibrary::md5;
        case "sha1"     -> DigestFunctionLibrary::sha1;
        default         -> throw new IllegalArgumentException("Unknown algorithm: " + algorithm);
        };
    }
}
