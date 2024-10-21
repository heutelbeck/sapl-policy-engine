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
package io.sapl.spring.config;

import java.util.ArrayList;
import java.util.Arrays;

import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.AdviceModeImportSelector;
import org.springframework.context.annotation.AutoProxyRegistrar;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.lang.NonNull;

import lombok.extern.slf4j.Slf4j;

/**
 * Dynamically determines which imports to include using the
 * {@link EnableSaplMethodSecurity} annotation.
 */
@Slf4j
final class SaplMethodSecuritySelector implements ImportSelector {

    private final ImportSelector autoProxy = new AutoProxyRegistrarSelector();

    @Override
    public String @lombok.NonNull [] selectImports(@NonNull AnnotationMetadata importMetadata) {
        if (!importMetadata.hasAnnotation(EnableSaplMethodSecurity.class.getName())) {
            return new String[0];
        }
        log.debug("Blocking SAPL method security activated.");
        final var imports = new ArrayList<>(Arrays.asList(this.autoProxy.selectImports(importMetadata)));
        imports.add(SaplMethodSecurityConfiguration.class.getName());
        return imports.toArray(new String[0]);
    }

    private static final class AutoProxyRegistrarSelector extends AdviceModeImportSelector<EnableSaplMethodSecurity> {
        // Spring security also support AspectJ, SAPL does (currently) not.
        private static final String[] IMPORTS = new String[] { AutoProxyRegistrar.class.getName() };

        @Override
        protected String[] selectImports(@NonNull AdviceMode adviceMode) {
            if (adviceMode != AdviceMode.PROXY)
                throw new IllegalStateException("SAPL does only support AdviceMode.PROXY. AspectJ is unsupported.");
            return IMPORTS;
        }

    }

}
