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
package io.sapl.spring.data.mongo.proxy;

import org.junit.jupiter.api.Test;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;

import static org.mockito.Mockito.*;

class MongoReactiveRepositoryFactoryCustomizerTests {

    RepositoryFactorySupport                     repositoryFactorySupportMock                  = mock(
            RepositoryFactorySupport.class);
    MongoReactiveRepositoryProxyPostProcessor<?> mongoReactiveRepositoryProxyPostProcessorMock = mock(
            MongoReactiveRepositoryProxyPostProcessor.class);

    @Test
    void when_usingMongoEnforcementPointIsDesired_then_customizeRepositoryFactorySupport() {
        // GIVEN
        final var customizer = new MongoReactiveRepositoryFactoryCustomizer(
                mongoReactiveRepositoryProxyPostProcessorMock);

        // WHEN
        customizer.customize(repositoryFactorySupportMock);

        // THEN
        verify(repositoryFactorySupportMock, times(1))
                .addRepositoryProxyPostProcessor(mongoReactiveRepositoryProxyPostProcessorMock);
    }

}
