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
package io.sapl.server.ce.model.setup;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.regex.Pattern;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AdminUserConfig {
    static final String USERNAME_PATH        = "io.sapl.server.accesscontrol.admin-username";
    static final String ENCODEDPASSWORD_PATH = "io.sapl.server.accesscontrol.encoded-admin-password";

    // min 12 characters long, 1 lower case letter, 1 upper case letter, 1 digit, 1
    // special character
    private static final Pattern strongPasswordPattern = Pattern
            .compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%^&+=!]).{12,}$");

    // min 8 characters long, 1 lower case letter, 1 upper case letter, 1 digit, 1
    // special character
    private static final Pattern moderatePasswordPattern = Pattern
            .compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%^&+=!]).{8,}$");

    private String  username       = "";
    private String  password       = "";
    private String  passwordRepeat = "";
    private boolean saved          = false;

    public String getEncodedPassword() {
        PasswordEncoder encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        return encoder.encode(this.password);
    }

    public boolean isValidConfig() {
        return !this.username.isEmpty() && !this.password.isEmpty() && this.password.equals(this.passwordRepeat);
    }

    public AdminUserPasswordStrength getPasswordStrength() {
        if (strongPasswordPattern.matcher(password).matches()) {
            return AdminUserPasswordStrength.STRONG;
        }
        if (moderatePasswordPattern.matcher(password).matches()) {
            return AdminUserPasswordStrength.MODERATE;
        }
        return AdminUserPasswordStrength.WEAK;
    }
}
