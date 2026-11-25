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
package io.sapl.attributes.http;

import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Single HTTP response body deadline")
class BlockingWebClientReadTimeoutTests {

    @Test
    @DisplayName("a partial response is closed when its body deadline expires")
    void whenPartialResponseMissesDeadlineThenTimeoutClosesBody() throws IOException {
        try (val body = new PipedInputStream(); val sender = new PipedOutputStream(body)) {
            sender.write('t');

            assertThatThrownBy(() -> BlockingWebClient.readBody(body, 2, Duration.ofMillis(50)))
                    .isInstanceOf(TimeoutException.class);
            assertThatThrownBy(() -> sender.write('r')).isInstanceOf(IOException.class);
        }
    }
}
