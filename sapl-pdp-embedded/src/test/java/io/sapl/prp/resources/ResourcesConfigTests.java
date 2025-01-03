/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.prp.resources;

import java.util.Optional;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import io.sapl.interpreter.InitializationException;
import io.sapl.pdp.config.resources.ResourcesVariablesAndCombinatorSource;
import reactor.core.publisher.SignalType;
import reactor.test.StepVerifier;

class ResourcesConfigTests {

    @Test
    void doTest() throws InitializationException {
        final var configProvider = new ResourcesVariablesAndCombinatorSource("/policies");
        configProvider.getCombiningAlgorithm().log(null, Level.INFO, SignalType.ON_NEXT).blockFirst();
        final var sut = configProvider.getVariables().next();
        StepVerifier.create(sut).expectNextMatches(Optional::isPresent).verifyComplete();
        configProvider.destroy();
    }

}
