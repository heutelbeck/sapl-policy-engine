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
package io.sapl.spring.data.r2dbc.proxy;

import io.sapl.spring.data.r2dbc.database.MethodInvocationForTesting;
import io.sapl.spring.data.r2dbc.database.Person;
import io.sapl.spring.data.r2dbc.database.PersonR2dbcRepository;
import io.sapl.spring.data.r2dbc.database.PersonReactiveCrudRepository;
import io.sapl.spring.data.r2dbc.database.PersonReactiveSortingRepository;
import io.sapl.spring.data.r2dbc.enforcement.R2dbcPolicyEnforcementPoint;
import io.sapl.spring.data.services.RepositoryInformationCollectorService;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.repository.core.RepositoryInformation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class R2dbcRepositoryProxyPostProcessorTests {

    @Mock
    R2dbcPolicyEnforcementPoint<Person>   r2dbcPolicyEnforcementPointMock;
    RepositoryInformationCollectorService repositoryInformationCollectorServiceMock = mock(
            RepositoryInformationCollectorService.class);
    ProxyFactory                          factoryMock                               = mock(ProxyFactory.class);
    RepositoryInformation                 repositoryInformationMock                 = mock(RepositoryInformation.class);

    @Test
    void when_postProcess_then_addAdviceWithR2dbcRepository() {
        // GIVEN
        when(repositoryInformationMock.getRepositoryInterface()).thenAnswer(invocation -> PersonR2dbcRepository.class);

        final var postProcessor = new R2dbcRepositoryProxyPostProcessor<Person>(r2dbcPolicyEnforcementPointMock,
                repositoryInformationCollectorServiceMock);

        // WHEN
        postProcessor.postProcess(factoryMock, repositoryInformationMock);

        // THEN
        verify(factoryMock, times(1)).addAdvice(r2dbcPolicyEnforcementPointMock);
        verify(repositoryInformationMock, times(1)).getRepositoryInterface();
        verify(repositoryInformationCollectorServiceMock, times(1)).add(any(RepositoryInformation.class));
    }

    @Test
    void when_postProcess_then_addNoAdvice() {
        // GIVEN
        when(repositoryInformationMock.getRepositoryInterface())
                .thenAnswer(invocation -> MethodInvocationForTesting.class);

        final var postProcessor = new R2dbcRepositoryProxyPostProcessor<Person>(r2dbcPolicyEnforcementPointMock,
                repositoryInformationCollectorServiceMock);

        // WHEN
        postProcessor.postProcess(factoryMock, repositoryInformationMock);

        // THEN
        verify(factoryMock, times(0)).addAdvice(r2dbcPolicyEnforcementPointMock);
        verify(repositoryInformationMock, times(1)).getRepositoryInterface();
        verify(repositoryInformationCollectorServiceMock, times(0)).add(any(RepositoryInformation.class));
    }

    @Test
    void when_postProcess_then_addAdviceWithReactiveCrudRepository() {
        // GIVEN
        when(repositoryInformationMock.getRepositoryInterface())
                .thenAnswer(invocation -> PersonReactiveCrudRepository.class);

        final var postProcessor = new R2dbcRepositoryProxyPostProcessor<Person>(r2dbcPolicyEnforcementPointMock,
                repositoryInformationCollectorServiceMock);

        // WHEN
        postProcessor.postProcess(factoryMock, repositoryInformationMock);

        // THEN
        verify(factoryMock, times(1)).addAdvice(r2dbcPolicyEnforcementPointMock);
        verify(repositoryInformationMock, times(1)).getRepositoryInterface();
        verify(repositoryInformationCollectorServiceMock, times(1)).add(any(RepositoryInformation.class));
    }

    @Test
    void when_postProcess_then_addAdviceWithReactiveSortingRepository() {
        // GIVEN
        when(repositoryInformationMock.getRepositoryInterface())
                .thenAnswer(invocation -> PersonReactiveSortingRepository.class);

        final var postProcessor = new R2dbcRepositoryProxyPostProcessor<Person>(r2dbcPolicyEnforcementPointMock,
                repositoryInformationCollectorServiceMock);

        // WHEN
        postProcessor.postProcess(factoryMock, repositoryInformationMock);

        // THEN
        verify(factoryMock, times(1)).addAdvice(r2dbcPolicyEnforcementPointMock);
        verify(repositoryInformationMock, times(1)).getRepositoryInterface();
        verify(repositoryInformationCollectorServiceMock, times(1)).add(any(RepositoryInformation.class));
    }

}
