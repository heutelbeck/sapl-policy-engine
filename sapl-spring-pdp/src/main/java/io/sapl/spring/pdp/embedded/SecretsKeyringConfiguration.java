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
package io.sapl.spring.pdp.embedded;

import java.util.Objects;
import java.util.Set;

import io.sapl.secrets.Keyring;

/**
 * Validated bundle-recipient configuration shared by source verification and
 * unsealing. Its string representation never includes key material.
 */
record SecretsKeyringConfiguration(Keyring keyring, boolean acceptUnencrypted) {

    SecretsKeyringConfiguration {
        Objects.requireNonNull(keyring, "keyring");
    }

    boolean hasKeys() {
        return !keyring.privateKeysByKeyId().isEmpty();
    }

    Set<String> keyIds() {
        return keyring.privateKeysByKeyId().keySet();
    }

    @Override
    public String toString() {
        return "SecretsKeyringConfiguration[keyIds=" + keyIds() + ", acceptUnencrypted=" + acceptUnencrypted + "]";
    }
}
