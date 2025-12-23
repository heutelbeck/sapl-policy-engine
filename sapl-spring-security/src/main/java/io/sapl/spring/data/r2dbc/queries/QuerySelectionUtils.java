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
package io.sapl.spring.data.r2dbc.queries;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@UtilityClass
public class QuerySelectionUtils {

    private static final String DOT               = ".";
    private static final String TYPE              = "type";
    private static final String ASTERISK          = "*";
    private static final String FROM              = " from ";
    private static final String COLUMNS           = "columns";
    private static final String WHITELIST         = "whitelist";
    private static final String SELECT_LOWERCASE  = "select ";
    private static final String SELECT_WITH_SPACE = "SELECT ";
    private static final String FROM_XXXXX_WHERE  = " FROM XXXXX WHERE ";
    private static final String QUERY_LOG         = "Several selection nodes detected. Only one selection node is possible, so the first node found is used: {}";

    public static <T> String createSelectionPartForMethodNameQuery(ArrayNode selections, ArrayNode transformations,
            Class<T> domainType) {

        final var propertiesOfDomain     = propertiesOfDomainToList(domainType);
        final var fieldList              = createSelectionPart(selections, propertiesOfDomain);
        final var fieldListWithFunctions = QuerySelectionUtils.addFunctionsToColumns(fieldList, transformations);

        if (fieldListWithFunctions.isEmpty()) {
            return SELECT_WITH_SPACE + ASTERISK + FROM_XXXXX_WHERE;
        }

        return SELECT_WITH_SPACE + String.join(",", fieldListWithFunctions) + FROM_XXXXX_WHERE;
    }

    public <T> String createSelectionPartForAnnotation(String query, ArrayNode selections, ArrayNode transformations,
            String alias, Class<T> domainType) {
        if (selections.isEmpty() && transformations.isEmpty()) {
            return query;
        }

        final var selectIndex = query.toLowerCase().indexOf(SELECT_LOWERCASE);
        final var fromIndex   = query.toLowerCase().indexOf(FROM);

        final var selectionToReplace = query.substring(selectIndex + SELECT_LOWERCASE.length(), fromIndex);

        final var isAsterix              = ASTERISK.equals(selectionToReplace.trim());
        final var fieldListWithFunctions = new ArrayList<String>();
        final var propertiesOfDomain     = propertiesOfDomainToList(domainType);

        if (isAsterix && selections.isEmpty()) {
            return query;
        }

        if (!selections.isEmpty()) {
            final var fieldList = QuerySelectionUtils.createSelectionPart(selections, propertiesOfDomain);
            fieldListWithFunctions.addAll(addFunctionsToColumns(fieldList, transformations));
        }

        if (!isAsterix && selections.isEmpty()) {

            final var columns   = selectionToReplace.split(",");
            final var fieldList = new ArrayList<String>();

            for (String column : columns) {
                fieldList.add(column.trim());
            }

            fieldListWithFunctions.addAll(addFunctionsToColumns(fieldList, transformations));
        }

        if (!"".equals(alias)) {
            applyAlias(fieldListWithFunctions, alias, propertiesOfDomain);
        }

        final var fieldListAsString = String.join(",", fieldListWithFunctions);

        return query.replace(selectionToReplace, fieldListAsString);
    }

    public static List<String> createSelectionPart(ArrayNode selections, List<String> fieldList) {
        if (selections.isEmpty()) {
            return List.of();
        }

        if (selections.size() > 1) {
            log.info(QUERY_LOG, selections.get(0).toPrettyString());
        }

        final var selection = selections.get(0);

        final var selectionList = new ArrayList<String>();
        final var elements      = selection.get(COLUMNS).elements();

        while (elements.hasNext()) {
            final var element = elements.next();
            selectionList.add(element.asText().trim());
        }

        if (WHITELIST.equals(selection.get(TYPE).asText())) {
            return selectionList;
        } else {

            for (String field : selectionList) {
                fieldList.remove(field);
            }

            return fieldList;
        }
    }

    private static List<String> addFunctionsToColumns(List<String> columns, ArrayNode transformations) {
        if (transformations.isEmpty()) {
            return columns;
        }

        final var transformationsAsPairs = transformationsToPair(transformations);

        for (int i = 0; i < columns.size(); i++) {
            final var possiblePair = findPairByKey(transformationsAsPairs, columns.get(i));

            if (possiblePair != null) {
                final var columnWithFunc = possiblePair.getValue() + "(" + columns.get(i) + ")";
                columns.set(i, columnWithFunc);
            }
        }

        return columns;
    }

    private List<Map.Entry<String, String>> transformationsToPair(Iterable<JsonNode> transformations) {
        final var transformationsAsPairs = new ArrayList<Map.Entry<String, String>>();

        for (JsonNode jsonNode : transformations) {
            final var objectNode = (ObjectNode) jsonNode;
            final var key        = objectNode.fieldNames().next();
            final var value      = objectNode.get(key).asText();

            transformationsAsPairs.add(Map.entry(key, value));
        }

        return transformationsAsPairs;
    }

    private static <T> List<String> propertiesOfDomainToList(Class<T> domainType) {
        final var finalFieldList = new ArrayList<String>();
        final var fields         = Arrays.asList(domainType.getDeclaredFields());

        for (Field field : fields) {
            final var name = field.getName();
            finalFieldList.add(name);
        }

        return finalFieldList;
    }

    public static <K, V> Map.Entry<K, V> findPairByKey(Iterable<Map.Entry<K, V>> pairList, K key) {
        for (Map.Entry<K, V> pair : pairList) {
            if (pair.getKey().equals(key)) {
                return pair;
            }
        }
        return null;
    }

    public static List<String> applyAlias(List<String> columns, String alias, List<String> properties) {
        for (int i = 0; i < columns.size(); i++) {
            final var column = columns.get(i);

            for (var property : properties) {
                if (column.contains(property)) {
                    final var columnWithAlias = alias + DOT + property;
                    final var tempColumn      = column.replaceAll(property, columnWithAlias);
                    columns.set(i, tempColumn);
                }
            }
        }

        return columns;
    }

}
