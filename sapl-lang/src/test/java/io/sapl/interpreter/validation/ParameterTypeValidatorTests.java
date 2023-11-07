/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.interpreter.validation;

import static io.sapl.interpreter.validation.ParameterTypeValidator.validateType;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.common.collect.Sets;
import com.google.inject.Inject;

import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Array;
import io.sapl.api.validation.Bool;
import io.sapl.api.validation.Int;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Long;
import io.sapl.api.validation.Number;
import io.sapl.api.validation.Text;
import lombok.AllArgsConstructor;
import lombok.Data;

class ParameterTypeValidatorTests {

    private static final Set<Class<?>> VALIDATION_ANNOTATIONS = Set.of(Number.class, Int.class, Long.class, Bool.class,
            Text.class, Array.class, JsonObject.class);

    private static final Set<Class<?>> UNRELATED_ANNOTATIONS = Set.of(Inject.class);

    private static final Set<Class<?>> TEST_ANNOTATIONS = Sets.union(VALIDATION_ANNOTATIONS, UNRELATED_ANNOTATIONS);

    private static final Set<Set<Class<?>>> ANOTATION_POWERSET = Sets.powerSet(TEST_ANNOTATIONS);

    // @formatter:off
	private static final Map<Val, Class<?>[]> TEST_CASES = Map.of(
	//      GIVEN VALUE                         WILL BE VALID WITH THESE ANNOTATIONS
			Val.of(123), 						new Class<?>[] { Number.class, Int.class, Long.class },
			Val.UNDEFINED, 						new Class<?>[] { },
			Val.error(), 						new Class<?>[] { },
			Val.of(Double.MAX_VALUE), 			new Class<?>[] { Number.class },
			Val.of(java.lang.Long.MAX_VALUE),	new Class<?>[] { Number.class, Long.class },
			Val.of(Integer.MAX_VALUE),			new Class<?>[] { Number.class, Long.class, Int.class },
			Val.ofEmptyObject(),				new Class<?>[] { JsonObject.class },
			Val.TRUE, 							new Class<?>[] { Bool.class },
			Val.ofEmptyArray(),					new Class<?>[] { Array.class },
			Val.of(""), 						new Class<?>[] { Text.class });
	// @formatter:on

    static Collection<ValidationTestSpecification> data() {
        var testData = new LinkedList<ValidationTestSpecification>();
        for (var testCase : TEST_CASES.entrySet()) {
            var           givenValue                          = testCase.getKey();
            Set<Class<?>> annotationsImplyingValidityForGiven = Stream.of(testCase.getValue())
                    .collect(Collectors.toCollection(HashSet::new));
            for (var givenAnnotations : ANOTATION_POWERSET) {
                var intersection                      = Sets.intersection(annotationsImplyingValidityForGiven,
                        givenAnnotations);
                var givenWithoutUnrelated             = Sets.difference(givenAnnotations, UNRELATED_ANNOTATIONS);
                var expectedToBeSuccessfullyValidated = givenWithoutUnrelated.isEmpty() || !intersection.isEmpty();
                testData.add(new ValidationTestSpecification(givenValue, givenAnnotations,
                        expectedToBeSuccessfullyValidated));
            }
        }
        return testData;
    }

    @ParameterizedTest
    @MethodSource("data")
    void theGivenValue_YieldsExpectedValidation(ValidationTestSpecification testSpec) {
        var parameter = mockParameter(testSpec.getGivenAnnotations());
        if (testSpec.expectedToBeSuccessfullyValidated)
            assertDoesNotThrow(() -> validateType(testSpec.getGivenValue(), parameter));
        else
            assertThrows(IllegalParameterType.class, () -> validateType(testSpec.getGivenValue(), parameter));
    }

    private static Parameter mockParameter(Set<Class<?>> annotationClasses) {
        var parameter         = mock(Parameter.class);
        var mockedAnnotations = new ArrayList<Annotation>(annotationClasses.size());
        for (var clazz : annotationClasses) {
            mockedAnnotations.add((Annotation) mock(clazz));
        }
        var annotationArray = mockedAnnotations.toArray();
        when(parameter.getAnnotations())
                .thenReturn(Arrays.copyOf(annotationArray, annotationArray.length, Annotation[].class));
        return parameter;
    }

    @Data
    @AllArgsConstructor
    static class ValidationTestSpecification {

        Val givenValue;

        Set<Class<?>> givenAnnotations;

        boolean expectedToBeSuccessfullyValidated;

    }

}
