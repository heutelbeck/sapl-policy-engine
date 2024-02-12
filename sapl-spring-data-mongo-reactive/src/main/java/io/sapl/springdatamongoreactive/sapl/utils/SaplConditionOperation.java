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
package io.sapl.springdatamongoreactive.sapl.utils;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.springdatamongoreactive.sapl.OperatorMongoDB;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * This class translates different kind of objects into {@link SaplCondition} to
 * facilitate further processing.
 */
@UtilityClass
public class SaplConditionOperation {

    private static final List<String> FIND_ALL_KEYWORDS = List.of("findAll", "readAll", "getAll", "queryAll",
            "searchAll", "streamAll");

    /**
     * The entry method of this utility class and is responsible for translating the
     * parameters into SaplCondition objects. The {@link PartTree} class is used to
     * put the method into a basic structure for now. After that, the individual
     * parts of the method can be better edited. To get to the field types of the
     * DomainType, {@link java.lang.reflect} is used.
     *
     * @param args       are the parameters of the original method.
     * @param method     is the original method.
     * @param domainType is the domain type.
     * @return list of {@link SaplCondition} which were created from the parameters
     *         of the method.
     */
    public List<SaplCondition> methodToSaplConditions(Object[] args, Method method, Class<?> domainType) {
        var saplConditions = new ArrayList<SaplCondition>();
        var partTree       = new PartTree(method.getName(), domainType);

        if (partTree.getParts().toList().isEmpty()) {
            return saplConditions;
        }

        var domainTypes       = getDomainFieldOfEveryMethodParameter(partTree);
        var operators         = getOperatorOfEveryMethodParameter(partTree);
        var reflectParameters = method.getParameters();

        if (reflectParameters.length == args.length) {

            for (int i = 0; i < reflectParameters.length; i++) {
                saplConditions.add(new SaplCondition(domainTypes.get(i), args[i], operators.get(i), null));
            }

            return saplConditions;
        } else {
            throw new ArrayIndexOutOfBoundsException();
        }
    }

    /**
     * Here the functionality of {@link BasicQuery} is used to create
     * {@link SaplCondition} objects. It is assumed that the conditions from the
     * {@link io.sapl.api.pdp.Decision} have the correct structured form of a
     * mongodb condition, which is mongodb query language.
     *
     * @param conditions are the conditions of the {@link io.sapl.api.pdp.Decision}
     * @return list of {@link SaplCondition}
     */
    public List<SaplCondition> jsonNodeToSaplConditions(Iterable<JsonNode> conditions) {
        var saplConditions = new ArrayList<SaplCondition>();

        var iterator = conditions.iterator();

        if (!iterator.hasNext()) {
            return saplConditions;
        }

        while (iterator.hasNext()) {

            var value = iterator.next();

            var baseQuery = new BasicQuery(value.asText());

            convertBasicQueryToSaplConditions(baseQuery, saplConditions);

        }

        return saplConditions;
    }

    private void convertBasicQueryToSaplConditions(BasicQuery basicQuery, List<SaplCondition> saplConditions) {
        basicQuery.getQueryObject().forEach((String field, Object val) -> {
            if (val instanceof List<?> list) {
                handleListsOfBasicQuery(saplConditions, list);
            } else {
                convertDocumentToSaplCondition(saplConditions, field, val, "And");
            }
        });
    }

    private void handleListsOfBasicQuery(Collection<SaplCondition> saplConditions, Iterable<?> list) {
        for (Object o : list) {
            ((Document) o).forEach(
                    (String field, Object value) -> convertDocumentToSaplCondition(saplConditions, field, value, "Or"));
        }
    }

    private void convertDocumentToSaplCondition(Collection<SaplCondition> saplConditions, String field, Object value,
            String conjunction) {
        if (value instanceof Document doc) {
            addNewSaplCondition(saplConditions, field, doc, conjunction);
        } else {
            addNewSaplCondition(saplConditions, field, value);
        }
    }

