/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.server.lt;

import org.passay.CharacterRule;
import org.passay.CharacterData;
import org.passay.EnglishCharacterData;
import org.passay.PasswordGenerator;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import lombok.experimental.UtilityClass;

@UtilityClass
public class SecretGenerator {

    public static final int BASIC_KEY_LENGTH    = 10;
    public static final int BASIC_SECRET_LENGTH = 32;
    public static final int MIN_API_KEY_LENGTH  = 32;

    public String newSecret() {
        return generatePassword(BASIC_SECRET_LENGTH);
    }

    public String newKey() {
        return generateKey(BASIC_KEY_LENGTH);
    }

    public String newApiKey() {
        return generateKey(MIN_API_KEY_LENGTH);
    }

    public String encodeWithArgon2(String secret) {
        var encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        return encoder.encode(secret);
    }

    private String generateKey(int length) {
        var passwordGenerator = new PasswordGenerator();
        var lowerCaseRule     = new CharacterRule(EnglishCharacterData.LowerCase, 2);
        var upperCaseRule     = new CharacterRule(EnglishCharacterData.UpperCase, 2);
        var digitRule         = new CharacterRule(EnglishCharacterData.Digit, 2);
        return passwordGenerator.generatePassword(length, lowerCaseRule, upperCaseRule, digitRule);
    }

    private String generatePassword(int length) {
        var passwordGenerator = new PasswordGenerator();
        var lowerCaseRule     = new CharacterRule(EnglishCharacterData.LowerCase, 2);
        var upperCaseRule     = new CharacterRule(EnglishCharacterData.UpperCase, 2);
        var digitRule         = new CharacterRule(EnglishCharacterData.Digit, 2);
        var splCharRule       = new CharacterRule(new CharacterData() {
                                  @Override
                                  public String getErrorCode() {
                                      return "ERR_SPECIAL";
                                  }

                                  @Override
                                  public String getCharacters() {
                                      return "$-_.+!*'(),";
                                  }
                              }, 2);
        return passwordGenerator.generatePassword(length, splCharRule, lowerCaseRule, upperCaseRule, digitRule);
    }
}
