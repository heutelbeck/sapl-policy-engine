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

import lombok.val;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

class FileMixinTests {
    @Nested
    @DisplayName("when the file options are used for the attributes command")
    class WhenAttributeApiResponds {
        private final FileMixin mixin = new FileMixin();

        @TempDir
        Path dir;

        private final StringWriter out = new StringWriter();
        private final CommandLine  cmd = new CommandLine(new GetAttributeCommand());
        private AttributeApiStub   apiServer;

        @BeforeEach
        void setUp() throws IOException {
            apiServer = new AttributeApiStub();
            cmd.setOut(new PrintWriter(out));
        }

        @AfterEach
        void tearDown() {
            apiServer.close();
        }

        @Test
        @DisplayName("then the results are displayed and the content written into the file")
        void thenResultsAreWrittenAndFileIsWritten() throws Exception {
            mixin.file = dir.resolve("test.txt");

            val result = mixin.run(() -> {
                mixin.getFileWriter().println("testcontent");
                return 0;
            });

            assertThat(result).isZero();
            assertThat(Files.readString(mixin.file)).contains("testcontent");
        }

        @Test
        @DisplayName("then a repeated file calls returns the same writer")
        void thenRepeatedCallReturnsSameWriter() throws Exception {
            mixin.file = dir.resolve("test.txt");

            val firstWriter  = mixin.getFileWriter();
            val secondWriter = mixin.getFileWriter();

            assertThat(secondWriter).isSameAs(firstWriter);
            mixin.close();
        }

        @Test
        @DisplayName("then the command line writer is used")
        void thenCommandlineOutputWriterIsUsed() throws Exception {
            cmd.setOut(new PrintWriter(new StringWriter()));
            mixin.spec = cmd.getCommandSpec();
            assertThat(mixin.getFileWriter()).isSameAs(cmd.getOut());
        }
    }
}
