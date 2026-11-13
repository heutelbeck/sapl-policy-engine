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

import picocli.CommandLine;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Picocli mixin for the additional {@code --output} option for the attribute
 * commands. Commands write their result into the given file. Otherwise they
 * write it directly into the console.
 */
public class FileMixin implements AutoCloseable {
    @Spec
    CommandSpec spec;

    @CommandLine.Option(names = "--output")
    Path file;

    private PrintWriter writer;

    /**
     * Returns the writer for the command output. Created at first use and reused afterwards.
     *
     * @return a writer for the {@code --output} file.
     * @throws IOException if the output cannot be opened.
     */
    public PrintWriter getFileWriter() throws IOException {
        if (writer == null) {
            writer = file != null ? new PrintWriter(Files.newBufferedWriter(file)) : spec.commandLine().getOut();
        }
        return writer;
    }

    /**
     * Runs the command and closes the output file afterwards.
     *
     * @param action the command to run.
     * @return the exit code of the command.
     * @throws Exception if the command fails.
     */
    public Integer run(Callable<Integer> action) throws Exception {
        try (this) {
            return action.call();
        }
    }

    @Override
    public void close() {
        if (file != null && writer != null) {
            writer.close();
        }
    }
}
