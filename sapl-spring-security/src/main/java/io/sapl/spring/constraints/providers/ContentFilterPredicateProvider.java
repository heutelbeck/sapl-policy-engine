/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.util.Objects;
import java.util.function.Predicate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.spring.constraints.api.FilterPredicateConstraintHandlerProvider;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ContentFilterPredicateProvider implements FilterPredicateConstraintHandlerProvider {

    private static final String CONSTRAINT_TYPE = "jsonContentFilterPredicate";
    private static final String TYPE            = "type";

    private final ObjectMapper objectMapper;

    @Override
    public boolean isResponsible(JsonNode constraint) {
        if (constraint == null || !constraint.isObject())
            return false;

        var type = constraint.get(TYPE);

        if (Objects.isNull(type) || !type.isTextual())
            return false;

        return CONSTRAINT_TYPE.equals(type.asText());
    }

    @Override
    public Predicate<Object> getHandler(JsonNode constraint) {
        return ContentFilterUtil.predicateFromConditions(constraint, objectMapper);
    }
}
