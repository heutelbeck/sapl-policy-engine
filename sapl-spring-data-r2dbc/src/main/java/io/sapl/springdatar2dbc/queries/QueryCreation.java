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
package io.sapl.springdatar2dbc.queries;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.data.domain.Sort;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.springdatacommon.queries.QueryAnnotationParameterResolver;
import lombok.experimental.UtilityClass;

@UtilityClass
public class QueryCreation {

    /**
     * Manipulates the original query by appending the conditions from the decision,
     * if the original query contains the keyword ' where '. Else just append the
     * conditions from the obligation.
     *
     * @param basicQuery is the original value from the
     * {@link org.springframework.data.r2dbc.repository.Query} annotation.
     * @param conditions are the query conditions from the {@link Decision}.
     * @return the manipulated query.
     */
    public static <T> String manipulateQuery(String basicQuery, ArrayNode conditions, ArrayNode selections,
            ArrayNode transformations, String alias, Class<T> domainType) {
        var finalQuery = "";

        if (basicQuery.toLowerCase().contains(" where ")) {
            finalQuery = createConditionPartWithoutWherePart(basicQuery, conditions);
        }

        return QuerySelectionUtils.createSelectionPartForAnnotation(finalQuery, selections, transformations, alias,
                domainType);
    }

    private String createConditionPartWithoutWherePart(String query, ArrayNode conditions) {
        final var indexWithoutWhere = query.toLowerCase().indexOf(" where ");
        final var indexWithWhere    = indexWithoutWhere + 7;

        final var originalConditions    = query.substring(indexWithWhere);
        final var queryBeforeConditions = query.substring(0, indexWithWhere);

        final var queryBuilder = new StringBuilder();

        for (JsonNode condition : conditions) {
            queryBuilder.append(getConditionWithoutPropositionalConnectives(condition.asText()))
                    .append(getPropositionalConnectives(condition.asText()));
        }

        return queryBeforeConditions + queryBuilder.toString() + originalConditions;
    }

    /**
     * Returning propositionalConnectives of the condition.
     *
     * @param condition is the condition of the sql-query.
     * @return the propositionalConnectives.
     */
    private String getPropositionalConnectives(String condition) {
        final var adjustedCondition = condition.toLowerCase().trim();

        if (adjustedCondition.startsWith("or ")) {
            return " OR ";
        }

        return " AND ";
    }

    /**
     * When condition contains any propositionalConnectives, then this method
     * removes the propositionalConnectives from the condition and returns the
     * condition only.
     *
     * @param condition is the condition of the sql-query.
     * @return the manipulated query.
     */
    private String getConditionWithoutPropositionalConnectives(String condition) {
        final var adjustedCondition = condition.toLowerCase().trim();

        if (adjustedCondition.startsWith("and ")) {
            return condition.substring(4);
        }

        if (adjustedCondition.startsWith("or ")) {
            return condition.substring(3);
        }

        return condition;
    }

    public static String createBaselineQuery(MethodInvocation invocation) {
        final var queryWithParameterSolved = QueryAnnotationParameterResolver
                .resolveForRelationalDatabase(invocation.getMethod(), invocation.getArguments());
        final var sortingPart              = ConvertToSQL.prepareAndMergeSortObjects(Sort.unsorted(),
                invocation.getArguments());
        return queryWithParameterSolved + sortingPart;
    }

    /**
     * The method fetches the matching obligation and extracts the condition from
     * it. This condition is appended to the end of the sql query. The base query is
     * converted from the method name.
     *
     * @param conditions are the conditions from the {@link Decision}.
     * @param selection is the selection from the {@link Decision}.
     * @return created sql query.
     */
    public <T> String createSqlQuery(ArrayNode conditions, ArrayNode selection, ArrayNode transformations,
            Class<T> domainType, String baseQuery) {
        final var queryBuilder = new StringBuilder();

        queryBuilder.append(
                QuerySelectionUtils.createSelectionPartForMethodNameQuery(selection, transformations, domainType));

        for (int i = 0; i < conditions.size(); i++) {
            if (i == conditions.size() - 1 && "".equals(baseQuery)) {
                // baseQuery is empty, when repository method is like findAll(), streamAll()
                queryBuilder.append(conditions.get(i).asText());
            } else {
                queryBuilder.append(addMissingConjunction(conditions.get(i).asText()));
            }
        }

        queryBuilder.append(baseQuery);

        return queryBuilder.toString();
    }

    /**
     * If the condition from the obligation does not have a conjunction, an "AND"
     * conjunction is automatically assumed and appended to the base query.
     *
     * @param sqlConditionFromDecision represents the condition
     * @return the condition with conjunction or not
     */
    private String addMissingConjunction(String sqlConditionFromDecision) {
        final var conditionStartsWithPropositionalConnectives = sqlConditionFromDecision.toLowerCase().trim()
                .startsWith("and ") || sqlConditionFromDecision.toLowerCase().trim().startsWith("or ");

        final var propositionalConnective = sqlConditionFromDecision.trim().substring(0, 3);
        final var sqlCondition            = sqlConditionFromDecision.trim().substring(3);

        if (conditionStartsWithPropositionalConnectives) {
            return sqlCondition + " " + propositionalConnective + " ";
        } else {
            return sqlConditionFromDecision + " AND ";
        }
    }

}
