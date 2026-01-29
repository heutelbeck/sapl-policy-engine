/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.spring.data.mongo.queries;

import tools.jackson.databind.JsonNode;
import io.sapl.spring.data.queries.QueryAnnotationParameterResolver;
import lombok.experimental.UtilityClass;
import org.aopalliance.intercept.MethodInvocation;
import org.bson.Document;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.CriteriaDefinition;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.repository.query.parser.PartTree;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class QueryCreation {

    public static Query manipulateQuery(Iterable<JsonNode> conditions, Iterable<JsonNode> selections,
            BasicQuery annotationQuery, MethodInvocation invocation) {

        final var query = enforceQueryManipulation(annotationQuery, conditions, invocation);

        return addSelectionPart(selections, query);
    }

    private static <T extends Query> T addSelectionPart(Iterable<JsonNode> selections, T query) {
        for (JsonNode selection : selections) {
            query = QuerySelectionUtils.addSelectionPartToQuery(selection, query);
        }
        return query;
    }

    /**
     * f Manipulates the original query by appending the conditions from the
     * decision and calling the database with the manipulated query.
     * <p>
     * Note: In MongoDB, conditions that filter by the same field of the
     * DomainObject are overridden. In our case: If the original repository method
     * filters by name ({'name': 'Lloyd'}) and the sapl condition also contains a
     * filter with the name field, the original filter will be overwritten with the
     * value of the sapl condition.
     *
     * @param annotationQuery is the original value from the
     * {@link org.springframework.data.mongodb.repository.Query} annotation.
     * @param conditions are the query conditions from the {@link Decision}.
     * @return the manipulated query.
     */
    private Query enforceQueryManipulation(BasicQuery annotationQuery, Iterable<JsonNode> conditions,
            MethodInvocation invocation) {
        final var sorting = ConvertToMQL.createPageable(invocation, annotationQuery);

        for (JsonNode condition : conditions) {
            final var conditionAsBasicQuery = new BasicQuery(condition.asString());
            conditionAsBasicQuery.getQueryObject()
                    .forEach((key, value) -> annotationQuery.getQueryObject().append(key, value));
        }

        return annotationQuery.with(sorting);
    }

    public static BasicQuery createBaselineQuery(MethodInvocation invocation) {
        final var basicQuery = createBasicQuery(invocation);
        final var pageable   = ConvertToMQL.createPageable(invocation, basicQuery);
        basicQuery.with(pageable);
        return basicQuery;
    }

    private BasicQuery createBasicQuery(MethodInvocation invocation) {

        final var queryAnnotation = QueryAnnotationParameterResolver.resolveForMongoDB(invocation.getMethod(),
                invocation.getArguments());

        // Use -1 limit to preserve trailing empty strings from split
        final var queryParts       = queryAnnotation.split("XXXXX", -1);
        final var queryPartsEdited = new ArrayList<String>();

        for (String part : queryParts) {

            final var editedPart = part.replace('\'', '\"');

            queryPartsEdited.add(editedPart);
        }

        final var queryDoc  = queryPartsEdited.getFirst();
        final var fieldsDoc = queryPartsEdited.size() > 1 ? queryPartsEdited.get(1) : "";

        final var basicQuery = fieldsDoc.isEmpty() ? new BasicQuery(queryDoc) : new BasicQuery(queryDoc, fieldsDoc);

        if (queryPartsEdited.size() >= 3 && !queryPartsEdited.get(2).isEmpty()) {
            basicQuery.setSortObject(Document.parse(queryPartsEdited.get(2)));
        }

        return basicQuery;
    }

    /**
     * This is the entry method of the class and creates the {@link Query}. There
     * are several steps necessary until a query is created as a result in the end.
     * The parameters of the method must be put into a structured form. Also, the
     * conditions from the {@link io.sapl.api.pdp.Decision} are first packed into a
     * suitable form. The method name can then be adapted and a
     * {@link org.springframework.data.mongodb.core.query.Criteria} can be built
     * from all the information obtained.
     *
     * @param conditions are the query condition from the
     * {@link io.sapl.api.pdp.Decision}
     * @return a manipulated {@link Query}
     */
    public static <T> Query createManipulatedQuery(Iterable<JsonNode> conditions, Iterable<JsonNode> selections,
            String methodName, Class<T> domainType, Object[] args) {

        final var cleanedArguments = removePageableAndSort(args);

        /**
         * Converts the conditions from the corresponding obligation into SaplConditions
         * for further operations.
         */
        final var saplParametersFromObligation = SaplConditionOperation.jsonNodeToSaplConditions(conditions);

        // combine method arguments and obligation arguments
        final var allParametersValueAsObjects = new ArrayList<>(List.of(cleanedArguments));
        for (SaplCondition condition : saplParametersFromObligation) {
            allParametersValueAsObjects.add(condition.value());
        }

        /**
         * Creates a new method name from the old method name and the newly acquired
         * SAPL conditions.
         */
        final var modifiedMethodName = SaplConditionOperation.toModifiedMethodName(methodName,
                saplParametersFromObligation);

        // Create PartTree of new method name
        final var manipulatedPartTree = new PartTree(modifiedMethodName, domainType);

        final var criteria = SaplPartTreeCriteriaCreator.create(allParametersValueAsObjects, manipulatedPartTree);

        final var query = createNewQuery(criteria).with(manipulatedPartTree.getSort());

        return addSelectionPart(selections, query);
    }

    private Query createNewQuery(CriteriaDefinition criteria) {
        return new Query(criteria);
    }

    private Object[] removePageableAndSort(Object[] array) {
        List<Object> filteredList = new ArrayList<>();

        for (Object obj : array) {
            if (!(obj instanceof Pageable) && !(obj instanceof Sort)) {
                filteredList.add(obj);
            }
        }

        return filteredList.toArray(new Object[0]);
    }

}
