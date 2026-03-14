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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import lombok.val;

@DisplayName("decide integration")
class DecideIntegrationTests extends AbstractCliIntegrationTests {

    @Nested
    @DisplayName("directory mode with named flags")
    class DirectoryWithNamedFlagsTests {

        @TempDir
        Path policyDir;

        @Test
        @DisplayName("permit-all policy outputs PERMIT NDJSON line")
        void whenPermitPolicy_thenPermitNdjsonOutput() throws Exception {
            Files.writeString(policyDir.resolve("test.sapl"), "policy \"test\" permit");

            val future = CompletableFuture.supplyAsync(() -> SaplNodeApplication.run(new String[] { "decide", "--dir",
                    policyDir.toString(), "-s", "\"alice\"", "-a", "\"read\"", "-r", "\"document\"" }));

            waitForOutput(5);

            assertThat(capturedOutput()).contains("{\"decision\":\"PERMIT\"}");

            future.cancel(true);
        }

    }

    private void waitForOutput(int timeoutSeconds) throws InterruptedException {
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);
        while (capturedSize() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
    }

}
