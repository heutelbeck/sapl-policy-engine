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
package io.sapl.node.cli.commands;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;

/**
 * Starts the SAPL PDP server. This is the default behavior when no subcommand
 * is specified.
 * <p>
 * Server startup is handled by {@code SaplNodeApplication} before picocli
 * executes, so this command exists primarily for help text and documentation
 * generation.
 */
// @formatter:off
@Command(
    name = "server",
    mixinStandardHelpOptions = true,
    header = "Start the PDP server (default when no subcommand is given).",
    description = { """
        Launches the SAPL Policy Decision Point as an HTTP server. Clients
        send authorization subscriptions via the HTTP API and receive
        decisions as JSON responses or Server-Sent Event streams.

        Optionally, a high-performance RSocket endpoint with protobuf
        serialization can be enabled for lower-latency authorization.
        Enable with --sapl.pdp.rsocket.enabled=true (default port: 7000).

        The server is configured via application.yml. Place it in a config/
        subdirectory of the working directory, or specify a custom location
        with --spring.config.location=file:/path/to/application.yml.

        Any Spring Boot property can be overridden on the command line:
          --server.port=9090
          --sapl.pdp.rsocket.enabled=true
          --sapl.pdp.rsocket.port=7000

        Key configuration areas: policy source type (DIRECTORY, BUNDLES),
        authentication (no-auth, basic, API key, OAuth2), TLS, RSocket,
        and observability (health endpoints, Prometheus metrics).
        """ },
    exitCodeListHeading = "%nExit Codes:%n",
    exitCodeList = {
        " 0:Clean shutdown",
        " 1:Startup or runtime error"
    },
    footerHeading = "%nExamples:%n",
    footer = { """
          # Start with default settings
          sapl server

          # Start on a custom port
          sapl server --server.port=9090

          # Use a custom configuration file
          sapl server --spring.config.location=file:/etc/sapl/application.yml

        See Also: sapl-generate-basic(1), sapl-generate-apikey(1)
        """ }
)
// @formatter:on
public class ServerCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        return 0;
    }

}
