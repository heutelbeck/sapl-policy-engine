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
package io.sapl.node.cli;

import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Option;

class RemoteConnectionOptions {

    @Option(names = "--remote", required = true, description = "Use remote PDP instead of local policies")
    boolean remote;

    @Option(names = "--url", description = "Remote PDP URL (default: ${DEFAULT-VALUE})", defaultValue = "http://localhost:8443")
    String url;

    @ArgGroup(exclusive = true)
    AuthOptions auth;

    @Option(names = "--insecure", description = "Skip TLS certificate verification")
    boolean insecure;

    static class AuthOptions {

        @Option(names = "--basic-auth", description = "Basic auth credentials (user:password)")
        String basicAuth;

        @Option(names = "--token", description = "Bearer token (API key or JWT)")
        String token;

    }

}
