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

import picocli.CommandLine.Command;

/**
 * Starts the SAPL PDP server. This is the default behavior when no subcommand
 * is specified.
 * <p>
 * Server startup is handled by {@code SaplNodeApplication} before picocli
 * executes, so this command exists primarily for help text and documentation
 * generation.
 */
@Command(name = "server", description = "Start the PDP server (default when no command is given)", mixinStandardHelpOptions = true)
class ServerCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        return 0;
    }

}
