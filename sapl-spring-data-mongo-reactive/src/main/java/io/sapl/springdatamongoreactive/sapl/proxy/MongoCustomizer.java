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

import org.springframework.data.repository.core.support.RepositoryFactoryCustomizer;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.data.repository.core.support.RepositoryProxyPostProcessor;
import org.springframework.stereotype.Service;

/**
 * This service adds the EnforcementPoint to the corresponding
 * {@link RepositoryFactorySupport} as a {@link RepositoryProxyPostProcessor},
 */
@Service
public class MongoCustomizer implements RepositoryFactoryCustomizer {

    private final MongoEnforcementPoint mongoEnforcementPoint;

    public MongoCustomizer(MongoEnforcementPoint mongoEnforcementPoint) {
        this.mongoEnforcementPoint = mongoEnforcementPoint;
    }

    /**
     * This method allows access to the {@link RepositoryFactorySupport} class in
     * order to inject the EnforcementPoint.
     *
     * @param repositoryFactory is the {@link RepositoryFactorySupport} to be
     *                          customized.
     */
    @Override
    public void customize(RepositoryFactorySupport repositoryFactory) {
        repositoryFactory.addRepositoryProxyPostProcessor(mongoEnforcementPoint);
    }
}
