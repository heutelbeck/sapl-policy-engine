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
package io.sapl.functions.libraries.crypto;

import io.sapl.api.SaplVersion;
import lombok.experimental.StandardException;

import java.io.Serial;

/**
 * Exception for signaling errors during cryptographic operations. This
 * exception is used within the crypto function
 * libraries for traced errors flow control. All instances are caught at the
 * public API boundary and converted to
 * ErrorValue before returning to callers.
 * <p>
 * This is an traced API and should not be used outside the SAPL function
 * libraries.
 */
@StandardException
public class CryptoException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;
}
