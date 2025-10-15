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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DigestFunctionLibraryTests {

    /* SHA-256 Tests */

    @Test
    void sha256_whenSimpleText_computesCorrectHash() {
        var result = DigestFunctionLibrary.sha256(Val.of("hello"));
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", result.getText());
    }

    @Test
    void sha256_whenEmptyString_computesCorrectHash() {
        var result = DigestFunctionLibrary.sha256(Val.of(""));
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", result.getText());
    }

    @Test
    void sha256_whenSameInput_producesConsistentHash() {
        var result1 = DigestFunctionLibrary.sha256(Val.of("test"));
        var result2 = DigestFunctionLibrary.sha256(Val.of("test"));
        assertEquals(result1.getText(), result2.getText());
    }

    @Test
    void sha256_whenDifferentInput_producesDifferentHash() {
        var result1 = DigestFunctionLibrary.sha256(Val.of("test"));
        var result2 = DigestFunctionLibrary.sha256(Val.of("test2"));
        assertNotEquals(result1.getText(), result2.getText());
    }

    @Test
    void sha256_returnsLowercaseHex() {
        var result = DigestFunctionLibrary.sha256(Val.of("hello"));
        assertEquals(result.getText(), result.getText().toLowerCase());
        assertEquals(64, result.getText().length());
    }

    /* SHA-384 Tests */

    @Test
    void sha384_whenSimpleText_computesCorrectHash() {
        var result = DigestFunctionLibrary.sha384(Val.of("hello"));
        assertEquals("59e1748777448c69de6b800d7a33bbfb9ff1b463e44354c3553bcdb9c666fa90125a3c79f90397bdf5f6a13de828684f",
                result.getText());
    }

    @Test
    void sha384_whenEmptyString_computesCorrectHash() {
        var result = DigestFunctionLibrary.sha384(Val.of(""));
        assertEquals("38b060a751ac96384cd9327eb1b1e36a21fdb71114be07434c0cc7bf63f6e1da274edebfe76f65fbd51ad2f14898b95b",
                result.getText());
    }

    @Test
    void sha384_returnsLowercaseHex() {
        var result = DigestFunctionLibrary.sha384(Val.of("hello"));
        assertEquals(result.getText(), result.getText().toLowerCase());
        assertEquals(96, result.getText().length());
    }

    /* SHA-512 Tests */

    @Test
    void sha512_whenSimpleText_computesCorrectHash() {
        var result = DigestFunctionLibrary.sha512(Val.of("hello"));
        assertEquals(
                "9b71d224bd62f3785d96d46ad3ea3d73319bfbc2890caadae2dff72519673ca72323c3d99ba5c11d7c7acc6e14b8c5da0c4663475c2e5c3adef46f73bcdec043",
                result.getText());
    }

    @Test
    void sha512_whenEmptyString_computesCorrectHash() {
        var result = DigestFunctionLibrary.sha512(Val.of(""));
        assertEquals(
                "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e",
                result.getText());
    }

    @Test
    void sha512_returnsLowercaseHex() {
        var result = DigestFunctionLibrary.sha512(Val.of("hello"));
        assertEquals(result.getText(), result.getText().toLowerCase());
        assertEquals(128, result.getText().length());
    }

    /* SHA3-256 Tests */

    @Test
    void sha3256_whenSimpleText_computesCorrectHash() {
        var result = DigestFunctionLibrary.sha3256(Val.of("hello"));
        assertEquals("3338be694f50c5f338814986cdf0686453a888b84f424d792af4b9202398f392", result.getText());
    }

    @Test
    void sha3256_whenEmptyString_computesCorrectHash() {
        var result = DigestFunctionLibrary.sha3256(Val.of(""));
        assertEquals("a7ffc6f8bf1ed76651c14756a061d662f580ff4de43b49fa82d80a4b80f8434a", result.getText());
    }

    @Test
    void sha3_256_differFromSha256() {
        var sha2Result = DigestFunctionLibrary.sha256(Val.of("hello"));
        var sha3Result = DigestFunctionLibrary.sha3256(Val.of("hello"));
        assertNotEquals(sha2Result.getText(), sha3Result.getText());
    }

    @Test
    void sha3256_returnsLowercaseHex() {
        var result = DigestFunctionLibrary.sha3256(Val.of("hello"));
        assertEquals(result.getText(), result.getText().toLowerCase());
        assertEquals(64, result.getText().length());
    }

    /* SHA3-384 Tests */

    @Test
    void sha3384_whenSimpleText_computesCorrectHash() {
        var result = DigestFunctionLibrary.sha3384(Val.of("hello"));
        assertEquals("720aea11019ef06440fbf05d87aa24680a2153df3907b23631e7177ce620fa1330ff07c0fddee54699a4c3ee0ee9d887",
                result.getText());
    }

    @Test
    void sha3384_returnsLowercaseHex() {
        var result = DigestFunctionLibrary.sha3384(Val.of("hello"));
        assertEquals(result.getText(), result.getText().toLowerCase());
        assertEquals(96, result.getText().length());
    }

    /* SHA3-512 Tests */

    @Test
    void sha3512_whenSimpleText_computesCorrectHash() {
        var result = DigestFunctionLibrary.sha3512(Val.of("hello"));
        assertEquals(
                "75d527c368f2efe848ecf6b073a36767800805e9eef2b1857d5f984f036eb6df891d75f72d9b154518c1cd58835286d1da9a38deba3de98b5a53e5ed78a84976",
                result.getText());
    }

    @Test
    void sha3512_returnsLowercaseHex() {
        var result = DigestFunctionLibrary.sha3512(Val.of("hello"));
        assertEquals(result.getText(), result.getText().toLowerCase());
        assertEquals(128, result.getText().length());
    }

    /* MD5 Tests (Legacy) */

    @Test
    void md5_whenSimpleText_computesCorrectHash() {
        var result = DigestFunctionLibrary.md5(Val.of("hello"));
        assertEquals("5d41402abc4b2a76b9719d911017c592", result.getText());
    }

    @Test
    void md5_whenEmptyString_computesCorrectHash() {
        var result = DigestFunctionLibrary.md5(Val.of(""));
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", result.getText());
    }

    @Test
    void md5_returnsLowercaseHex() {
        var result = DigestFunctionLibrary.md5(Val.of("hello"));
        assertEquals(result.getText(), result.getText().toLowerCase());
        assertEquals(32, result.getText().length());
    }

    /* SHA-1 Tests (Legacy) */

    @Test
    void sha1_whenSimpleText_computesCorrectHash() {
        var result = DigestFunctionLibrary.sha1(Val.of("hello"));
        assertEquals("aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d", result.getText());
    }

    @Test
    void sha1_whenEmptyString_computesCorrectHash() {
        var result = DigestFunctionLibrary.sha1(Val.of(""));
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", result.getText());
    }

    @Test
    void sha1_returnsLowercaseHex() {
        var result = DigestFunctionLibrary.sha1(Val.of("hello"));
        assertEquals(result.getText(), result.getText().toLowerCase());
        assertEquals(40, result.getText().length());
    }

    /* Behavior Tests */

    @Test
    void allDigests_whenSpecialCharacters_handleCorrectly() {
        var input = Val.of("!@#$%^&*(){}[]|\\:;\"'<>,.?/~`");
        assertTrue(DigestFunctionLibrary.sha256(input).isDefined());
        assertTrue(DigestFunctionLibrary.sha384(input).isDefined());
        assertTrue(DigestFunctionLibrary.sha512(input).isDefined());
        assertTrue(DigestFunctionLibrary.sha3256(input).isDefined());
        assertTrue(DigestFunctionLibrary.sha3384(input).isDefined());
        assertTrue(DigestFunctionLibrary.sha3512(input).isDefined());
        assertTrue(DigestFunctionLibrary.md5(input).isDefined());
        assertTrue(DigestFunctionLibrary.sha1(input).isDefined());
    }

    @Test
    void allDigests_whenUnicodeCharacters_handleCorrectly() {
        var input = Val.of("Hello 世界 🌍");
        assertTrue(DigestFunctionLibrary.sha256(input).isDefined());
        assertTrue(DigestFunctionLibrary.sha384(input).isDefined());
        assertTrue(DigestFunctionLibrary.sha512(input).isDefined());
        assertTrue(DigestFunctionLibrary.sha3256(input).isDefined());
        assertTrue(DigestFunctionLibrary.md5(input).isDefined());
        assertTrue(DigestFunctionLibrary.sha1(input).isDefined());
    }

    @Test
    void allDigests_produceConsistentResults() {
        var input = Val.of("consistency test");

        var sha256First  = DigestFunctionLibrary.sha256(input);
        var sha256Second = DigestFunctionLibrary.sha256(input);
        assertEquals(sha256First.getText(), sha256Second.getText());

        var sha512First  = DigestFunctionLibrary.sha512(input);
        var sha512Second = DigestFunctionLibrary.sha512(input);
        assertEquals(sha512First.getText(), sha512Second.getText());
    }

    @Test
    void differentAlgorithms_produceDifferentHashes() {
        var input  = Val.of("test");
        var sha256 = DigestFunctionLibrary.sha256(input);
        var sha512 = DigestFunctionLibrary.sha512(input);
        var sha3   = DigestFunctionLibrary.sha3256(input);
        var md5    = DigestFunctionLibrary.md5(input);

        assertNotEquals(sha256.getText(), sha512.getText());
        assertNotEquals(sha256.getText(), sha3.getText());
        assertNotEquals(sha256.getText(), md5.getText());
        assertNotEquals(sha512.getText(), sha3.getText());
    }
}
