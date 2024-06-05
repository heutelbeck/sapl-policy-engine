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

package io.sapl.server.ce.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import io.sapl.server.ce.model.setup.condition.SetupFinishedCondition;

@Configuration
@Conditional(SetupFinishedCondition.class)
public class DataSourceConfiguration {

    @Value("${spring.datasource.url:#{null}}")
    private String dataSourceUrl;

    @Value("${spring.datasource.username:#{null}}")
    private String dataSourceUsername;

    @Value("${spring.datasource.password:#{null}}")
    private String dataSourcePassword;

    @Value("${spring.datasource.driverClassName:#{null}}")
    private String dataSourceDriverClassName;

    @Bean
    DataSource dataSource() {
        DataSourceBuilder<?> dataSourceBuilder = DataSourceBuilder.create();
        dataSourceBuilder.driverClassName(dataSourceDriverClassName);
        dataSourceBuilder.url(dataSourceUrl);
        dataSourceBuilder.username(dataSourceUsername);
        dataSourceBuilder.password(dataSourcePassword);
        return dataSourceBuilder.build();

    }
}
