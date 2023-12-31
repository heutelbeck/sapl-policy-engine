/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.springdatamongoreactive.sapl.queries.enforcement;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import io.sapl.springdatamongoreactive.sapl.utils.Utilities;
import lombok.experimental.UtilityClass;

/**
 * This class takes care of linking the parameters from the query annotation to
 * the parameters of the method.
 */
@UtilityClass
public class QueryAnnotationParameterResolver {

    /**
     * In the query annotation of a method, where the query can be found, parameters
     * with the parameters from the method are followed by a question mark and an
     * index.
     * <p>
     * For example: '?0' corresponds to the first parameter of the method.
     * <p>
     * This method links all parameters to each other.
     *
     * @param method is the original repository method.
     * @param args   are the original parameters of the method.
     * @return the new query with the correct values as a string, since the query
     *         from the annotation is also a string.
     */
    public static String resolveBoundedMethodParametersAndAnnotationParameters(Method method, Object[] args) {
        var parameterNameList = new ArrayList<String>();
        var parameters        = Arrays.stream(method.getParameters()).toList();

        var query = method.getAnnotation(org.springframework.data.mongodb.repository.Query.class).value();
        for (Parameter parameter : parameters) {
            parameterNameList.add("?" + parameters.indexOf(parameter));
        }

        var finalArgs          = convertArgumentsOfTypeString(args);
        var parameterNameArray = new String[parameterNameList.size()];

        parameterNameArray = parameterNameList.toArray(parameterNameArray);
        var finalArgsArray = new String[finalArgs.size()];
        finalArgsArray = finalArgs.toArray(finalArgsArray);

        return StringUtils.replaceEach(query, parameterNameArray, finalArgsArray);
    }

    /**
     * If a value within a query corresponds to the type string, then this value
     * must be enclosed in quotation marks, since the query itself is a string.
     *
     * @param args are the original parameters of the method.
     * @return all parameters including the manipulated strings.
     */
    private List<String> convertArgumentsOfTypeString(Object[] args) {
        return Arrays.stream(args).map(arg -> {
            if (Utilities.isString(arg)) {
                return "'" + arg + "'";
            } else {
                return arg.toString();
            }
        }).toList();
    }

}
