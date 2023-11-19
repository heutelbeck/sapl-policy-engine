package io.sapl.springdatamongoreactive.sapl.queryTypes.methodNameEnforcement;

import io.sapl.springdatamongoreactive.sapl.database.MethodInvocationForTesting;
import io.sapl.springdatamongoreactive.sapl.database.TestUser;
import io.sapl.springdatamongoreactive.sapl.Operator;
import io.sapl.springdatamongoreactive.sapl.utils.SaplCondition;
import io.sapl.springdatamongoreactive.sapl.utils.SaplConditionOperation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.ReflectionUtils;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SaplConditionOperationTest {

    @Test
    void when_methodIsQueryMethodButNoPartsCanBeCreatedByMethodName_then_returnEmptySaplConditionList() {
        // GIVEN
        var methodInvocation = new MethodInvocationForTesting("findAllBy", new ArrayList<>(List.of()),
                new ArrayList<>(List.of()), null);
        var method           = methodInvocation.getMethod();
        var args             = methodInvocation.getArguments();

        var expectedResult = new ArrayList<>();

        // WHEN
        var actualResult = SaplConditionOperation.methodToSaplConditions(args, method, TestUser.class);

        // THEN
        assertEquals(expectedResult, actualResult);
    }

    @Test
    void when_methodIsQueryMethodButParametersDontFit_then_throwArrayIndexOutOfBoundsException() {
        // GIVEN
        var methodInvocation = new MethodInvocationForTesting("findAllByFirstnameAndAgeBefore",
                new ArrayList<>(List.of(String.class, int.class)), new ArrayList<>(List.of("Aaron")), null);
        var method           = methodInvocation.getMethod();
        var args             = methodInvocation.getArguments();

        // WHEN

        // THEN
        Assertions.assertThrows(ArrayIndexOutOfBoundsException.class,
                () -> SaplConditionOperation.methodToSaplConditions(args, method, TestUser.class));
    }

    @Test
    void when_methodIsQueryMethod_then_convertToSaplConditions() {
        // GIVEN
        var methodInvocation = new MethodInvocationForTesting("findAllByFirstnameAndAgeBefore",
                new ArrayList<>(List.of(String.class, int.class)), new ArrayList<>(List.of("Aaron", 22)), null);
        var method           = methodInvocation.getMethod();
        var args             = methodInvocation.getArguments();
        var expectedResult   = List.of(new SaplCondition("firstname", "Aaron", Operator.SIMPLE_PROPERTY, "And"),
                new SaplCondition("age", 22, Operator.BEFORE, "And"));

        // WHEN
        var actualResult = SaplConditionOperation.methodToSaplConditions(args, method, TestUser.class);

        // THEN
        for (int i = 0; i < actualResult.size(); i++) {
            assertTwoSaplConditions(expectedResult.get(i), actualResult.get(i));
        }
    }

    @Test
    void when_classIsStaticUtilityClass_then_instantiateThisTestForCoverageReasonsOfConstructor() {
        assertThrows(InvocationTargetException.class, () -> {
            var constructor = SaplConditionOperation.class.getDeclaredConstructor();
            assertTrue(Modifier.isPrivate(constructor.getModifiers()));
            ReflectionUtils.makeAccessible(constructor);
            constructor.newInstance();
        });
    }

    private void assertTwoSaplConditions(SaplCondition first, SaplCondition second) {
        assertEquals(first.field(), second.field());
        assertEquals(first.value(), second.value());
        assertEquals(first.operator(), second.operator());
        assertEquals(first.conjunction(), second.conjunction());
    }
}
