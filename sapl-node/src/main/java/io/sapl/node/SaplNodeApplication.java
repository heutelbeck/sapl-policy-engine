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
package io.sapl.node;

import java.util.Set;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;

import io.sapl.node.cli.SaplNodeCli;
import picocli.CommandLine;

@EnableCaching
@SpringBootApplication(excludeName = "org.springframework.boot.autoconfigure.security.rsocket.RSocketSecurityAutoConfiguration")
@ComponentScan({ "io.sapl.node", "io.sapl.server" })
@EnableConfigurationProperties(SaplNodeProperties.class)
public class SaplNodeApplication {

    private static final Set<String> CLI_ONLY_ARGS = Set.of("--help", "-h", "--version", "-V", "bundle", "generate");

    public static void main(String[] args) {
        int exitCode = run(args);
        if (exitCode != 0 || isCliOnlyCommand(args)) {
            System.exit(exitCode);
        }
    }

    /**
     * Runs the application and returns an exit code. Testable entry point.
     */
    static int run(String[] args) {
        if (isCliOnlyCommand(args)) {
            return new CommandLine(new SaplNodeCli()).execute(args);
        }
        SpringApplication.run(SaplNodeApplication.class, args);
        return 0;
    }

    private static boolean isCliOnlyCommand(String[] args) {
        for (var arg : args) {
            if (CLI_ONLY_ARGS.contains(arg)) {
                return true;
            }
        }
        return false;
    }

}
