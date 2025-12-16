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
package io.sapl.pdp.configuration.bundle;

import io.sapl.api.SaplVersion;
import lombok.experimental.StandardException;

import java.io.Serial;

/**
 * Exception thrown when bundle signature verification fails.
 * <p>
 * This exception indicates a security-relevant failure during bundle
 * verification. Possible causes include:
 * </p>
 * <ul>
 * <li>Missing signature when one is required</li>
 * <li>Invalid or corrupted signature</li>
 * <li>Signature created with unknown or untrusted key</li>
 * <li>File hash mismatch (bundle contents were modified)</li>
 * <li>Expired signature</li>
 * <li>Extra files in bundle not covered by signature</li>
 * <li>Missing files that were signed</li>
 * </ul>
 *
 * @see BundleManifest
 * @see BundleParser
 */
@StandardException
public class BundleSignatureException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;
}
