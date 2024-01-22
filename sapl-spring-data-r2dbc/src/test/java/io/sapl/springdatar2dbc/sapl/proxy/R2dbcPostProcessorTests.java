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
package io.sapl.springdatar2dbc.sapl.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactoryBean;

@SpringBootTest(classes = R2dbcPostProcessor.class)
class R2dbcPostProcessorTests {

    @Autowired
    R2dbcPostProcessor r2dbcPostProcessor;

    @MockBean
    R2dbcCustomizer r2dbcCustomizerMock;

    @Mock
    R2dbcRepositoryFactoryBean<?, ?, ?> r2dbcRepositoryFactoryBeanMock;

    @Test
    void when_R2dbcRepositoryFactoryBeanExists_then_addRepositoryFactoryCustomizer() {
        // GIVEN

        // WHEN
        var result = r2dbcPostProcessor.postProcessBeforeInitialization(r2dbcRepositoryFactoryBeanMock,
                "r2dbcRepositoryFactoryBean");

        // THEN
        assertEquals(result, r2dbcRepositoryFactoryBeanMock);
        verify(r2dbcRepositoryFactoryBeanMock, Mockito.times(1)).addRepositoryFactoryCustomizer(r2dbcCustomizerMock);
    }
}
