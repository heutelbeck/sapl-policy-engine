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
package io.sapl.spring.pdp.embedded;

import java.util.List;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.functions.FunctionLibrarySupplier;
import io.sapl.extension.jwt.JWTFunctionLibrary;
import io.sapl.extension.jwt.JWTKeyProvider;
import io.sapl.extension.jwt.JWTPolicyInformationPoint;

@AutoConfiguration
@ConditionalOnClass(name = "io.sapl.extension.jwt.JWTFunctionLibrary")
public class JwtExtensionAutoConfiguration {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    FunctionLibrarySupplier jwtFunctionLibrarySupplier(ObjectMapper mapper) {
        return () -> List.of(new JWTFunctionLibrary(mapper));
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    JWTPolicyInformationPoint jwtPolicyInformationPoint(ObjectMapper mapper, JWTKeyProvider jwtKeyProvider) {
        return new JWTPolicyInformationPoint(jwtKeyProvider);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    JWTKeyProvider jwtKeyProvider(WebClient.Builder builder) {
        return new JWTKeyProvider(builder);
    }

}
