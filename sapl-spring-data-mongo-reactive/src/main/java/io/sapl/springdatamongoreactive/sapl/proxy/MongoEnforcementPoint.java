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
package io.sapl.springdatamongoreactive.sapl.proxy;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.support.RepositoryProxyPostProcessor;
import org.springframework.stereotype.Service;

/**
 * This service is used to provide an instance of type
 * {@link RepositoryProxyPostProcessor} to then use the customize method of
 * {@link org.springframework.data.repository.core.support.RepositoryFactoryCustomizer}
 * which injects the EnforcementPoint.
 */
@Service
public class MongoEnforcementPoint implements RepositoryProxyPostProcessor {

    private final MongoProxyInterceptor<?> mongoProxyInterceptor;

    MongoEnforcementPoint(MongoProxyInterceptor<?> mongoProxyInterceptor) {
        this.mongoProxyInterceptor = mongoProxyInterceptor;
    }

    /**
     * This method of the RepositoryProxyPostProcessors interface allows to
     * manipulate a ProxyFactory class via the postProcess method.
     *
     * @param factory               the corresponding {@link ProxyFactory}.
     * @param repositoryInformation the related {@link RepositoryInformation}.
     */
    @Override
    public void postProcess(ProxyFactory factory, RepositoryInformation repositoryInformation) {
        factory.addAdvice(mongoProxyInterceptor);
    }

}
