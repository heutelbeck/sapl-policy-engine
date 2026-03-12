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

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Presents a clean, actionable startup message when bundle security is not
 * configured. Replaces the raw stack trace with a structured description and
 * concrete commands using the actual runtime policies path.
 */
class BundleSecurityFailureAnalyzer extends AbstractFailureAnalyzer<BundleSecurityNotConfiguredException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, BundleSecurityNotConfiguredException cause) {
        var path        = cause.getPoliciesPath();
        var description = "The PDP is configured for BUNDLES mode but no bundle security policy has been set. "
                + "Policies path: " + path;
        var action      = "To enable signature verification, generate a keypair and configure the public key:\n\n"
                + "  sapl-node bundle keygen -o signing\n" + "  sapl-node bundle create -i <policy-dir> -o " + path
                + "/default.saplbundle -k signing.pem\n\n" + "Then add to your application.yml:\n\n"
                + "  io.sapl.pdp.embedded:\n" + "    bundle-security:\n" + "      public-key-path: signing.pub\n\n"
                + "To opt out of signature verification (development only):\n\n" + "  io.sapl.pdp.embedded:\n"
                + "    bundle-security:\n" + "      allow-unsigned: true\n\n"
                + "Alternatively, if you do not need atomic bundle deploys, you can switch\n"
                + "the policy source type by setting io.sapl.pdp.embedded.pdp-config-type to\n"
                + "DIRECTORY (watch individual .sapl files) or REMOTE_BUNDLES (fetch bundles\n"
                + "from an HTTP server). See the configuration reference for details.";
        return new FailureAnalysis(description, action, cause);
    }

}
