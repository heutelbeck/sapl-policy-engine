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

import java.util.List;
import java.util.concurrent.Callable;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.functions.libraries.SaplFunctionLibrary;
import io.sapl.node.SaplNodeApplication;
import lombok.val;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Unmatched;

/**
 * Main CLI entry point for sapl-node.
 * <p>
 * When no command is specified, starts the PDP server (Spring Boot).
 * Subcommands like 'bundle' are handled by Picocli without starting Spring.
 */
@Command(name = "sapl-node", description = "SAPL Policy Decision Point Server", mixinStandardHelpOptions = true, versionProvider = SaplNodeCli.VersionProvider.class, subcommands = {
        BundleCommand.class, GenerateCredentialsCommand.class })
public class SaplNodeCli implements Callable<Integer> {

    @Unmatched
    List<String> springArgs;

    @Override
    public Integer call() {
        // Default behavior: start Spring Boot server
        // Pass any unmatched args to Spring Boot
        val args = springArgs != null ? springArgs.toArray(String[]::new) : new String[0];
        SaplNodeApplication.runServer(args);
        return 0;
    }

    public static void main(String[] args) {
        val exitCode = new CommandLine(new SaplNodeCli()).execute(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static class VersionProvider implements CommandLine.IVersionProvider {

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
