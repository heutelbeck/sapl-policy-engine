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
package io.sapl.springdatamongoreactive.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactoryBean;

import io.sapl.springdatamongoreactive.sapl.database.UserReactiveMongoRepository;

@ExtendWith(MockitoExtension.class)
class MongoReactiveBeanPostProcessorTests {

    @Mock
    ObjectProvider<MongoReactiveRepositoryFactoryCustomizer> mongoReactiveRepositoryFactoryCustomizerProviderMock;

    MongoReactiveRepositoryFactoryCustomizer mongoReactiveRepositoryFactoryCustomizerMock = mock(
            MongoReactiveRepositoryFactoryCustomizer.class);

    @Mock
    ReactiveMongoRepositoryFactoryBean<?, ?, ?> reactiveMongoRepositoryFactoryBeanMock;

    @Test
    void when_postProcessBeforeInitialization_then_addRepositoryFactoryCustomizer() {
        // GIVEN
        final var mongoPostProcessor = new MongoReactiveBeanPostProcessor(
                mongoReactiveRepositoryFactoryCustomizerProviderMock);

        // WHEN
        when(mongoReactiveRepositoryFactoryCustomizerProviderMock.getObject())
                .thenReturn(mongoReactiveRepositoryFactoryCustomizerMock);

        final var result = mongoPostProcessor.postProcessBeforeInitialization(reactiveMongoRepositoryFactoryBeanMock,
                "reactiveMongoRepositoryFactoryBean");

        // THEN
        assertEquals(result, reactiveMongoRepositoryFactoryBeanMock);
        verify(reactiveMongoRepositoryFactoryBeanMock, times(1))
                .addRepositoryFactoryCustomizer(mongoReactiveRepositoryFactoryCustomizerMock);
    }

    @Test
    void when_postProcessBeforeInitialization_then_findNoFittingBean() {
        // GIVEN
        final var mongoPostProcessor        = new MongoReactiveBeanPostProcessor(
                mongoReactiveRepositoryFactoryCustomizerProviderMock);
        final var mongoDbRepositoryTestMock = mock(UserReactiveMongoRepository.class);

        // WHEN
        final var result = mongoPostProcessor.postProcessBeforeInitialization(mongoDbRepositoryTestMock,
                "reactiveMongoRepositoryFactoryBean");

        // THEN
        assertEquals(result, mongoDbRepositoryTestMock);
        verify(reactiveMongoRepositoryFactoryBeanMock, times(0))
                .addRepositoryFactoryCustomizer(mongoReactiveRepositoryFactoryCustomizerMock);
    }
}
