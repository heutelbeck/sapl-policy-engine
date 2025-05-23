/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactoryBean;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class R2dbcBeanPostProcessor implements BeanPostProcessor {

    private final ObjectProvider<R2dbcRepositoryFactoryCustomizer> r2dbcRepositoryFactoryCustomizerProvider;

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof R2dbcRepositoryFactoryBean<?, ?, ?> r2dbcRepositoryFactoryBean) {
            r2dbcRepositoryFactoryBean
                    .addRepositoryFactoryCustomizer(r2dbcRepositoryFactoryCustomizerProvider.getObject());
            return r2dbcRepositoryFactoryBean;
        }
        return bean;
    }
}
