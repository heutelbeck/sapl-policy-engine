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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.spring.constraints.providers.ConstraintResponsibility;
import io.sapl.spring.pep.constraints.ConstraintEnforcementException;
import io.sapl.spring.pep.constraints.ConstraintHandler.Mapper;
import io.sapl.spring.pep.constraints.ConstraintHandlerProvider;
import io.sapl.spring.pep.constraints.ScopedConstraintHandler;
import io.sapl.spring.pep.constraints.Signal.SqlShimSignal;
import io.sapl.spring.pep.constraints.SignalType;
import lombok.val;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.update.Update;

/**
 * Translates a {@code sql:queryManipulation} constraint into a {@link Mapper}
 * attached to the PEP's {@link SqlShimSignal}. The mapper rewrites the SQL
 * via JSqlParser AST manipulation, eliminating the precedence and string-
 * literal hazards inherent to regex-based SQL rewriting.
 * </p>
 * The obligation shape is
 * </p>
 *
 * <pre>{@code
 * {
 *   "type": "sql:queryManipulation",
 *   "conditions": ["tenant_id = 7", "status = 'active'"],
 *   "columns": ["id", "name", "email"]
 * }
 * }</pre>
 *
 * Each entry in {@code conditions} is parsed as a boolean SQL expression and
 * AND-combined with the existing WHERE clause. Each addition is wrapped in
 * parentheses so OR-precedence in either the original or the addition cannot
 * leak rows. When the original statement has no WHERE clause, one is added.
 * </p>
 * The {@code columns} entry, when present and the statement is a SELECT,
 * narrows the projection by intersection with the original SELECT list. For
 * {@code SELECT *}, the obligation defines the projection. {@code columns}
 * is silently ignored for UPDATE and DELETE.
 * </p>
 * Failure modes (all fail closed: the mapper throws, the planner sets the
 * obligation's failure state, the PEP raises {@code AccessDeniedException}):
 * <ul>
 * <li>Original SQL fails to parse</li>
 * <li>Obligation condition fails to parse</li>
 * <li>Statement type does not support WHERE injection
 * (e.g. plain {@code INSERT VALUES}, DDL)</li>
 * <li>SELECT is a {@code SetOperationList} (UNION/INTERSECT/EXCEPT) and
 * conditions are present</li>
 * </ul>
 */
public class SqlQueryManipulationProvider implements ConstraintHandlerProvider {

    private static final String CONSTRAINT_TYPE  = "sql:queryManipulation";
    private static final String FIELD_COLUMNS    = "columns";
    private static final String FIELD_CONDITIONS = "conditions";
    private static final int    DEFAULT_PRIORITY = 30;

    private static final String ERROR_PARSE_OBLIGATION_CONDITION = "Cannot parse obligation condition '%s': %s";
    private static final String ERROR_PARSE_SQL                  = "Cannot parse SQL '%s': %s";
    private static final String ERROR_UNSUPPORTED_STATEMENT      = "Statement type %s does not support WHERE injection: %s";

    @Override
    public Optional<ScopedConstraintHandler> getConstraintHandler(Value constraint, Set<SignalType> supportedSignals) {
        if (!ConstraintResponsibility.isResponsible(constraint, CONSTRAINT_TYPE)) {
            return Optional.empty();
        }
        if (!supportedSignals.contains(SqlShimSignal.TYPE)) {
            return Optional.empty();
        }
        val conditions = extractStringArray(constraint, FIELD_CONDITIONS);
        val columns    = extractStringArray(constraint, FIELD_COLUMNS);
        if (conditions.isEmpty() && columns.isEmpty()) {
            return Optional.empty();
        }
        Mapper<String> mapper = sql -> rewrite(sql, conditions, columns);
        return Optional.of(new ScopedConstraintHandler(mapper, SqlShimSignal.TYPE, DEFAULT_PRIORITY));
    }

    private static String rewrite(String sql, List<String> conditions, List<String> columns) {
        val statement = parse(sql);
        val injection = combineConditions(conditions);
        applyConditions(statement, injection, sql);
        applyColumns(statement, columns);
        return statement.toString();
    }

    private static Statement parse(String sql) {
        try {
            return CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException e) {
            throw new ConstraintEnforcementException(ERROR_PARSE_SQL.formatted(sql, e.getMessage()), e);
        }
    }

    private static Expression combineConditions(List<String> conditions) {
        if (conditions.isEmpty()) {
            return null;
        }
        Expression combined = null;
        for (val condition : conditions) {
            val parsed = parseCondition(condition);
            combined = (combined == null) ? parsed
                    : new AndExpression(combined, new ParenthesedExpressionList<>(parsed));
        }
        return combined;
    }

    private static Expression parseCondition(String condition) {
        try {
            return CCJSqlParserUtil.parseCondExpression(condition);
        } catch (JSQLParserException e) {
            throw new ConstraintEnforcementException(
                    ERROR_PARSE_OBLIGATION_CONDITION.formatted(condition, e.getMessage()), e);
        }
    }

    private static void applyConditions(Statement statement, Expression injection, String originalSql) {
        if (injection == null) {
            return;
        }
        val wrapped = new ParenthesedExpressionList<>(injection);
        if (statement instanceof PlainSelect plainSelect) {
            plainSelect.setWhere(combineWhere(plainSelect.getWhere(), wrapped));
            return;
        }
        if (statement instanceof Update update) {
            update.setWhere(combineWhere(update.getWhere(), wrapped));
            return;
        }
        if (statement instanceof Delete delete) {
            delete.setWhere(combineWhere(delete.getWhere(), wrapped));
            return;
        }
        throw new ConstraintEnforcementException(
                ERROR_UNSUPPORTED_STATEMENT.formatted(statement.getClass().getSimpleName(), originalSql));
    }

    private static Expression combineWhere(Expression existing, Expression injection) {
        return (existing == null) ? injection : new AndExpression(new ParenthesedExpressionList<>(existing), injection);
    }

    private static void applyColumns(Statement statement, List<String> columns) {
        if (columns.isEmpty()) {
            return;
        }
        if (!(statement instanceof PlainSelect plainSelect)) {
            return;
        }
        val originalItems = plainSelect.getSelectItems();
        if (isSelectStar(originalItems)) {
            plainSelect.setSelectItems(columns.stream().map(SqlQueryManipulationProvider::columnSelectItem).toList());
            return;
        }
        val obligationSet = new LinkedHashSet<>(columns);
        val intersected   = originalItems.stream().filter(item -> matchesObligationColumn(item, obligationSet))
                .toList();
        plainSelect.setSelectItems(intersected);
    }

    private static boolean isSelectStar(List<SelectItem<?>> items) {
        return items != null && items.size() == 1 && items.getFirst().getExpression() instanceof AllColumns;
    }

    private static boolean matchesObligationColumn(SelectItem<?> item, Set<String> obligationColumns) {
        return item.getExpression() instanceof Column column && obligationColumns.contains(column.getColumnName());
    }

    private static SelectItem<?> columnSelectItem(String columnName) {
        return new SelectItem<>(new Column(columnName));
    }

    private static List<String> extractStringArray(Value constraint, String fieldName) {
        if (!(constraint instanceof ObjectValue object)) {
            return List.of();
        }
        if (!(object.get(fieldName) instanceof ArrayValue array)) {
            return List.of();
        }
        val result = new ArrayList<String>(array.size());
        for (val element : array) {
            if (element instanceof TextValue(String text)) {
                result.add(text);
            }
        }
        return List.copyOf(result);
    }
}
