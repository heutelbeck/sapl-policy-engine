package io.sapl.springdatar2dbc.sapl.queryTypes.annotationEnforcement;

import io.sapl.springdatar2dbc.sapl.utils.Utilities;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.r2dbc.repository.Query;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

        var query = method.getAnnotation(Query.class).value();
        for (Parameter parameter : parameters) {
            parameterNameList.add("(:" + parameter.getName() + ")");
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
