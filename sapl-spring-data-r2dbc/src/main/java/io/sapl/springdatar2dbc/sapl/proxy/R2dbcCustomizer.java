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

import org.springframework.data.repository.core.support.RepositoryFactoryCustomizer;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.stereotype.Service;

@Service
public class R2dbcCustomizer implements RepositoryFactoryCustomizer {

    private final R2dbcEnforcementPoint r2dbcEnforcementPoint;

    public R2dbcCustomizer(R2dbcEnforcementPoint r2dbcEnforcementPoint) {
        this.r2dbcEnforcementPoint = r2dbcEnforcementPoint;
    }

    @Override
    public void customize(RepositoryFactorySupport repositoryFactory) {
        repositoryFactory.addRepositoryProxyPostProcessor(r2dbcEnforcementPoint);
    }
}
