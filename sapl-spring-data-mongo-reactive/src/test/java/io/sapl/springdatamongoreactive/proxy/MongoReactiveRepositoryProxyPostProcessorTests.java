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
package io.sapl.springdatamongoreactive.proxy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.repository.core.RepositoryInformation;

import io.sapl.springdatacommon.services.RepositoryInformationCollectorService;
import io.sapl.springdatamongoreactive.enforcement.MongoReactivePolicyEnforcementPoint;
import io.sapl.springdatamongoreactive.sapl.database.MethodInvocationForTesting;
import io.sapl.springdatamongoreactive.sapl.database.TestUser;
import io.sapl.springdatamongoreactive.sapl.database.UserReactiveCrudRepository;
import io.sapl.springdatamongoreactive.sapl.database.UserReactiveMongoRepository;
import io.sapl.springdatamongoreactive.sapl.database.UserReactiveSortingRepository;

class MongoReactiveRepositoryProxyPostProcessorTests {

    @Mock
    MongoReactivePolicyEnforcementPoint<TestUser> mongoReactivePolicyEnforcementPointMock;
    RepositoryInformationCollectorService         repositoryInformationCollectorServiceMock = mock(
            RepositoryInformationCollectorService.class);
    ProxyFactory                                  factoryMock                               = mock(ProxyFactory.class);
    RepositoryInformation                         repositoryInformationMock                 = mock(
            RepositoryInformation.class);

    @Test
	void when_postProcess_then_addAdviceWithReactiveMongoRepository() {
		// GIVEN
        when(repositoryInformationMock.getRepositoryInterface()).thenAnswer(invocation -> UserReactiveMongoRepository.class);

		var postProcessor = new MongoReactiveRepositoryProxyPostProcessor<TestUser>(mongoReactivePolicyEnforcementPointMock, repositoryInformationCollectorServiceMock);

		// WHEN
		postProcessor.postProcess(factoryMock, repositoryInformationMock);

		// THEN
		verify(factoryMock, times(1)).addAdvice(mongoReactivePolicyEnforcementPointMock);
		verify(repositoryInformationMock, times(1)).getRepositoryInterface();
		verify(repositoryInformationCollectorServiceMock, times(1)).add(any(RepositoryInformation.class));
	}

    @Test
	void when_postProcess_then_addNoAdvice() {
		// GIVEN
        when(repositoryInformationMock.getRepositoryInterface()).thenAnswer(invocation -> MethodInvocationForTesting.class);

		var postProcessor = new MongoReactiveRepositoryProxyPostProcessor<TestUser>(mongoReactivePolicyEnforcementPointMock, repositoryInformationCollectorServiceMock);

		// WHEN
		postProcessor.postProcess(factoryMock, repositoryInformationMock);

		// THEN
		verify(factoryMock, times(0)).addAdvice(mongoReactivePolicyEnforcementPointMock);
		verify(repositoryInformationMock, times(1)).getRepositoryInterface();
		verify(repositoryInformationCollectorServiceMock, times(0)).add(any(RepositoryInformation.class));
	}

    @Test
	void when_postProcess_then_addAdviceWithReactiveCrudRepository() {
		// GIVEN
        when(repositoryInformationMock.getRepositoryInterface()).thenAnswer(invocation -> UserReactiveCrudRepository.class);

		var postProcessor = new MongoReactiveRepositoryProxyPostProcessor<TestUser>(mongoReactivePolicyEnforcementPointMock, repositoryInformationCollectorServiceMock);

		// WHEN
		postProcessor.postProcess(factoryMock, repositoryInformationMock);

		// THEN
		verify(factoryMock, times(1)).addAdvice(mongoReactivePolicyEnforcementPointMock);
		verify(repositoryInformationMock, times(1)).getRepositoryInterface();
		verify(repositoryInformationCollectorServiceMock, times(1)).add(any(RepositoryInformation.class));
	}

    @Test
	void when_postProcess_then_addAdviceWithReactiveSortingRepository() {
		// GIVEN
        when(repositoryInformationMock.getRepositoryInterface()).thenAnswer(invocation -> UserReactiveSortingRepository.class);

		var postProcessor = new MongoReactiveRepositoryProxyPostProcessor<TestUser>(mongoReactivePolicyEnforcementPointMock, repositoryInformationCollectorServiceMock);

		// WHEN
		postProcessor.postProcess(factoryMock, repositoryInformationMock);

		// THEN
		verify(factoryMock, times(1)).addAdvice(mongoReactivePolicyEnforcementPointMock);
		verify(repositoryInformationMock, times(1)).getRepositoryInterface();
		verify(repositoryInformationCollectorServiceMock, times(1)).add(any(RepositoryInformation.class));
	}

}
