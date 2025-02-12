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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;

import io.sapl.springdatacommon.utils.Utilities;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * This class is responsible for translating a PartTree into a Sql-Query.
 */
@UtilityClass
public class PartTreeToSqlQueryStringConverter {

    /**
     * Builds the corresponding Sql-Query with the information of a
     * {@link MethodInvocation} object.
     *
     * @param <T> the domain type
     * @return SQL query of a {@link PartTree}.
     */
    public <T> String createSqlBaseQuery(MethodInvocation invocation, Class<T> domainType) {
        final var methodName = invocation.getMethod().getName();

        if (Utilities.isSpringDataDefaultMethod(methodName)) {
            return "";
        }

        final var arguments        = invocation.getArguments();
        final var partTree         = new PartTree(methodName, domainType);
        final var argumentIterator = Arrays.stream(arguments).iterator();
        final var sortPart         = ConvertToSQL.prepareAndMergeSortObjects(partTree.getSort(), arguments);
        var       baseConditions   = new ArrayList<SqlCondition>();

        for (PartTree.OrPart node : partTree) {

            final var partsIterator = node.iterator();

            final var currentOrPart = new ArrayList<SqlCondition>();
            currentOrPart.add(and(partsIterator.next(), argumentIterator.next(), domainType));

            while (partsIterator.hasNext()) {
                currentOrPart.add(and(partsIterator.next(), argumentIterator.next(), domainType));
            }

            baseConditions = baseConditions.isEmpty() ? currentOrPart : or(baseConditions, currentOrPart);
        }

        return toString(baseConditions, sortPart);
    }

    /**
     * Converts {@link SqlCondition}s to a Sql-Query.
     *
     * @param conditions built from a {@link PartTree}
     * @return sql query.
     */
    private String toString(List<SqlCondition> conditions, String sortOrders) {
        final var whereClause = new StringBuilder();

        whereClause.append(ConvertToSQL.conditions(conditions));
        whereClause.append(sortOrders);

        return whereClause.toString();
    }

    /**
     * If the {@link PartTree} has {@link PartTree.OrPart}, respectively if the
     * query method has an Or-Conjunction, the corresponding {@link SqlCondition} is
     * adjusted here.
     *
     * @param baseConditions the already built {@link SqlCondition}s.
     * @param currentOrPart the current SqlConditions, where the last
     * {@link SqlCondition} is adjusted.
     * @return the composite {@link SqlCondition}s.
     */
    private ArrayList<SqlCondition> or(ArrayList<SqlCondition> baseConditions, ArrayList<SqlCondition> currentOrPart) {
        final var conditionsSize = currentOrPart.size();
        currentOrPart.get(conditionsSize - 1).setPropositionalConnectives(PropositionalConnectives.OR);
        baseConditions.addAll(currentOrPart);
        return baseConditions;
    }

    /**
     * In an SQL query, strings are enclosed in quotation marks.
     *
     * @param value is the string which is enclosed.
     * @return the enclosed value.
     */
    private String toSqlConditionString(String value) {
        return "'" + value + "'";
    }

    /**
     * Accepts an object which is supposed to be a list of strings. The values are
     * enclosed in quotation marks and round brackets, since lists are specified
     * this way within sql queries.
     *
     * @param arg which is supposed to be a list of strings.
     * @return the transformed list as string.
     */
    private String createSqlArgumentArray(Object arg) {
        if (!(arg instanceof List<?> arguments)) {
            throw new IllegalStateException("Operator requires array of arguments.");
        }

        final var arrayList = new ArrayList<String>();

        for (Object argument : arguments) {
            if (argument instanceof String stringArgument) {
                arrayList.add(toSqlConditionString(stringArgument));
            }
        }

        return replaceSquareBracketsWithRoundBrackets(arrayList);
    }

    /**
     * Accepts an object which is supposed to be a list. The list is converted to a
     * string and the square brackets are replaced with round brackets.
     *
     * @param arrayList which is supposed to be a list.
     * @return the transformed list as string.
     */
    private String replaceSquareBracketsWithRoundBrackets(Object arrayList) {
        return arrayList.toString().replace(']', ')').replace('[', '(');
    }

    /**
     * Builds a {@link SqlCondition} from the available parameters.
     *
     * @param part is the current {@link Part}
     * @param argument is the corresponding value of the part.
     * @param domainType is the domain type.
     * @return created {@link SqlCondition}.
     */
    @SneakyThrows // NoSuchFieldException, NullPointerException
    private <T> SqlCondition and(Part part, Object argument, Class<T> domainType) {
        if (argument == null) {
            throw new NullPointerException("The appropriate argument is missing for this part of the method. ");
        }

        final var operator  = OperatorR2dbc.valueOf(part.getType().name());
        final var fieldType = domainType.getDeclaredField(part.getProperty().toDotPath()).getType();

        if (fieldType.isAssignableFrom(String.class) && operator.isArray()) {
            argument = createSqlArgumentArray(argument);
        }

        if (!fieldType.isAssignableFrom(String.class) && operator.isArray()) {
            argument = replaceSquareBracketsWithRoundBrackets(argument);
        }

        if (fieldType.isAssignableFrom(String.class) && !operator.isArray()) {
            argument = toSqlConditionString(argument.toString());
        }

        return new SqlCondition(PropositionalConnectives.AND,
                part.getProperty().toDotPath() + " " + operator.getSqlQueryBasedKeywords().get(0) + " " + argument);
    }
}
