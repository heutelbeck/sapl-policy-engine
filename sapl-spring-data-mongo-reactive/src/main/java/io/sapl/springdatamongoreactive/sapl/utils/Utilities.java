package io.sapl.springdatamongoreactive.sapl.utils;

import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This utility class collects all possible methods that test whether an object
 * or a class resembles another class.
 */
@UtilityClass
public class Utilities {
    public final static String STRING_BASED_IMPL_MSG         = "Sapl was injected via query annotation. ";
    public final static String METHOD_BASED_IMPL_MSG         = "Sapl was injected via the derivation of the method name. ";
    public final static String FILTER_BASED_IMPL_MSG         = "Sapl was injected by filtering the returning data stream from the database. ";
    public final static String FILTER_JSON_CONTENT           = "filterJsonContent";
    public final static String FILTER_JSON_CONTENT_PREDICATE = "jsonContentFilterPredicate";
    public final static String MONGO_QUERY_MANIPULATION      = "mongoQueryManipulation";
    public final static String CONDITIONS                    = "conditions";
    public final static String TYPE                          = "type";

    /**
     * Checks whether a method name is suitable for deriving a query.
     *
     * @param methodName the method name to be checked.
     * @return true, if a method is suitable for deriving a query.
     */
    public static boolean isMethodNameValid(String methodName) {
        var PREFIX_TEMPLATE = Pattern.compile("^(find|read|get|query|search|stream)(\\p{Lu}.*?)??By");

        var matcher = PREFIX_TEMPLATE.matcher(methodName);

        return matcher.find();
    }

    public static boolean isFlux(Class<?> clazz) {
        return clazz.isAssignableFrom(Flux.class);
    }

    public static boolean isMono(Class<?> clazz) {
        return clazz.equals(Mono.class);
    }

    public static boolean isListOrCollection(Class<?> clazz) {
        return clazz.equals(List.class) || clazz.equals(Collection.class);
    }

    public static boolean isString(Object object) {
        return object.getClass().isAssignableFrom(String.class);
    }
}
