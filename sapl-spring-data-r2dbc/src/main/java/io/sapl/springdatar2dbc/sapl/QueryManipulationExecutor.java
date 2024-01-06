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
package io.sapl.springdatar2dbc.sapl;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.mapping.Table;

import reactor.core.publisher.Flux;

public class QueryManipulationExecutor {

    private R2dbcEntityTemplateExecutor r2dbcEntityTemplateExecutor;

    public QueryManipulationExecutor(BeanFactory beanFactory) {
        var r2dbcEntityTemplate = beanFactory.getBean(R2dbcEntityTemplate.class);
        r2dbcEntityTemplateExecutor = new R2dbcEntityTemplateExecutor(r2dbcEntityTemplate);
    }

    public <T> Flux<Map<String, Object>> execute(String query, Class<T> domainType) {

        if (query.toLowerCase().contains("where")) {
            return r2dbcEntityTemplateExecutor.executeQuery(query);
        } else {
            String tableName = getTableName(domainType);

            var queryWithSelectPart = "SELECT * FROM %s WHERE %s".formatted(tableName, query);

            return r2dbcEntityTemplateExecutor.executeQuery(queryWithSelectPart);
        }
    }

    private <T> String getTableName(Class<T> domainType) {
        boolean hasTableAnnotation = domainType.isAnnotationPresent(Table.class);

        if (hasTableAnnotation) {
            return domainType.getAnnotation(Table.class).name();
        } else {
            return StringUtils.capitalize(domainType.getSimpleName());
        }
    }
}
