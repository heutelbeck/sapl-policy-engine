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

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.functions.libraries.SaplFunctionLibrary;
import lombok.val;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;

/**
 * Main CLI entry point for sapl-node.
 * <p>
 * When no subcommand is specified, the server runs (Spring Boot is already
 * started).
 * Subcommands like 'bundle' are handled by picocli within the Spring context.
 */
// @formatter:off
@Command(
    name = "sapl",
    mixinStandardHelpOptions = true,
    versionProvider = SaplNodeCli.VersionProvider.class,
    header = "SAPL Node PDP server and policy CLI.",
    description = { """
        Without a subcommand, starts the PDP server on localhost:8443.
        Use subcommands to evaluate policies, manage bundles, and
        generate credentials without starting the server.

        Policy Sources:
          The check, decide, and decide-once commands load policies from
          one of three sources. By default, .sapl files and pdp.json are
          read from the current directory. Use --dir to specify a different
          directory, --bundle to load a .saplbundle file, or --remote to
          query a running PDP server.

        Authorization Subscriptions:
          An authorization subscription asks "may subject perform action
          on resource?" Each component is a JSON value. Strings must be
          double-quoted inside single quotes: -s '"alice"' (not -s alice).
          Provide the subscription via named flags (-s, -a, -r) or as a
          JSON file (-f). Use -f - to read from stdin.

        Environment Variables:
          SAPL_URL            Remote PDP URL (overridden by --url)
          SAPL_BASIC_AUTH     Basic auth as user:password (overridden by --basic-auth)
          SAPL_BEARER_TOKEN   Bearer token for API key/JWT (overridden by --token)

        Files:
          *.sapl              SAPL policy files
          pdp.json            PDP configuration (combining algorithm, variables)
          *.saplbundle        Policy bundle archive (ZIP with policies and manifest)
          application.yml     Server configuration (in config/ or working directory)
        """ },
    subcommands = {
        ServerCommand.class, BundleCommand.class, CheckCommand.class,
        DecideCommand.class, DecideOnceCommand.class, GenerateCredentialsCommand.class,
        TestCommand.class
    }
)
// @formatter:on
public class SaplNodeCli implements Callable<Integer> {

    @Override
    public Integer call() {
        return 0;
    }

    static class VersionProvider implements IVersionProvider {

        private static final String UNKNOWN = "unknown";

        @Override
        public String[] getVersion() {
            val info = (ObjectValue) SaplFunctionLibrary.info();
            return new String[] { "SAPL Node %s (%s)".formatted(text(info, "saplVersion"), text(info, "gitCommitId")),
                    "Built: %s on branch %s".formatted(text(info, "gitBuildTime"), text(info, "gitBranch")),
                    "Java:  %s (%s)".formatted(text(info, "javaVersion"), text(info, "javaVendor")),
                    "OS:    %s %s %s".formatted(text(info, "osName"), text(info, "osVersion"), text(info, "osArch")) };
        }

        private static String text(ObjectValue info, String key) {
            val value = info.get(key);
            if (value instanceof TextValue(String text)) {
                return text;
            }
            return UNKNOWN;
        }

    }

}
