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

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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
import io.sapl.test.grammar.sapltest.ImportType;

public class JUnitTestAdapter extends BaseTestAdapter<DynamicContainer> {

    @TestFactory
    @DisplayName("SAPLTest")
    public List<DynamicContainer> getTests() {
        final var paths = TestDiscoveryHelper.discoverTests();
        if (null == paths) {
            return Collections.emptyList();
        }
        return paths.stream().map(this::createTest).toList();
    }

    private Stream<DynamicNode> getDynamicContainersFromTestNode(final Collection<? extends TestNode> testNodes) {
        if (null == testNodes) {
            return Stream.empty();
        }

        return testNodes.stream().map(testNode -> {
            if (testNode instanceof TestCase testCase) {
                return DynamicTest.dynamicTest(testCase.getIdentifier(), testCase::run);
            } else if (testNode instanceof TestContainer testContainer) {
                // always set false here to avoid overwriting testSourceUri
                return convertTestContainerToTargetRepresentation(testContainer, false);
            }
            throw new SaplTestException("Unknown type of TestNode");
        });
    }

    @Override
    protected DynamicContainer convertTestContainerToTargetRepresentation(final TestContainer testContainer,
            final boolean shouldSetTestSourceUri) {
        final var identifier = testContainer.getIdentifier();

        final var        dynamicNodes = getDynamicContainersFromTestNode(testContainer.getTestNodes());
        DynamicContainer dynamicContainer;
        if (shouldSetTestSourceUri) {
            final var uri = Path.of(TestDiscoveryHelper.RESOURCES_ROOT, identifier).toUri();
            dynamicContainer = DynamicContainer.dynamicContainer(identifier, uri, dynamicNodes);
        } else {
            dynamicContainer = DynamicContainer.dynamicContainer(identifier, dynamicNodes);
        }

        return dynamicContainer;
    }

    @Override
    protected Map<ImportType, Map<String, Object>> getFixtureRegistrations() {
        return Collections.emptyMap();
    }
}
