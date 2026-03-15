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

import static io.sapl.node.SecretGenerator.encodeWithArgon2;
import static io.sapl.node.SecretGenerator.newApiKey;
import static io.sapl.node.SecretGenerator.newKey;
import static io.sapl.node.SecretGenerator.newSecret;

import java.util.concurrent.Callable;

import lombok.val;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Commands for generating authentication credentials.
 */
// @formatter:off
@Command(
    name = "generate",
    mixinStandardHelpOptions = true,
    header = "Generate authentication credentials for PDP server clients.",
    description = { """
        Creates credentials with Argon2id-encoded hashes and outputs
        ready-to-use configuration snippets for application.yml.
        Credentials can use HTTP Basic Auth or API key (Bearer token).
        """ },
    subcommands = {
        GenerateCredentialsCommand.BasicCredentials.class,
        GenerateCredentialsCommand.ApiKey.class
    }
)
// @formatter:on
class GenerateCredentialsCommand {

    // @formatter:off
    @Command(
        name = "basic",
        mixinStandardHelpOptions = true,
        header = "Generate HTTP Basic Auth credentials with Argon2id-encoded password.",
        description = { """
            Creates a random username and password, encodes the password
            with Argon2id, and prints the credentials along with an
            application.yml configuration snippet and a curl usage example.

            Store the plaintext password securely. Only the Argon2id hash
            goes into server configuration.
            """ },
        exitCodeListHeading = "%nExit Codes:%n",
        exitCodeList = {
            " 0:Credentials generated successfully",
            " 1:Error during generation"
        },
        footerHeading = "%nExamples:%n",
        footer = { """
              # Generate random credentials
              sapl generate basic

              # Generate with custom ID and PDP routing
              sapl generate basic --id my-client --pdp-id production

            See Also: sapl-generate-apikey(1), sapl-server(1)
            """ }
    )
    // @formatter:on
    static class BasicCredentials implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        @Option(names = { "-i", "--id" }, description = "User ID (default: generated)", defaultValue = "")
        private String userId;

        @Option(names = { "-p",
                "--pdp-id" }, description = "PDP ID for routing (default: 'default')", defaultValue = "default")
        private String pdpId;

        @Override
        public Integer call() {
            val id            = userId.isEmpty() ? "user-" + newKey() : userId;
            val username      = newKey();
            val secret        = newSecret();
            val encodedSecret = encodeWithArgon2(secret);
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

    // @formatter:off
    @Command(
        name = "apikey",
        mixinStandardHelpOptions = true,
        header = "Generate a Bearer token API key with Argon2id-encoded hash.",
        description = { """
            Creates an API key with the format sapl_<random> and encodes
            it with Argon2id. Prints the key along with an application.yml
            configuration snippet and a curl usage example.

            The API key is used as a Bearer token in the Authorization
            header.
            """ },
        exitCodeListHeading = "%nExit Codes:%n",
        exitCodeList = {
            " 0:API key generated successfully",
            " 1:Error during generation"
        },
        footerHeading = "%nExamples:%n",
        footer = { """
              # Generate a random API key
              sapl generate apikey

              # Generate with custom ID and PDP routing
              sapl generate apikey --id my-service --pdp-id production

            See Also: sapl-generate-basic(1), sapl-server(1)
            """ }
    )
    // @formatter:on
    static class ApiKey implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        @Option(names = { "-i", "--id" }, description = "User ID (default: generated)", defaultValue = "")
        private String userId;

        @Option(names = { "-p",
                "--pdp-id" }, description = "PDP ID for routing (default: 'default')", defaultValue = "default")
        private String pdpId;

        @Override
        public Integer call() {
            val id         = userId.isEmpty() ? "user-" + newKey() : userId;
            val key        = newKey();
            val secret     = newApiKey();
            val apiKey     = "sapl_" + key + "_" + secret;
            val encodedKey = encodeWithArgon2(apiKey);
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
