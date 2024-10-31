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
package io.sapl.springdatar2dbc.queries;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ConvertToSQL {
    private static final String ORDERBY = " ORDER BY ";

    public static String getSorting(String query, Object[] parameters) {
        if (!query.contains(ORDERBY)) {

            for (Object param : parameters) {
                if (param instanceof Sort sort) {
                    return ConvertToSQL.sort(sort);
                }
            }
        }

        return "";
    }

    private static String sort(Sort sort) {
        if (sort.isUnsorted()) {
            return "";
        }

        var sqlSortClause = ORDERBY;

        final var sortOrders = sort.stream()
                .map(order -> order.getProperty() + " " + getOrderDirection(order.getDirection()))
                .collect(Collectors.joining(", "));

        sqlSortClause += sortOrders;

        return sqlSortClause;
    }

    private String getOrderDirection(Sort.Direction direction) {
        return direction.isAscending() ? "ASC" : "DESC";
    }

    public static String conditions(List<SqlCondition> conditions) {
        if (conditions.isEmpty()) {
            return "";
        }

        final var stringBuilder = new StringBuilder(conditions.get(0).getCondition());

        for (int i = 1; i < conditions.size(); i++) {
            stringBuilder.append(' ').append(conditions.get(i).getPropositionalConnectives()).append(' ')
                    .append(conditions.get(i).getCondition());
        }

        return stringBuilder.toString();
    }

    public static String prepareAndMergeSortObjects(Sort sortPartTree, Object[] arguments) {
        final var sqlQuery  = new StringBuilder();
        var       finalSort = sortPartTree;
        Pageable  pageable  = null;

        for (Object argument : arguments) {
            if (argument instanceof Sort sor) {
                finalSort = finalSort == null ? sor : finalSort.and(sor);
            } else if (argument instanceof Pageable pa) {
                pageable = pa;
            }
        }

        if ((finalSort == null || finalSort.isEmpty()) && pageable != null) {
            finalSort = pageable.getSort();
        }

        Consumer<Sort.Order> appendOrder = order -> sqlQuery.append(order.getProperty()).append(' ')
                .append(order.getDirection().name()).append(", ");

        if (finalSort != null && !finalSort.isEmpty()) {
            sqlQuery.append(ORDERBY);
            finalSort.forEach(appendOrder);
            sqlQuery.setLength(sqlQuery.length() - 2);
        }

        if (pageable != null) {
            final var pageNumber = pageable.getPageNumber();
            final var pageSize   = pageable.getPageSize();
            sqlQuery.append(" LIMIT ").append(pageSize).append(" OFFSET ").append(pageNumber);
        }

        return sqlQuery.toString();
    }

}
