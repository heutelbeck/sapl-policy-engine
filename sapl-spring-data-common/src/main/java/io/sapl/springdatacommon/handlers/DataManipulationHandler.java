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
package io.sapl.springdatacommon.handlers;

import static io.sapl.springdatacommon.sapl.utils.ConstraintHandlerUtils.getConstraintHandlerByTypeIfResponsible;
import static io.sapl.springdatacommon.sapl.utils.Utilities.FILTER_JSON_CONTENT;
import static io.sapl.springdatacommon.sapl.utils.Utilities.FILTER_JSON_CONTENT_PREDICATE;

import java.util.function.Function;
import java.util.function.Predicate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.spring.constraints.providers.ContentFilterPredicateProvider;
import io.sapl.spring.constraints.providers.ContentFilteringProvider;
import io.sapl.springdatacommon.sapl.utils.OidObjectMapper;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

/**
 * This class takes care of manipulating the received database objects. In
 * detail, it is about
 *
 * @see ContentFilteringProvider and
 * @see ContentFilterPredicateProvider
 *
 * @param <T> is the type of the domain object.
 */
@RequiredArgsConstructor
public class DataManipulationHandler<T> {
    private final Class<T> domainType;
    private final boolean  isRelationalDatabase;

    private final OidObjectMapper          oidObjectMapper = new OidObjectMapper();
    private final ObjectMapper             objectMapper    = new ObjectMapper();
    private ContentFilteringProvider       contentFilteringProvider;
    private ContentFilterPredicateProvider contentFilterPredicateProvider;

    /**
     * Initiates the manipulation of the database objects. It checks which providers
     * are present in the decision's obligation and applies them to the data
     * accordingly.
     *
     * @param obligations are the obligations of the decision.
     * @return the manipulated database objects.
     */
    public Function<Flux<T>, Flux<T>> manipulate(JsonNode obligations) {
        return data -> {
            if (this.isRelationalDatabase) {
                this.contentFilteringProvider       = new ContentFilteringProvider(objectMapper);
                this.contentFilterPredicateProvider = new ContentFilterPredicateProvider(objectMapper);
            } else {
                this.contentFilteringProvider       = new ContentFilteringProvider(oidObjectMapper);
                this.contentFilterPredicateProvider = new ContentFilterPredicateProvider(oidObjectMapper);
            }

            var filterJsonContentObligation = getConstraintHandlerByTypeIfResponsible(obligations, FILTER_JSON_CONTENT);
            var isContentFilterResponsible  = filterJsonContentObligation != JsonNodeFactory.instance.nullNode();

            var jsonContentFilterPredicateObligation    = getConstraintHandlerByTypeIfResponsible(obligations,
                    FILTER_JSON_CONTENT_PREDICATE);
            var isJsonContentFilterPredicateResponsible = jsonContentFilterPredicateObligation != JsonNodeFactory.instance
                    .nullNode();

            if (isContentFilterResponsible && isJsonContentFilterPredicateResponsible) {
                return data.filter(handleFilter(jsonContentFilterPredicateObligation))
                        .map(handleTransformation(filterJsonContentObligation)).map(toDomainObject());
            }

            if (!isContentFilterResponsible && isJsonContentFilterPredicateResponsible) {
                return data.filter(handleFilter(jsonContentFilterPredicateObligation)).map(toDomainObject());
            }

            if (isContentFilterResponsible) {
                return data.map(handleTransformation(filterJsonContentObligation)).map(toDomainObject());
            }

            return data;
        };
    }

    /**
     * Converts an unknown object to {@link #domainType}
     *
     * @return the converted object.
     */
    public Function<Object, T> toDomainObject() {
        return databaseObject -> oidObjectMapper.convertValue(databaseObject, domainType);
    }

    /**
     * Takes over the transformation of the database objects. For this purpose, the
     * corresponding
     * {@link io.sapl.spring.constraints.providers.ContentFilteringProvider#getHandler(JsonNode)}
     * is called from the Sapl engine.
     *
     * @param contentFilterObligation is the corresponding obligation from the
     *                                decision.
     * @return the corresponding handler.
     */
    protected Function<Object, Object> handleTransformation(JsonNode contentFilterObligation) {
        return contentFilteringProvider.getHandler(contentFilterObligation);
    }

    /**
     * Takes over the filtering of the database objects. For this purpose, the
     * corresponding
     * {@link io.sapl.spring.constraints.providers.ContentFilterPredicateProvider#getHandler(JsonNode)}
     * is called from the Sapl engine.
     *
     * @param contentFilterPredicateObligation is the corresponding obligation from
     *                                         the decision.
     * @return the corresponding handler.
     */
    protected Predicate<Object> handleFilter(JsonNode contentFilterPredicateObligation) {
        return contentFilterPredicateProvider.getHandler(contentFilterPredicateObligation);
    }
}
