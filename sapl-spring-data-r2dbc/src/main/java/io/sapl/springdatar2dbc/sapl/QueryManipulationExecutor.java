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
package io.sapl.springdatar2dbc.sapl;

import lombok.experimental.UtilityClass;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import reactor.core.publisher.Flux;

import java.util.Map;

@UtilityClass
public class QueryManipulationExecutor {

    public <T> Flux<Map<String, Object>> execute(String query, BeanFactory beanFactory, Class<T> domainType) {
        var r2dbcEntityTemplate = beanFactory.getBean(R2dbcEntityTemplate.class);

        if (query.toLowerCase().contains("where")) {
            return r2dbcEntityTemplate.getDatabaseClient().sql(query).fetch().all();
        } else {
            var tableName           = r2dbcEntityTemplate.getDataAccessStrategy().getTableName(domainType)
                    .getReference();
            var queryWithSelectPart = "SELECT * FROM %s WHERE %s".formatted(tableName, query);

            return r2dbcEntityTemplate.getDatabaseClient().sql(queryWithSelectPart).fetch().all();
        }
    }
}
