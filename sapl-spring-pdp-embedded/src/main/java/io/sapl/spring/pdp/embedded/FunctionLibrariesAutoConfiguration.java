/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.spring.pdp.embedded;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;

import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.functions.LoggingFunctionLibrary;
import io.sapl.functions.StandardFunctionLibrary;
import io.sapl.functions.TemporalFunctionLibrary;

/**
 * This configuration deploys the default function libraries for the PDP.
 */
@AutoConfiguration
public class FunctionLibrariesAutoConfiguration {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    FilterFunctionLibrary filterFunctionLibrary() {
        return new FilterFunctionLibrary();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    StandardFunctionLibrary standardFunctionLibrary() {
        return new StandardFunctionLibrary();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    TemporalFunctionLibrary temporalFunctionLibrary() {
        return new TemporalFunctionLibrary();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    LoggingFunctionLibrary loggingFunctionLibrary() {
        return new LoggingFunctionLibrary();
    }

}
