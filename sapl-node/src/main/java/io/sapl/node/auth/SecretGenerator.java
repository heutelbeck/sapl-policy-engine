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
package io.sapl.node.auth;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import org.passay.data.CharacterData;
import org.passay.data.EnglishCharacterData;
import org.passay.generate.PasswordGenerator;
import org.passay.rule.CharacterRule;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import lombok.experimental.UtilityClass;
import lombok.val;

@UtilityClass
public class SecretGenerator {

    public static final int  BASIC_KEY_LENGTH    = 10;
    public static final int  BASIC_SECRET_LENGTH = 32;
    public static final int  MIN_API_KEY_LENGTH  = 32;
    private static final int RETRY_LIMIT         = 2;

    public static String newSecret() {
        return generatePassword(BASIC_SECRET_LENGTH);
    }

    public static String newKey() {
        return generateKey(BASIC_KEY_LENGTH);
    }

    public static String newApiKey() {
        return generateKey(MIN_API_KEY_LENGTH);
    }

    /**
     * Argon2 parameters from Spring Security 5.8: m=16384 (16 MiB), t=2,
     * p=1, salt=16 bytes, hash=32 bytes. These are below OWASP 2024
     * recommendations (m=19456 / t=2) but kept on the Spring Security
     * defaults so a credential generated here matches what Spring's own
     * matcher accepts without configuration drift across releases.
     * Operators with stricter requirements can re-encode externally.
     */
    public static String encodeWithArgon2(String secret) {
        val encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        return encoder.encode(secret);
    }

    private static final CharacterData SPECIAL_CHARACTERS = new CharacterData() {
        @Override
        public String getErrorCode() {
            return "ERR_SPECIAL";
        }

        @Override
        public String getCharacters() {
            return "$-_.+!*'(),";
        }
    };

    private static List<CharacterRule> baseRules() {
        return List.of(new CharacterRule(EnglishCharacterData.LowerCase, 2),
                new CharacterRule(EnglishCharacterData.UpperCase, 2), new CharacterRule(EnglishCharacterData.Digit, 2));
    }

    // Explicit non-blocking SecureRandom: Passay's default constructor calls
    // SecureRandom.getInstance(...), which can resolve to NativePRNGBlocking
    // and stall on /dev/random in GraalVM native images at startup.
    // new SecureRandom() uses /dev/urandom on Linux and is non-blocking.
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static String generateKey(int length) {
        return new PasswordGenerator(SECURE_RANDOM, length, RETRY_LIMIT, baseRules()).generate().toString();
    }

    private static String generatePassword(int length) {
        val rules = new ArrayList<CharacterRule>();
        rules.add(new CharacterRule(SPECIAL_CHARACTERS, 2));
        rules.addAll(baseRules());
        return new PasswordGenerator(SECURE_RANDOM, length, RETRY_LIMIT, rules).generate().toString();
    }
}
