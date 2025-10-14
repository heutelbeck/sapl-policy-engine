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
package io.sapl.spring.pdp.embedded;

import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.functions.FunctionLibrarySupplier;
import io.sapl.api.functions.StaticFunctionLibrarySupplier;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;

import java.util.Collection;

@Slf4j
@AutoConfiguration
@RequiredArgsConstructor
@AutoConfigureAfter(FunctionLibrariesAutoConfiguration.class)
public class FunctionContextAutoConfiguration {

    private final Collection<FunctionLibrarySupplier>       functionLibrarySuppliers;
    private final Collection<StaticFunctionLibrarySupplier> staticFunctionLibrarySuppliers;
    private final ConfigurableApplicationContext            applicationContext;

    @Bean
    @ConditionalOnMissingBean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    FunctionContext functionContext() throws InitializationException {
        final var functionContext = new AnnotationFunctionContext();
        for (var supplier : functionLibrarySuppliers) {
            for (var library : supplier.get()) {
                log.trace("loading function library: {}", library.getClass().getSimpleName());
                functionContext.loadLibrary(library);
            }
        }
        for (var supplier : staticFunctionLibrarySuppliers) {
            for (var libraryClass : supplier.get()) {
                log.trace("loading static function library: {}", libraryClass.getSimpleName());
                functionContext.loadLibrary(libraryClass);
            }
        }
        Collection<Object> beanLibraries = applicationContext.getBeansWithAnnotation(FunctionLibrary.class).values();
        for (var library : beanLibraries) {
            log.trace("loading Spring bean function library: {}", library.getClass().getSimpleName());
            functionContext.loadLibrary(library);
        }
        return functionContext;
    }

}
