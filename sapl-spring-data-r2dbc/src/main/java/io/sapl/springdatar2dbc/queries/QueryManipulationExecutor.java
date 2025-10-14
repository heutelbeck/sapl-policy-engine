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
package io.sapl.springdatar2dbc.queries;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.relational.core.mapping.Table;
import reactor.core.publisher.Flux;

@Slf4j
@AllArgsConstructor
public class QueryManipulationExecutor {

    private final ObjectProvider<SqlQueryExecutor> sqlQueryExecutor;

    private static final String XXXXX     = "XXXXX";
    private static final String QUERY_LOG = "[SAPL QUERY: {} ]";

    public <T> Flux<T> execute(String query, Class<T> domainType) {

        if (query.contains(XXXXX)) {
            final var tableName = getTableName(domainType);

            query = query.replace(XXXXX, tableName);

            log.debug(QUERY_LOG, query);

            return sqlQueryExecutor.getObject().executeQuery(query, domainType);
        }

        log.debug(QUERY_LOG, query);

        return sqlQueryExecutor.getObject().executeQuery(query, domainType);
    }

    private <T> String getTableName(Class<T> domainType) {
        boolean hasTableAnnotation = domainType.isAnnotationPresent(Table.class);

        if (hasTableAnnotation) {
            return domainType.getAnnotation(Table.class).value();
        } else {
            return StringUtils.capitalize(domainType.getSimpleName());
        }
    }
}
