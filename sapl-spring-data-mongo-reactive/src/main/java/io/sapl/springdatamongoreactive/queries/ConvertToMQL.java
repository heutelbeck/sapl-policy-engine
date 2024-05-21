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
package io.sapl.springdatamongoreactive.queries;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Query;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ConvertToMQL {

    public Pageable createPageable(MethodInvocation invocation, BasicQuery query) {
        var arguments           = invocation.getArguments();
        var sortMethodName      = extractSort(query);
        var sortMethodArguments = extractSort(arguments);
        var mergedSort          = sortMethodName.and(sortMethodArguments);
        var pageable            = extractPageable(arguments);

        return processSort(pageable, mergedSort);
    }

    private static Pageable processSort(Pageable pageable, Sort sort) {
        if (pageable != null) {
            if (!sort.isEmpty()) {
                return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort.and(pageable.getSort()));
            } else {
                return pageable;
            }
        } else if (!sort.isEmpty()) {
            return PageRequest.of(0, Integer.MAX_VALUE, sort);
        }
        return Pageable.unpaged();
    }

    private Pageable extractPageable(Object[] arguments) {
        for (Object argument : arguments) {
            if (argument instanceof Pageable pageable) {
                return pageable;
            }
        }
        return null;
    }

    private Sort extractSort(Object[] arguments) {
        for (Object argument : arguments) {
            if (argument instanceof Sort sort) {
                return sort;
            }
        }
        return Sort.unsorted();
    }

    private static Sort extractSort(Query query) {
        var sortDocument = query.getSortObject();

        if (!sortDocument.isEmpty()) {

            var orders = new Sort.Order[sortDocument.size()];
            var i      = 0;
            for (String key : sortDocument.keySet()) {
                var directionValue = sortDocument.getInteger(key);
                var direction      = directionValue == 1 ? Sort.Direction.ASC : Sort.Direction.DESC;
                orders[i++] = new Sort.Order(direction, key);
            }
            return Sort.by(orders);
        }

        return Sort.unsorted();
    }

}
