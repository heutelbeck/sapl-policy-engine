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

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;

@SpringBootTest(classes = R2dbcCustomizer.class)
class R2dbcCustomizerTests {

    @Autowired
    R2dbcCustomizer r2dbcCustomizer;

    @MockBean
    RepositoryFactorySupport repositoryFactorySupportMock;

    @MockBean
    R2dbcEnforcementPoint r2dbcEnforcementPointMock;

    @Test
    void when_usingMongoEnforcementPointIsDesired_then_customizeRepositoryFactorySupport() {
        // GIVEN

        // WHEN
        r2dbcCustomizer.customize(repositoryFactorySupportMock);

        // THEN
        verify(repositoryFactorySupportMock, times(1)).addRepositoryProxyPostProcessor(r2dbcEnforcementPointMock);
    }
}
