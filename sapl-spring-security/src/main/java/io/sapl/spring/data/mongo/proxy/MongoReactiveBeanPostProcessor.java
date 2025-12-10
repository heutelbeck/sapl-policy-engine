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
package io.sapl.spring.data.mongo.proxy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactoryBean;

/**
 * This service implements the {@link BeanPostProcessor} interface and is thus
 * able to manipulate beans before the application is ready to start via the
 * postProcessBeforeInitialization method.
 */
@Slf4j
@RequiredArgsConstructor
public class MongoReactiveBeanPostProcessor implements BeanPostProcessor {

    private final ObjectProvider<MongoReactiveRepositoryFactoryCustomizer> mongoReactiveRepositoryFactoryCustomizerProvider;

    /**
     * Here the bean is filtered, which belongs to class of
     * {@link ReactiveMongoRepositoryFactoryBean}. Spring creates a
     * {@link ReactiveMongoRepositoryFactoryBean} from a reactive mongo repository,
     * which is intercepted here and serves as a starting point to inject the
     * EnforcementPoint.
     *
     * @param bean the new bean instance.
     * @param beanName the name of the bean.
     * @return the original bean or the manipulated bean, should it be of type
     * {@link ReactiveMongoRepositoryFactoryBean}.
     */
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof ReactiveMongoRepositoryFactoryBean<?, ?, ?> mongoReactiveRepositoryFactoryBean) {
            log.debug("# MongoReactiveBeanPostProcessor postProcessBeforeInitialization {} {}",
                    bean.getClass().getSimpleName(), beanName);
            mongoReactiveRepositoryFactoryBean
                    .addRepositoryFactoryCustomizer(mongoReactiveRepositoryFactoryCustomizerProvider.getObject());
            return mongoReactiveRepositoryFactoryBean;
        }
        return bean;
    }
}