    /**
     * Expands the method name using the {@link SaplCondition}s created from the
     * conditions of the {@link io.sapl.api.pdp.Decision}.
     *
     * @param methodName     is the repository method name.
     * @param saplConditions are created from the conditions of the
     *                       {@link io.sapl.api.pdp.Decision}.
     * @return modified method name.
     */
    public String toModifiedMethodName(String methodName, List<SaplCondition> saplConditions) {
        int     index             = getIndexIfSourceContainsAnyKeyword(methodName);
        boolean findAllMethodType = isMethodOneOfTheFindAllMethods(methodName);

        if (index == -1) {
            methodName = methodName + creatModifyingMethodNamePart(saplConditions, findAllMethodType);
        } else {
            methodName = methodName.substring(0, index)
                    + creatModifyingMethodNamePart(saplConditions, findAllMethodType) + methodName.substring(index);
        }

        return methodName;
    }

    /**
     * Checks if the method name has certain keywords.
     *
     * @param methodName is the repository method name.
     * @return the index at which the keyword occurs.
     */
    private int getIndexIfSourceContainsAnyKeyword(String methodName) {
        return StringUtils.indexOf(methodName, "OrderBy");
    }

    /**
     * Creates the modifying part of the method name, which will later be appended
     * to the original method name.
     *
     * @param saplConditions are created from the conditions of the
     *                       {@link io.sapl.api.pdp.Decision}.
     * @return the modifying method name part.
     */
    private String creatModifyingMethodNamePart(List<SaplCondition> saplConditions, boolean findAllMethodType) {
        var creatModifyingPart = new StringBuilder();

        for (int i = 0; i < saplConditions.size(); i++) {

            if (i == 0 && findAllMethodType) {
                creatModifyingPart.append("By").append(saplConditions.get(i).field().substring(0, 1).toUpperCase())
                        .append(saplConditions.get(i).field().substring(1)).append(saplConditions.get(i).operator()
                                .getMethodNameBasedKeywords().stream().findFirst().orElseThrow());
            } else {
                appendSaplCondition(creatModifyingPart, saplConditions.get(i));
            }
        }

        return creatModifyingPart.toString();
    }

    private void appendSaplCondition(StringBuilder saplConditionsBuilder, SaplCondition saplCondition) {
        saplConditionsBuilder.append(saplCondition.conjunction())
                .append(saplCondition.field().substring(0, 1).toUpperCase()).append(saplCondition.field().substring(1))
                .append(saplCondition.operator().getMethodNameBasedKeywords().stream().findFirst().orElseThrow());
    }

    /**
     * The {@link PartTree} of the method divides this method into {@link Part}s and
     * each Part contains information. One of the information can be derived for the
     * creation of an {@link OperatorMongoDB}. This is extracted here from the
     * individual parts.
     *
     * @param partTree is the PartTree of the method.
     * @return the created list of Operations from the individual parts.
     */
    private List<OperatorMongoDB> getOperatorOfEveryMethodParameter(PartTree partTree) {
        var operators = new ArrayList<OperatorMongoDB>();
        for (Part part : partTree.getParts()) {
            for (int i = 0; i < part.getNumberOfArguments(); i++) {
                if ("SIMPLE_PROPERTY".equals(part.getType().name())) {
                    operators.add(OperatorMongoDB.SIMPLE_PROPERTY);
                } else {
                    operators.add(OperatorMongoDB.getOperatorByKeyword(part.getType().name()));
                }
            }
        }
        return operators;
    }

    /**
     * Extracts the corresponding field of all parameters of the method.
     *
     * @param partTree is the {@link PartTree} of the method.
     * @return the created list of fields for every parameter of the method.
     */
    private List<String> getDomainFieldOfEveryMethodParameter(PartTree partTree) {
        var domainTypes = new ArrayList<String>();
        for (Part part : partTree.getParts()) {
            for (int i = 0; i < part.getNumberOfArguments(); i++) {
                domainTypes.add(part.getProperty().getSegment());
            }
        }
        return domainTypes;
    }

    private void addNewSaplCondition(Collection<SaplCondition> saplConditions, String field, Map<String, Object> doc,
            String conjunction) {
        var operator = OperatorMongoDB.getOperatorByKeyword(doc.keySet().toArray()[0].toString());
        var value    = doc.values().toArray()[0];

        saplConditions.add(new SaplCondition(field, value, operator, conjunction));
    }

    private void addNewSaplCondition(Collection<SaplCondition> saplConditions, String field, Object value) {
        saplConditions.add(new SaplCondition(field, value, OperatorMongoDB.SIMPLE_PROPERTY, "Or"));
    }

    private boolean isMethodOneOfTheFindAllMethods(String methodName) {
        return FIND_ALL_KEYWORDS.stream().anyMatch(keyword -> keyword.equals(methodName));
    }
}
