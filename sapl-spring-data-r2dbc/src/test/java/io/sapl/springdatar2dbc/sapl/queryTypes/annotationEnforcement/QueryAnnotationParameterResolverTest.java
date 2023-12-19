package io.sapl.springdatar2dbc.sapl.queryTypes.annotationEnforcement;

import io.sapl.springdatar2dbc.database.MethodInvocationForTesting;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QueryAnnotationParameterResolverTest {

    @Test
    void resolveBoundedMethodParametersAndAnnotationParameters() {
        // GIVEN
        var r2dbcMethodInvocationTest = new MethodInvocationForTesting("findAllUsersTest",
                new ArrayList<>(List.of(int.class, String.class)), new ArrayList<>(List.of(30, "2")), null);
        var expected                  = "SELECT * FROM testUser WHERE age = 30 AND id = '2'";

        // WHEN
        var actual = QueryAnnotationParameterResolver.resolveBoundedMethodParametersAndAnnotationParameters(
                r2dbcMethodInvocationTest.getMethod(), r2dbcMethodInvocationTest.getArguments());

        // THEN
        Assertions.assertEquals(expected, actual);
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
