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

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.repository.core.RepositoryInformation;

@SpringBootTest(classes = R2dbcEnforcementPoint.class)
class R2dbcEnforcementPointTests {

    @Autowired
    R2dbcEnforcementPoint r2dbcEnforcementPoint;

    @MockBean
    R2dbcProxyInterceptor<?> r2DbcProxyInterceptorMock;

    @Mock
    ProxyFactory factoryMock;

    @Mock
    RepositoryInformation repositoryInformationMock;

    @Test
    void when_ProxyR2dbcHandlerIsAddedToProxyFactoryAsAdvice_then_postProcess() {
        // GIVEN

        // WHEN
        r2dbcEnforcementPoint.postProcess(factoryMock, repositoryInformationMock);

        // THEN
        verify(factoryMock, Mockito.times(1)).addAdvice(r2DbcProxyInterceptorMock);

    }
}
