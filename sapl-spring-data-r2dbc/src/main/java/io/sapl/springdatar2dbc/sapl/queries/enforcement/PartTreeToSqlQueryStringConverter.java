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
package io.sapl.springdatar2dbc.sapl.queries.enforcement;

import static io.sapl.springdatacommon.sapl.utils.Utilities.isString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;

import io.sapl.springdatacommon.sapl.QueryManipulationEnforcementData;
import io.sapl.springdatar2dbc.sapl.OperatorR2dbc;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

/**
 * This class is responsible for translating a PartTree into a Sql-Query.
 */
@UtilityClass
public class PartTreeToSqlQueryStringConverter {

    /**
     * Builds the corresponding Sql-Query with the information of a
     * {@link QueryManipulationEnforcementData} object.
     *
     * @param enforcementData which contains the necessary information.
     * @param <T>             the domain type
     * @return SQL query of a {@link PartTree}.
     */
    public <T> String createSqlBaseQuery(QueryManipulationEnforcementData<T> enforcementData) {
        var methodName       = enforcementData.getMethodInvocation().getMethod().getName();
        var arguments        = enforcementData.getMethodInvocation().getArguments();
        var domainType       = enforcementData.getDomainType();
        var partTree         = new PartTree(methodName, domainType);
        var baseConditions   = new ArrayList<SqlCondition>();
        var argumentIterator = Arrays.stream(arguments).iterator();

        for (PartTree.OrPart node : partTree) {

            var partsIterator = node.iterator();

            var currentOrPart = new ArrayList<SqlCondition>();
            currentOrPart.add(and(partsIterator.next(), argumentIterator.next(), domainType));

            while (partsIterator.hasNext()) {
                currentOrPart.add(and(partsIterator.next(), argumentIterator.next(), domainType));
            }

            baseConditions = baseConditions.isEmpty() ? currentOrPart : or(baseConditions, currentOrPart);
        }

        return toString(baseConditions, partTree.getSort().get());
    }

    /**
     * Converts {@link SqlCondition}s to a Sql-Query.
     *
     * @param conditions built from a {@link PartTree}
     * @return sql query.
     */
    private String toString(List<SqlCondition> conditions, Stream<Sort.Order> sortOrders) {
        var stringBuilder = new StringBuilder();
        var orders        = sortOrders.toList();

        for (int i = 0; i < conditions.size(); i++) {
            if (i == 0) {
                stringBuilder.append(conditions.get(i).getCondition());
            }
            if (i != 0) {
                stringBuilder.append(' ').append(conditions.get(i).getConjunction()).append(' ')
                        .append(conditions.get(i).getCondition());
            }
        }

        if (!orders.isEmpty()) {
            stringBuilder.append(" ORDER BY");
            for (int i = 0; i < orders.size(); i++) {
                if (i == 0) {
                    stringBuilder.append(' ').append(orders.get(i).getProperty()).append(' ')
                            .append(orders.get(i).getDirection());
                } else {
                    stringBuilder.append(", ").append(orders.get(i).getProperty()).append(' ')
                            .append(orders.get(i).getDirection());
                }
            }
        }

        return stringBuilder.toString();
    }

    /**
     * If the {@link PartTree} has {@link PartTree.OrPart}, respectively if the
     * query method has an Or-Conjunction, the corresponding {@link SqlCondition} is
     * adjusted here.
     *
     * @param baseConditions the already built {@link SqlCondition}s.
     * @param currentOrPart  the current SqlConditions, where the last
     *                       {@link SqlCondition} is adjusted.
     * @return the composite {@link SqlCondition}s.
     */
    private ArrayList<SqlCondition> or(ArrayList<SqlCondition> baseConditions, ArrayList<SqlCondition> currentOrPart) {
        var conditionsSize = currentOrPart.size();
        currentOrPart.get(conditionsSize - 1).setConjunction(Conjunction.OR);
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
        List<?> arguments = (List<?>) arg;

        var arrayList = new ArrayList<String>();

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
     * @param part       is the current {@link Part}
     * @param argument   is the corresponding value of the part.
     * @param domainType is the domain type.
     * @return created {@link SqlCondition}.
     */
    @SneakyThrows // NoSuchFieldException
    private <T> SqlCondition and(Part part, Object argument, Class<T> domainType) {
        var operator  = OperatorR2dbc.valueOf(part.getType().name());
        var fieldType = domainType.getDeclaredField(part.getProperty().toDotPath()).getType();

        if (isString(fieldType) && operator.isArray()) {
            argument = createSqlArgumentArray(argument);
        }

        if (!isString(fieldType) && operator.isArray()) {
            argument = replaceSquareBracketsWithRoundBrackets(argument);
        }

        if (isString(fieldType) && !operator.isArray()) {
            argument = toSqlConditionString(argument.toString());
        }

        return new SqlCondition(Conjunction.AND,
                part.getProperty().toDotPath() + " " + operator.getSqlQueryBasedKeywords().get(0) + " " + argument);
    }
}
