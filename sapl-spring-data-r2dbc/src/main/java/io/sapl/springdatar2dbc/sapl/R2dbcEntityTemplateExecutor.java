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

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;

import reactor.core.publisher.Flux;

public class R2dbcEntityTemplateExecutor {

    private final R2dbcEntityTemplate r2dbcEntityTemplate;

    public R2dbcEntityTemplateExecutor(R2dbcEntityTemplate r2dbcEntityTemplate) {
        this.r2dbcEntityTemplate = r2dbcEntityTemplate;
    }

    Flux<Map<String, Object>> executeQuery(String sqlQuery) {
        return r2dbcEntityTemplate.getDatabaseClient().sql(sqlQuery).fetch().all();
    }

}
