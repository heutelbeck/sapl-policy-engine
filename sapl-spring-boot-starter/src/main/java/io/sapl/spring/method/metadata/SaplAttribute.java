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
package io.sapl.spring.method.metadata;

import lombok.NonNull;
import org.springframework.expression.Expression;

/**
 * Holds parsed SpEL expressions from SAPL security annotations.
 * <p>
 * Used internally to cache and pass annotation metadata to the authorization
 * subscription builder.
 *
 * @param annotationType the annotation class (e.g., PreEnforce.class)
 * @param subjectExpression SpEL expression for the subject, or null
 * @param actionExpression SpEL expression for the action, or null
 * @param resourceExpression SpEL expression for the resource, or null
 * @param environmentExpression SpEL expression for the environment, or null
 * @param secretsExpression SpEL expression for secrets, or null
 * @param genericsType the generic type for collection returns
 */
public record SaplAttribute(
        Class<?> annotationType,
        Expression subjectExpression,
        Expression actionExpression,
        Expression resourceExpression,
        Expression environmentExpression,
        Expression secretsExpression,
        Class<?> genericsType) {

    private static final String NO_SECRETS = "NO SECRETS";
    private static final String SECRETS_REDACTED = "SECRETS REDACTED";

    public static final SaplAttribute NULL_ATTRIBUTE = new SaplAttribute(null, null, null, null, null, null, null);

    @Override
    public @NonNull String toString() {
        return "@" + (annotationType() == null ? "null" : annotationType().getSimpleName()) + "(subject="
                + expressionStringOrNull(subjectExpression()) + ", action=" + expressionStringOrNull(actionExpression())
                + ", resource=" + expressionStringOrNull(resourceExpression()) + ", environment="
                + expressionStringOrNull(environmentExpression()) + ", secrets=" + maskSecrets() + ", genericsType="
                + (genericsType() == null ? "null" : genericsType().getName()) + ")";
    }

    private String maskSecrets() {
        return secretsExpression() == null ? NO_SECRETS : SECRETS_REDACTED;
    }

    private String expressionStringOrNull(Expression expression) {
        if (expression == null) {
            return "null";
        }
        return '"' + expression.getExpressionString() + '"';
    }
}
