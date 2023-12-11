/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.test.dsl.setup;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.sapl.test.dsl.interfaces.TestNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class TestContainerTest {

    @Test
    void from_buildsTestContainerWithGivenIdentifierAndTestNodes_returnsTestContainer() {
        final var testNodes = List.<TestNode>of();

        final var container = TestContainer.from("identifier", testNodes);

        assertEquals("identifier", container.getIdentifier());
        assertEquals(testNodes, container.getTestNodes());
    }
}
