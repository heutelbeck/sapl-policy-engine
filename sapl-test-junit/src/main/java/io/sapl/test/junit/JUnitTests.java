/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.test.junit;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.interfaces.TestNode;
import io.sapl.test.dsl.setup.BaseTestAdapter;
import io.sapl.test.dsl.setup.TestCase;
import io.sapl.test.dsl.setup.TestContainer;

public class JUnitTests extends BaseTestAdapter<DynamicContainer> {

    @TestFactory
    @DisplayName("DSLTests")
    public List<DynamicContainer> getTests() {
        final var paths = TestDiscoveryHelper.discoverTests();
        if (paths == null) {
            return Collections.emptyList();
        }
        return paths.stream().map(this::createTest).toList();
    }

    private List<DynamicNode> getDynamicContainersFromTestNode(final Collection<? extends TestNode> testNodes) {
        if (testNodes == null) {
            return Collections.emptyList();
        }

        return testNodes.stream().map(testNode -> {
            if (testNode instanceof TestCase testCase) {
                return DynamicTest.dynamicTest(testCase.getIdentifier(), testCase::run);
            } else if (testNode instanceof TestContainer testContainer) {
                return DynamicContainer.dynamicContainer(testContainer.getIdentifier(),
                        getDynamicContainersFromTestNode(testContainer.getTestNodes()));
            }
            throw new SaplTestException("Unknown type of TestNode");
        }).toList();
    }

    @Override
    protected DynamicContainer convertTestContainerToTargetRepresentation(final TestContainer testContainer) {
        return DynamicContainer.dynamicContainer(testContainer.getIdentifier(),
                getDynamicContainersFromTestNode(testContainer.getTestNodes()));
    }
}
