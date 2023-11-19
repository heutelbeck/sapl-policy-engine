package io.sapl.springdatar2dbc.sapl.queryTypes.methodNameEnforcement;

import io.sapl.springdatar2dbc.sapl.Operator;
import io.sapl.springdatar2dbc.sapl.QueryManipulationEnforcementData;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static io.sapl.springdatar2dbc.sapl.utils.Utilities.isString;

/**
 * This class is responsible for translating a PartTree into a Sql-Query.
 */
@UtilityClass
public class PartTreeToSqlQueryStringConverter {

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Own solution based on Spring data solution without invoking Spring data
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////// methods
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////// to
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////// create
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////// query
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////// (String)
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////// //
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Builds the corresponding Sql-Query with the information of a
     * {@link QueryManipulationEnforcementData} object.
     *
     * @param enforcementData which contains the necessary information.
     * @param <T>             the domain type
     * @return sql query of a {@link PartTree}.
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

            if (!partsIterator.hasNext()) { // Don't know if this check is necessary, hard to test or to test at all.
                throw new IllegalStateException(String.format("No part found in PartTree %s", partTree));
            }

            var currentOrPart = new ArrayList<SqlCondition>();
            currentOrPart.add(and(partsIterator.next(), argumentIterator.next(), domainType));

            while (partsIterator.hasNext()) {
                currentOrPart.add(and(partsIterator.next(), argumentIterator.next(), domainType));
            }

            baseConditions = baseConditions.size() == 0 ? currentOrPart : or(baseConditions, currentOrPart);
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

        if (orders.size() > 0) {
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
    @SuppressWarnings({ "unchecked", "PDP_POORLY_DEFINED_PARAMETER" })
    private String createSqlArgumentArray(Object arg) {
        var arguments = (List<String>) arg;
        var arrayList = new ArrayList<>();

        for (String argument : arguments) {
            arrayList.add(toSqlConditionString(argument));
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
    @SneakyThrows
    private <T> SqlCondition and(Part part, Object argument, Class<T> domainType) {
        var operator  = Operator.valueOf(part.getType().name());
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
