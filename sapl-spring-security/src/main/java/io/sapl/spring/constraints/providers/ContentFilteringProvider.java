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
package io.sapl.spring.constraints.providers;

import java.util.function.UnaryOperator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.spring.constraints.api.MappingConstraintHandlerProvider;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ContentFilteringProvider implements MappingConstraintHandlerProvider<Object> {

    private static final String CONSTRAINT_TYPE = "filterJsonContent";

    private final ObjectMapper objectMapper;

    @Override
    public boolean isResponsible(JsonNode constraint) {
        return ConstraintResposibility.isResponsible(constraint, CONSTRAINT_TYPE);
    }

    @Override
    public Class<Object> getSupportedType() {
        return Object.class;
    }

    @Override
    public UnaryOperator<Object> getHandler(JsonNode constraint) {
        return ContentFilter.getHandler(constraint, objectMapper);
    }

}
