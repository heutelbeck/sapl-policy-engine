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

import java.util.concurrent.Callable;

import io.sapl.node.SecretGenerator;
import lombok.experimental.UtilityClass;
import lombok.val;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Commands for generating authentication credentials.
 */
@UtilityClass
@Command(name = "generate", description = "Generate authentication credentials", subcommands = {
        GenerateCredentialsCommand.BasicCredentials.class, GenerateCredentialsCommand.ApiKey.class })
class GenerateCredentialsCommand {

    @Command(name = "basic", description = "Generate Basic Auth credentials (Argon2 encoded)")
    static class BasicCredentials implements Callable<Integer> {

        @Spec
        CommandSpec spec;

        @Option(names = { "-i", "--id" }, description = "User ID (default: generated)", defaultValue = "")
        String userId;

        @Option(names = { "-p",
                "--pdp-id" }, description = "PDP ID for routing (default: 'default')", defaultValue = "default")
        String pdpId;

        @Override
        public Integer call() {
            val id            = userId.isEmpty() ? "user-" + SecretGenerator.newKey() : userId;
            val username      = SecretGenerator.newKey();
            val secret        = SecretGenerator.newSecret();
            val encodedSecret = SecretGenerator.encodeWithArgon2(secret);
            spec.commandLine().getOut().printf("""
                    %n\
                    Basic Auth Credentials%n\
                    ======================%n\
                    %n\
                    User ID:  %s%n\
                    PDP ID:   %s%n\
                    Username: %s%n\
                    Password: %s%n\
                    %n\
                    Configuration (application.yml):%n\
                    --------------------------------%n\
                    io.sapl.node:%n\
                      allowBasicAuth: true%n\
                      users:%n\
                        - id: "%s"%n\
                          pdpId: "%s"%n\
                          basic:%n\
                            username: "%s"%n\
                            secret: "%s"%n\
                    %n\
                    Usage (curl):%n\
                    -------------%n\
                    curl -u '%s:%s' \\%n\
                      -X POST https://localhost:8443/api/pdp/decide-once \\%n\
                      -H 'Content-Type: application/json' \\%n\
                      -d '{"subject":"alice","action":"read","resource":"document"}' \\%n\
                      --cacert server.crt%n\
                    %n\
                    IMPORTANT: Store the password securely. Use the encoded value in%n\
                    server configuration; use the plaintext password in clients.%n\
                    """, id, pdpId, username, secret, id, pdpId, username, encodedSecret, username, secret);
            return 0;
        }

    }

    @Command(name = "apikey", description = "Generate an API key")
    static class ApiKey implements Callable<Integer> {

        @Spec
        CommandSpec spec;

        @Option(names = { "-i", "--id" }, description = "User ID (default: generated)", defaultValue = "")
        String userId;

        @Option(names = { "-p",
                "--pdp-id" }, description = "PDP ID for routing (default: 'default')", defaultValue = "default")
        String pdpId;

        @Override
        public Integer call() {
            val id         = userId.isEmpty() ? "user-" + SecretGenerator.newKey() : userId;
            val key        = SecretGenerator.newKey();
            val secret     = SecretGenerator.newApiKey();
            val apiKey     = "sapl_" + key + "_" + secret;
            val encodedKey = SecretGenerator.encodeWithArgon2(apiKey);
            spec.commandLine().getOut().printf("""
                    %n\
                    API Key%n\
                    =======%n\
                    %n\
                    User ID: %s%n\
                    PDP ID:  %s%n\
                    Key:     %s%n\
                    %n\
                    Configuration (application.yml):%n\
                    --------------------------------%n\
                    io.sapl.node:%n\
                      allowApiKeyAuth: true%n\
                      users:%n\
                        - id: "%s"%n\
                          pdpId: "%s"%n\
                          apiKey: "%s"%n\
                    %n\
                    Usage (curl):%n\
                    -------------%n\
                    curl -H 'Authorization: Bearer %s' \\%n\
                      -X POST https://localhost:8443/api/pdp/decide-once \\%n\
                      -H 'Content-Type: application/json' \\%n\
                      -d '{"subject":"alice","action":"read","resource":"document"}' \\%n\
                      --cacert server.crt%n\
                    %n\
                    IMPORTANT: This key grants full PDP access. Store securely and%n\
                    rotate periodically. Multiple users with API keys can be configured.%n\
                    """, id, pdpId, apiKey, id, pdpId, encodedKey, apiKey);
            return 0;
        }

    }

}
