package io.sapl.springdatar2dbc.sapl.utils;

import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
public class Utilities {
    public final static String STRING_BASED_IMPL_MSG         = "Sapl is implemented using the String-Based Implementation. ";
    public final static String METHOD_BASED_IMPL_MSG         = "Sapl is implemented using the Method-Name-Based Implementation. ";
    public final static String FILTER_BASED_IMPL_MSG         = "Sapl is implemented using the Filter-Based Implementation. ";
    public final static String FILTER_JSON_CONTENT           = "filterJsonContent";
    public final static String FILTER_JSON_CONTENT_PREDICATE = "jsonContentFilterPredicate";
    public final static String R2DBC_QUERY_MANIPULATION      = "r2dbcQueryManipulation";
    public final static String CONDITION                     = "condition";
    public final static String TYPE                          = "type";

    public boolean isMethodNameValid(String methodName) {
        Pattern PREFIX_TEMPLATE = Pattern.compile( //
                "^(find|read|get|query|search|stream)(\\p{Lu}.*?)??By");

        Matcher matcher = PREFIX_TEMPLATE.matcher(methodName);

        return matcher.find();
    }

    public static boolean isFlux(Class<?> clazz) {
        return clazz.equals(Flux.class);
    }

    public static boolean isMono(Class<?> clazz) {
        return clazz.equals(Mono.class);
    }

    public static boolean isListOrCollection(Class<?> clazz) {
        return clazz.equals(List.class) || clazz.equals(Collection.class);
    }

    public static boolean isInteger(Object object) {
        return object.getClass().isAssignableFrom(Integer.class);
    }

    public static boolean isString(Object object) {
        return object.getClass().isAssignableFrom(String.class);
    }

    public static boolean isInteger(Class<?> clazz) {
        return clazz.isAssignableFrom(Integer.class);
    }

    public static boolean isString(Class<?> clazz) {
        return clazz.isAssignableFrom(String.class);
    }

}
