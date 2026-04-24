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
package io.sapl.spring.pep.constraints.providers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.spring.constraints.providers.ConstraintResponsibility;
import io.sapl.spring.pep.constraints.ConstraintHandler.Mapper;
import io.sapl.spring.pep.constraints.ConstraintHandlerProvider;
import io.sapl.spring.pep.constraints.ScopedConstraintHandler;
import io.sapl.spring.pep.constraints.SignalType;
import io.sapl.spring.pep.constraints.Signal.SqlShimSignal;
import lombok.val;

/**
 * Translates a {@code sql:queryManipulation} constraint into a {@link Mapper}
 * attached to the PEP's {@link SqlShimSignal}. The mapper textually rewrites
 * the SQL string by injecting the obligation's {@code conditions} array into
 * the {@code WHERE} clause of the original query.
 *
 * The obligation shape is
 *
 * <pre>{@code
 * {
 *   "type": "sql:queryManipulation",
 *   "conditions": ["status = 'active'", "or tenant_id = 7"]
 * }
 * }</pre>
 *
 * Each condition string may begin with {@code "and "} or {@code "or "} to
 * indicate the propositional connective; defaults to {@code AND}. Conditions
 * are prepended before any existing WHERE conditions of the original query.
 *
 * Selection and transformation directives (column whitelisting, function
 * wrapping) require domain-type reflection and are not supported on the SQL
 * shim path. Use the {@code relational:queryManipulation} obligation against
 * {@code RelationalQueryShimSignal} for those features.
 */
public class SqlQueryManipulationProvider implements ConstraintHandlerProvider {

    private static final String CONSTRAINT_TYPE  = "sql:queryManipulation";
    private static final String FIELD_CONDITIONS = "conditions";
    private static final int    DEFAULT_PRIORITY = 30;
    private static final String WHERE_KEYWORD    = " where ";
    private static final String AND_CONNECTIVE   = "and ";
    private static final String OR_CONNECTIVE    = "or ";
    private static final String AND_WITH_PADDING = " AND ";
    private static final String OR_WITH_PADDING  = " OR ";

    @Override
    public Optional<ScopedConstraintHandler> getConstraintHandler(Value constraint, Set<SignalType> supportedSignals) {
        if (!ConstraintResponsibility.isResponsible(constraint, CONSTRAINT_TYPE)) {
            return Optional.empty();
        }
        if (!supportedSignals.contains(SqlShimSignal.TYPE)) {
            return Optional.empty();
        }
        val conditions = extractConditions(constraint);
        if (conditions.isEmpty()) {
            return Optional.empty();
        }
        Mapper<String> mapper = sql -> rewriteWhereClause(sql, conditions);
        return Optional.of(new ScopedConstraintHandler(mapper, SqlShimSignal.TYPE, DEFAULT_PRIORITY));
    }

    private static List<String> extractConditions(Value constraint) {
        if (!(constraint instanceof ObjectValue object)) {
            return List.of();
        }
        if (!(object.get(FIELD_CONDITIONS) instanceof ArrayValue conditionsArray)) {
            return List.of();
        }
        val result = new ArrayList<String>(conditionsArray.size());
        for (val element : conditionsArray) {
            if (element instanceof TextValue(String text)) {
                result.add(text);
            }
        }
        return List.copyOf(result);
    }

    private static String rewriteWhereClause(String sql, List<String> conditions) {
        val whereIndex = sql.toLowerCase().indexOf(WHERE_KEYWORD);
        if (whereIndex < 0) {
            return sql;
        }
        val splitIndex            = whereIndex + WHERE_KEYWORD.length();
        val queryBeforeConditions = sql.substring(0, splitIndex);
        val originalConditions    = sql.substring(splitIndex);
        val builder               = new StringBuilder(queryBeforeConditions);
        for (val condition : conditions) {
            builder.append(stripLeadingConnective(condition)).append(connectiveFor(condition));
        }
        return builder.append(originalConditions).toString();
    }

    private static String stripLeadingConnective(String condition) {
        val trimmed = condition.toLowerCase().trim();
        if (trimmed.startsWith(AND_CONNECTIVE)) {
            return condition.substring(condition.toLowerCase().indexOf(AND_CONNECTIVE) + AND_CONNECTIVE.length());
        }
        if (trimmed.startsWith(OR_CONNECTIVE)) {
            return condition.substring(condition.toLowerCase().indexOf(OR_CONNECTIVE) + OR_CONNECTIVE.length());
        }
        return condition;
    }

    private static String connectiveFor(String condition) {
        return condition.toLowerCase().trim().startsWith(OR_CONNECTIVE) ? OR_WITH_PADDING : AND_WITH_PADDING;
    }
}
