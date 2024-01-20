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
package io.sapl.spring.method.metadata;

import org.springframework.expression.Expression;

public record SaplAttribute(Class<?> annotationType, Expression subjectExpression, Expression actionExpression,
        Expression resourceExpression, Expression environmentExpression, Class<?> genericsType) {

    public static final SaplAttribute NULL_ATTRIBUTE = new SaplAttribute(null, null, null, null, null, null);

    @Override
    public String toString() {
        return "@" + (annotationType() == null ? "null" : annotationType().getSimpleName()) + "(subject="
                + expressionStringOrNull(subjectExpression()) + ", action=" + expressionStringOrNull(actionExpression())
                + ", resource=" + expressionStringOrNull(resourceExpression()) + ", environment="
                + expressionStringOrNull(environmentExpression()) + ", genericsType="
                + (genericsType() == null ? "null" : genericsType().getName()) + ")";
    }

    private String expressionStringOrNull(Expression expression) {
        if (expression == null) {
            return "null";
        }
        return '"' + expression.getExpressionString() + '"';
    }
}
