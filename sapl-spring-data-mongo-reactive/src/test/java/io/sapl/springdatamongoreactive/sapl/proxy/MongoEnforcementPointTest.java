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
package io.sapl.springdatamongoreactive.sapl.proxy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.repository.core.RepositoryInformation;

@SpringBootTest(classes = MongoEnforcementPoint.class)
class MongoEnforcementPointTest {

    @Autowired
    MongoEnforcementPoint mongoEnforcementPoint;

    @MockBean
    MongoProxyInterceptor<?> mongoProxyInterceptorMock;

    @Test
    void when_thereIsProxyFactory_then_postProcessAndAddMongoHandlerAsAdvice() {
        // GIVEN
        var proxyFactoryMock = mock(ProxyFactory.class);

        // WHEN
        mongoEnforcementPoint.postProcess(proxyFactoryMock, any(RepositoryInformation.class));

        // THEN
        verify(proxyFactoryMock, times(1)).addAdvice(mongoProxyInterceptorMock);
    }
}
