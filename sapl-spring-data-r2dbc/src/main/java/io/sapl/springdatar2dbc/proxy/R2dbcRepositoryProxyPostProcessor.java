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

import java.lang.reflect.Type;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.support.RepositoryProxyPostProcessor;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.repository.reactive.ReactiveSortingRepository;

import io.sapl.springdatacommon.services.RepositoryInformationCollectorService;
import io.sapl.springdatar2dbc.enforcement.R2dbcPolicyEnforcementPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class R2dbcRepositoryProxyPostProcessor<T> implements RepositoryProxyPostProcessor {

    private final R2dbcPolicyEnforcementPoint<T>        r2dbcPolicyEnforcementPoint;
    private final RepositoryInformationCollectorService repositoryInformationCollectorService;

    @Override
    public void postProcess(ProxyFactory factory, RepositoryInformation repositoryInformation) {
        log.debug("# R2dbcRepositoryProxyPostProcessor postProcess {} {}", factory.getClass().getSimpleName(),
                repositoryInformation.getClass().getSimpleName());

        final var repository = repositoryInformation.getRepositoryInterface();

        if (hasRequiredInterface(repository)) {
            repositoryInformationCollectorService.add(repositoryInformation);
            factory.addAdvice(r2dbcPolicyEnforcementPoint);
        }

    }

    private boolean hasRequiredInterface(Class<?> repository) {
        final var interfaces = repository.getInterfaces();

        for (Type interfaceType : interfaces) {
            if (interfaceType.equals(ReactiveCrudRepository.class) || interfaceType.equals(R2dbcRepository.class)
                    || interfaceType.equals(ReactiveSortingRepository.class)) {
                return true;
            }
        }
        return false;
    }
}
