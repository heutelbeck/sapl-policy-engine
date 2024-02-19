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
package io.sapl.spring.data.r2dbc.proxy;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.support.RepositoryProxyPostProcessor;

import io.sapl.spring.data.r2dbc.enforcement.R2dbcPolicyEnforcementPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class R2dbcRepositoryProxyPostProcessor implements RepositoryProxyPostProcessor {

    private final R2dbcPolicyEnforcementPoint r2dbcPolicyEnforcementPoint;

    @Override
    public void postProcess(ProxyFactory factory, RepositoryInformation repositoryInformation) {
        log.info("# R2dbcRepositoryProxyPostProcessor postProcess {} {}", factory.getClass().getSimpleName(),
                repositoryInformation.getClass().getSimpleName());
        factory.addAdvice(r2dbcPolicyEnforcementPoint);
    }
}
