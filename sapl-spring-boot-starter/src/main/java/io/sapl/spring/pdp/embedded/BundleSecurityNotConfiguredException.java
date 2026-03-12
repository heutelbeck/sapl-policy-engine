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

import java.io.Serial;
import java.nio.file.Path;

/**
 * Thrown when the PDP is configured for BUNDLES or REMOTE_BUNDLES mode but no
 * bundle security policy has been set up. Carries the resolved policies path
 * for context-aware error reporting via
 * {@link BundleSecurityFailureAnalyzer}.
 */
class BundleSecurityNotConfiguredException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Path policiesPath;

    BundleSecurityNotConfiguredException(Path policiesPath) {
        super("Bundle security not configured");
        this.policiesPath = policiesPath;
    }

    Path getPoliciesPath() {
        return policiesPath;
    }

}
