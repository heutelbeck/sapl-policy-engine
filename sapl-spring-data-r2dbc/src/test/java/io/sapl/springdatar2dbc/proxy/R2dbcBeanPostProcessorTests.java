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
package io.sapl.springdatar2dbc.proxy;

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
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactoryBean;

import io.sapl.springdatar2dbc.database.PersonR2dbcRepository;

@ExtendWith(MockitoExtension.class)
class R2dbcBeanPostProcessorTests {

    @Mock
    ObjectProvider<R2dbcRepositoryFactoryCustomizer> r2dbcRepositoryFactoryCustomizerProviderMock;

    R2dbcRepositoryFactoryCustomizer r2dbcRepositoryFactoryCustomizerMock = mock(
            R2dbcRepositoryFactoryCustomizer.class);

    @Mock
    R2dbcRepositoryFactoryBean<?, ?, ?> R2dbcRepositoryFactoryBeanMock;

    @Test
    void when_postProcessBeforeInitialization_then_addRepositoryFactoryCustomizer() {
        // GIVEN
        var mongoPostProcessor = new R2dbcBeanPostProcessor(r2dbcRepositoryFactoryCustomizerProviderMock);

        // WHEN
        when(r2dbcRepositoryFactoryCustomizerProviderMock.getObject()).thenReturn(r2dbcRepositoryFactoryCustomizerMock);

        var result = mongoPostProcessor.postProcessBeforeInitialization(R2dbcRepositoryFactoryBeanMock,
                "R2dbcRepositoryFactoryBean");

        // THEN
        assertEquals(result, R2dbcRepositoryFactoryBeanMock);
        verify(R2dbcRepositoryFactoryBeanMock, times(1))
                .addRepositoryFactoryCustomizer(r2dbcRepositoryFactoryCustomizerMock);
    }

    @Test
    void when_postProcessBeforeInitialization_then_findNoFittingBean() {
        // GIVEN
        var mongoPostProcessor        = new R2dbcBeanPostProcessor(r2dbcRepositoryFactoryCustomizerProviderMock);
        var mongoDbRepositoryTestMock = mock(PersonR2dbcRepository.class);

        // WHEN
        var result = mongoPostProcessor.postProcessBeforeInitialization(mongoDbRepositoryTestMock,
                "R2dbcRepositoryFactoryBean");

        // THEN
        assertEquals(result, mongoDbRepositoryTestMock);
        verify(R2dbcRepositoryFactoryBeanMock, times(0))
                .addRepositoryFactoryCustomizer(r2dbcRepositoryFactoryCustomizerMock);
    }
}
