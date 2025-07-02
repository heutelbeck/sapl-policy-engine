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
package io.sapl.springdatamongoreactive.queries;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.CriteriaDefinition;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;

import lombok.experimental.UtilityClass;

/**
 * This class builds a query and is supported by the {@link PartTree} class,
 * among others. The query itself is created with the class {@link Criteria} at
 * the end. With the help of the
 * {@link org.springframework.data.mongodb.core.ReactiveMongoTemplate} it is
 * possible to communicate with the database in different ways.
 */
@UtilityClass
public class SaplPartTreeCriteriaCreator {

    public static CriteriaDefinition create(List<Object> allParametersValueAsObjects, PartTree manipulatedPartTree) {

        return buildCriteria(manipulatedPartTree, allParametersValueAsObjects);
    }

    /**
     * Builds a {@link CriteriaDefinition} from a {@link PartTree} and certain
     * parameters. Actual query building logic. Traverses the {@link PartTree} and
     * invokes callback methods to delegate actual criteria creation and
     * concatenation.
     *
     * @param manipulatedPartTree is the created PartTree of the manipulated method.
     * @param parameters from the original method plus the parameters that could be
     * obtained from the condition from the {@link io.sapl.api.pdp.Decision}.
     * @return a new {@link CriteriaDefinition}.
     */
    private CriteriaDefinition buildCriteria(PartTree manipulatedPartTree, List<Object> parameters) {

        Criteria  base     = null;
        final var iterator = parameters.iterator();
        final var andPart  = new ArrayList<Criteria>();

        for (PartTree.OrPart node : manipulatedPartTree) {

            final var parts = node.iterator();
            andPart.add(buildCriteria(parts.next(), iterator.next()));

            while (parts.hasNext()) {
                andPart.add(buildCriteria(parts.next(), iterator.next()));
            }

            final var criteria = new Criteria().andOperator(andPart.toArray(new Criteria[0]));

            base = base == null ? criteria : base.orOperator(andPart.toArray(new Criteria[0]));
            andPart.clear();
        }

        return base;
    }

    private Criteria buildCriteria(Part part, Object value) {
        final var propertyName = part.getProperty().getSegment();

        switch (part.getType()) {
        case SIMPLE_PROPERTY:
            return Criteria.where(propertyName).is(value);
        case NEGATING_SIMPLE_PROPERTY:
            return Criteria.where(propertyName).ne(value);
        case GREATER_THAN:
            return Criteria.where(propertyName).gt(value);
        case GREATER_THAN_EQUAL:
            return Criteria.where(propertyName).gte(value);
        case LESS_THAN:
            return Criteria.where(propertyName).lt(value);
        case LESS_THAN_EQUAL:
            return Criteria.where(propertyName).lte(value);
        case AFTER:
            return Criteria.where(propertyName).gt(value);
        case BEFORE:
            return Criteria.where(propertyName).lt(value);
        case IS_NULL:
            return Criteria.where(propertyName).is(null);
        case IS_NOT_NULL:
            return Criteria.where(propertyName).ne(null);
        case LIKE:
            return Criteria.where(propertyName).regex((String) value);
        default:
            throw new IllegalArgumentException("Unsupported part type: " + part.getType());
        }
    }
}
