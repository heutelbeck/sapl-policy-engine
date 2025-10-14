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

import io.sapl.attributes.documentation.api.PolicyInformationPointDocumentationProvider;
import io.sapl.interpreter.functions.FunctionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;

@Slf4j
@AutoConfiguration
@AutoConfigureAfter({ AttributeContextAutoConfiguration.class, FunctionContextAutoConfiguration.class })
public class DocumentationAutoConfiguration {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    PolicyInformationPointsDocumentation pipDocumentation(PolicyInformationPointDocumentationProvider docProvider) {
        log.info("Provisioning PIP Documentation Bean");
        for (var doc : docProvider.getDocumentation()) {
            log.debug("AttributeCtx contains: {}", doc.namespace());
        }
        return new PolicyInformationPointsDocumentation(docProvider.getDocumentation());
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    FunctionLibrariesDocumentation functionDocumentation(FunctionContext functionCtx) {
        log.info("Provisioning Function Libraries Documentation Bean");
        for (var doc : functionCtx.getDocumentation()) {
            log.debug("FunctionCtx contains: {}", doc.getName());
        }
        return new FunctionLibrariesDocumentation(functionCtx.getDocumentation());
    }

}
