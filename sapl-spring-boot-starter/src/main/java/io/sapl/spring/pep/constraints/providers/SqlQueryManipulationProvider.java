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
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.NullValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import org.springframework.security.access.AccessDeniedException;
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
 * Translates a {@code sql:queryManipulation} (or alias
 * {@code relational:queryManipulation}) constraint into a {@link Mapper}
 * attached to the PEP's {@link SqlShimSignal}. The mapper rewrites the SQL
 * via JSqlParser AST manipulation, eliminating the precedence and string-
 * literal hazards inherent to regex-based SQL rewriting.
 * <p>
 * The obligation supports two complementary input shapes that may be combined
 * on a single obligation:
 *
 * <pre>{@code
 * {
 *   "type": "sql:queryManipulation",
 *   "criteria": [
 *     {"column": "tenant_id", "op": "=", "value": 7},
 *     {"or": [
 *       {"column": "owner_id", "op": "=", "value": "alice"},
 *       {"column": "public", "op": "=", "value": true}
 *     ]},
 *     {"column": "deleted_at", "op": "isNull"}
 *   ],
 *   "conditions": ["status IN ('active', 'pending')"],
 *   "columns": ["id", "name", "email"]
 * }
 * }</pre>
 *
 * The typed {@code criteria} array uses the same shape as the relational and
 * Mongo providers for cross-backend symmetry. Supported {@code op} values:
 * {@code =}, {@code !=}, {@code >}, {@code >=}, {@code <}, {@code <=},
 * {@code in}, {@code like}, {@code notLike}, {@code isNull},
 * {@code isNotNull}. Typed criteria are rendered to SQL fragments internally
 * and AND-combined with the obligation's string {@code conditions} (and with
 * any existing WHERE clause), each addition wrapped in parentheses so OR-
 * precedence cannot leak rows.
 * <p>
 * The {@code columns} entry, when present and the statement is a SELECT,
 * narrows the projection by intersection with the original SELECT list. For
 * {@code SELECT *}, the obligation defines the projection. {@code columns}
 * is silently ignored for UPDATE and DELETE.
 * <p>
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

    private static final String CONSTRAINT_TYPE_SQL        = "sql:queryManipulation";
    private static final String CONSTRAINT_TYPE_RELATIONAL = "relational:queryManipulation";

    private static final String FIELD_AND        = "and";
    private static final String FIELD_COLUMN     = "column";
    private static final String FIELD_COLUMNS    = "columns";
    private static final String FIELD_CONDITIONS = "conditions";
    private static final String FIELD_CRITERIA   = "criteria";
    private static final String FIELD_OP         = "op";
    private static final String FIELD_OR         = "or";
    private static final String FIELD_VALUE      = "value";
    private static final int    DEFAULT_PRIORITY = 30;

    private static final String ERROR_PARSE_OBLIGATION_CONDITION = "Cannot parse obligation condition '%s': %s";
    private static final String ERROR_PARSE_SQL                  = "Cannot parse SQL '%s': %s";
    private static final String ERROR_UNSUPPORTED_STATEMENT      = "Statement type %s does not support WHERE injection: %s";
    private static final String ERROR_UNSUPPORTED_OPERATOR       = "Unsupported operator in typed criterion: %s";
    private static final String ERROR_VALUE_REQUIRED             = "Value required for operator %s";
    private static final String ERROR_VALUE_KIND_FOR_OPERATOR    = "Value kind %s incompatible with operator %s";

    @Override
    public List<ScopedConstraintHandler> getConstraintHandlers(Value constraint, Set<SignalType> supportedSignals) {
        if (!isResponsible(constraint)) {
            return List.of();
        }
        if (!supportedSignals.contains(SqlShimSignal.SIGNAL_TYPE)) {
            return List.of();
        }
        val typedCriteria = extractTypedCriteriaFragments(constraint);
        val conditions    = extractStringArray(constraint, FIELD_CONDITIONS);
        val columns       = extractStringArray(constraint, FIELD_COLUMNS);
        if (typedCriteria.isEmpty() && conditions.isEmpty() && columns.isEmpty()) {
            return List.of();
        }
        val allConditions = new ArrayList<String>(typedCriteria.size() + conditions.size());
        allConditions.addAll(typedCriteria);
        allConditions.addAll(conditions);
        Mapper<String> mapper = sql -> rewrite(sql, List.copyOf(allConditions), columns);
        return List.of(new ScopedConstraintHandler(mapper, SqlShimSignal.SIGNAL_TYPE, DEFAULT_PRIORITY));
    }

    private static boolean isResponsible(Value constraint) {
        return ConstraintHandlerProvider.constraintIsOfType(constraint, CONSTRAINT_TYPE_SQL)
                || ConstraintHandlerProvider.constraintIsOfType(constraint, CONSTRAINT_TYPE_RELATIONAL);
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
            throw new AccessDeniedException(ERROR_PARSE_SQL.formatted(sql, e.getMessage()), e);
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
            throw new AccessDeniedException(ERROR_PARSE_OBLIGATION_CONDITION.formatted(condition, e.getMessage()), e);
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
        throw new AccessDeniedException(
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

    private static List<String> extractTypedCriteriaFragments(Value constraint) {
        if (!(constraint instanceof ObjectValue object)) {
            return List.of();
        }
        if (!(object.get(FIELD_CRITERIA) instanceof ArrayValue criteriaArray)) {
            return List.of();
        }
        val result = new ArrayList<String>(criteriaArray.size());
        for (val element : criteriaArray) {
            renderCriterionNode(element).ifPresent(result::add);
        }
        return List.copyOf(result);
    }

    private static Optional<String> renderCriterionNode(Value entry) {
        if (!(entry instanceof ObjectValue object)) {
            return Optional.empty();
        }
        if (object.get(FIELD_OR) instanceof ArrayValue orChildren) {
            return renderGroup(orChildren, " OR ");
        }
        if (object.get(FIELD_AND) instanceof ArrayValue andChildren) {
            return renderGroup(andChildren, " AND ");
        }
        return Optional.of(renderLeaf(object));
    }

    private static Optional<String> renderGroup(ArrayValue children, String joiner) {
        val parts = new ArrayList<String>(children.size());
        for (val child : children) {
            renderCriterionNode(child).ifPresent(parts::add);
        }
        if (parts.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("(" + String.join(joiner, parts) + ")");
    }

    private static String renderLeaf(ObjectValue object) {
        if (!(object.get(FIELD_COLUMN) instanceof TextValue(String column))) {
            throw new AccessDeniedException(ERROR_UNSUPPORTED_OPERATOR.formatted("missing 'column'"));
        }
        if (!(object.get(FIELD_OP) instanceof TextValue(String op))) {
            throw new AccessDeniedException(ERROR_UNSUPPORTED_OPERATOR.formatted("missing 'op'"));
        }
        if ("isNull".equals(op)) {
            return column + " IS NULL";
        }
        if ("isNotNull".equals(op)) {
            return column + " IS NOT NULL";
        }
        val valueNode = object.get(FIELD_VALUE);
        if (valueNode == null || valueNode instanceof UndefinedValue) {
            throw new AccessDeniedException(ERROR_VALUE_REQUIRED.formatted(op));
        }
        return renderBinary(column, op, valueNode);
    }

    private static String renderBinary(String column, String op, Value valueNode) {
        return switch (op) {
        case "="       -> column + " = " + renderValue(valueNode, op);
        case "!="      -> column + " <> " + renderValue(valueNode, op);
        case ">"       -> column + " > " + renderValue(valueNode, op);
        case ">="      -> column + " >= " + renderValue(valueNode, op);
        case "<"       -> column + " < " + renderValue(valueNode, op);
        case "<="      -> column + " <= " + renderValue(valueNode, op);
        case "in"      -> column + " IN " + renderArray(valueNode, op);
        case "like"    -> column + " LIKE " + renderText(valueNode, op);
        case "notLike" -> column + " NOT LIKE " + renderText(valueNode, op);
        default        -> throw new AccessDeniedException(ERROR_UNSUPPORTED_OPERATOR.formatted(op));
        };
    }

    private static String renderValue(Value value, String op) {
        return switch (value) {
        case TextValue(String text)              -> "'" + text.replace("'", "''") + "'";
        case NumberValue(java.math.BigDecimal n) -> n.toPlainString();
        case BooleanValue(boolean b)             -> b ? "TRUE" : "FALSE";
        case NullValue ignored                   -> "NULL";
        default                                  -> throw new AccessDeniedException(
                ERROR_VALUE_KIND_FOR_OPERATOR.formatted(value.getClass().getSimpleName(), op));
        };
    }

    private static String renderArray(Value value, String op) {
        if (!(value instanceof ArrayValue array)) {
            throw new AccessDeniedException(
                    ERROR_VALUE_KIND_FOR_OPERATOR.formatted(value.getClass().getSimpleName(), op));
        }
        val parts = new ArrayList<String>(array.size());
        for (val element : array) {
            parts.add(renderValue(element, op));
        }
        return "(" + String.join(", ", parts) + ")";
    }

    private static String renderText(Value value, String op) {
        if (!(value instanceof TextValue(String text))) {
            throw new AccessDeniedException(
                    ERROR_VALUE_KIND_FOR_OPERATOR.formatted(value.getClass().getSimpleName(), op));
        }
        return "'" + text.replace("'", "''") + "'";
    }
}
