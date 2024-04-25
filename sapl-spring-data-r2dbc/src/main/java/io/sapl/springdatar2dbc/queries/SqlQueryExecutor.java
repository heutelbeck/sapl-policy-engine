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
package io.sapl.springdatar2dbc.queries;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;

import lombok.AllArgsConstructor;
import reactor.core.publisher.Flux;

@AllArgsConstructor
public class SqlQueryExecutor {

    private final ObjectProvider<BeanFactory> beanFactoryProvider;

    public <T> Flux<T> executeQuery(String sqlQuery, Class<T> domainType) {
        var r2dbcEntityTemplate = beanFactoryProvider.getObject().getBean(R2dbcEntityTemplate.class);

        return r2dbcEntityTemplate.getDatabaseClient().sql(sqlQuery)
                .map((row, metadata) -> r2dbcEntityTemplate.getConverter().read(domainType, row, metadata)).all();
    }

}
