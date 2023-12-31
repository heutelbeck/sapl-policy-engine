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

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactoryBean;
import org.springframework.stereotype.Service;

/**
 * This service implements the {@link BeanPostProcessor} interface and is thus
 * able to manipulate beans before the application is ready to start via the
 * postProcessBeforeInitialization method.
 */
@Service
public class MongoPostProcessor implements BeanPostProcessor {

    private final MongoCustomizer mongoCustomizer;

    public MongoPostProcessor(MongoCustomizer mongoCustomizer) {
        this.mongoCustomizer = mongoCustomizer;
    }

    /**
     * Here the bean is filtered, which belongs to class of
     * {@link ReactiveMongoRepositoryFactoryBean}. Spring creates a
     * {@link ReactiveMongoRepositoryFactoryBean} from a reactive mongo repository,
     * which is intercepted here and serves as a starting point to inject the
     * EnforcementPoint.
     *
     * @param bean     the new bean instance.
     * @param beanName the name of the bean.
     * @return the original bean or the manipulated bean, should it be of type
     *         {@link ReactiveMongoRepositoryFactoryBean}.
     */
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean.getClass().equals(ReactiveMongoRepositoryFactoryBean.class)) {
            addMongoCustomizer(bean);
        }
        return bean;
    }

    private void addMongoCustomizer(Object bean) {
        ((ReactiveMongoRepositoryFactoryBean<?, ?, ?>) bean).addRepositoryFactoryCustomizer(mongoCustomizer);
    }

}
