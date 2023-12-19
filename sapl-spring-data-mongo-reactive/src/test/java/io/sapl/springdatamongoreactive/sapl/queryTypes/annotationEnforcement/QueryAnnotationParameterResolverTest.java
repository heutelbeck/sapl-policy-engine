package io.sapl.springdatamongoreactive.sapl.queryTypes.annotationEnforcement;

import io.sapl.springdatamongoreactive.sapl.database.MethodInvocationForTesting;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryAnnotationParameterResolverTest {

    @Test
    void when_resolveBoundedMethodParametersAndAnnotationParametersAreStrings_then_resolveQuery() {
        // GIVEN
        var expectedResult   = "{'firstname':  {'$in': [ 'Aaron' ]}}";
        var methodInvocation = new MethodInvocationForTesting("findAllUsersTest",
                new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Aaron")), null);
        var method           = methodInvocation.getMethod();
        var args             = methodInvocation.getArguments();

        // WHEN
        var result = QueryAnnotationParameterResolver.resolveBoundedMethodParametersAndAnnotationParameters(method,
                args);

        // THEN
        Assertions.assertEquals(expectedResult, result);
    }

    @Test
    void when_resolveBoundedMethodParametersAndAnnotationParametersAreNoStrings_then_resolveQuery() {
        // GIVEN
        var expectedResult   = "{'age':  {'$in': [ 22 ]}}";
        var methodInvocation = new MethodInvocationForTesting("findAllByAge", new ArrayList<>(List.of(int.class)),
                new ArrayList<>(List.of(22)), null);
        var method           = methodInvocation.getMethod();
        var args             = methodInvocation.getArguments();

        // WHEN
        var result = QueryAnnotationParameterResolver.resolveBoundedMethodParametersAndAnnotationParameters(method,
                args);

        // THEN
        Assertions.assertEquals(expectedResult, result);
    }

    @Test
    void when_classIsStaticUtilityClass_then_instantiateThisTestForCoverageReasonsOfConstructor() {
        assertThrows(InvocationTargetException.class, () -> {
            var constructor = QueryAnnotationParameterResolver.class.getDeclaredConstructor();
            assertTrue(Modifier.isPrivate(constructor.getModifiers()));
            ReflectionUtils.makeAccessible(constructor);
            constructor.newInstance();
        });
    }
}
