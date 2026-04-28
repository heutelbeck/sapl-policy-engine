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
package io.sapl.spring.method.metadata;

import org.junit.jupiter.api.Test;
import org.springframework.security.util.MethodInvocationUtils;

import static org.assertj.core.api.Assertions.assertThat;

class SaplAttributeRegistryTests {

    @Test
    void whenInspectedIsObject_ThenReturnsEmptyCollection() {
        final var sut = new SaplAttributeRegistry();
        final var mi  = MethodInvocationUtils.createFromClass(Object.class, "toString");
        assertThat(sut.getAllSaplAttributes(mi)).isEmpty();
    }

    @Test
    void whenInspectedHasNotAnnotationsAnywhere_ThenReturnsEmptyCollection() {

        class NoAnnotations {
            @SuppressWarnings("unused") // test dummy
            public void doSomething() {
                // NOOP test dummy
            }
        }

        final var sut = new SaplAttributeRegistry();
        final var mi  = MethodInvocationUtils.createFromClass(NoAnnotations.class, "doSomething");
        assertThat(sut.getAllSaplAttributes(mi)).isEmpty();
    }

    @Test
    void whenAnnotationOnClassOnly_ThenReturnsAnnotationFromClass() {

        @PreEnforce(subject = "'onClass'")
        class TestClass {
            @SuppressWarnings("unused") // test dummy
            public void doSomething() {
                // NOOP test dummy
            }
        }

        expectSubjectExpressionStringInAttribute(TestClass.class, "'onClass'");
    }

    @Test
    void whenAnnotationOnMethodOnly_ThenReturnsThat() {

        class TestClass {
            @PreEnforce(subject = "'onMethod'")
            public void doSomething() {
                // NOOP test dummy
            }
        }

        expectSubjectExpressionStringInAttribute(TestClass.class, "'onMethod'");
    }

    @Test
    void whenAnnotationOnMethodAndClass_ThenReturnsOnMethod() {

        @PreEnforce(subject = "'onClass'")
        class TestClass {
            @PreEnforce(subject = "'onMethod'")
            public void doSomething() {
                // NOOP test dummy
            }
        }

        expectSubjectExpressionStringInAttribute(TestClass.class, "'onMethod'");
    }

    interface TestInterfaceAnnotatedOnMethod {
        @PreEnforce(subject = "'onInterfaceMethod'")
        void doSomething();
    }

    @Test
    void whenAnnotationOnlyOnInterfaceMethod_ThenReturnsThat() {

        class TestClass implements TestInterfaceAnnotatedOnMethod {
            public void doSomething() {
                // NOOP test dummy
            }
        }
        expectSubjectExpressionStringInAttribute(TestClass.class, "'onInterfaceMethod'");
    }

    interface TestGenericInterface<E, I> {
        void doSomethingGeneric();
    }

    interface TestDomainInterface {
        @PreEnforce(subject = "'onDomainInterfaceMethod'")
        void doSomething();
    }

    interface CombinedInterface extends TestGenericInterface<Object, Long>, TestDomainInterface {

    }

    @Test
    void whenAnnotationOnlyComplexInterfaceHierarchy_ThenReturnsThat() {
        class Implementation implements CombinedInterface {

            @Override
            public void doSomethingGeneric() {
                // NOOP test dummy
            }

            @Override
            public void doSomething() {
                // NOOP test dummy
            }
        }

        expectSubjectExpressionStringInAttribute(Implementation.class, "'onDomainInterfaceMethod'");
    }

    @PreEnforce(subject = "'onInterface'")
    interface TestInterfaceAnnotatedOnInterface {
        void doSomething();
    }

    @Test
    void whenAnnotationOnlyOnInterface_ThenReturnsThat() {

        class TestClass implements TestInterfaceAnnotatedOnInterface {
            public void doSomething() {
                // NOOP test dummy
            }
        }

        expectSubjectExpressionStringInAttribute(TestClass.class, "'onInterface'");
    }

    @PreEnforce(subject = "'onInterface'")
    interface TestInterfaceAnnotatedOnInterfaceAndMethod {
        @PreEnforce(subject = "'onInterfaceMethod'")
        void doSomething();
    }

    @Test
    void whenAnnotationOnInterfaceAnInterfaceMethod_ThenReturnsOnInterfaceMethod() {

        class TestClass implements TestInterfaceAnnotatedOnInterfaceAndMethod {
            public void doSomething() {
                // NOOP test dummy
            }
        }

        expectSubjectExpressionStringInAttribute(TestClass.class, "'onInterfaceMethod'");
    }

    @Test
    void whenAnnotationOnMethodAndClassAndOnInterfaceAndInterfaceMethod_ThenReturnsOnMethod() {

        @PreEnforce(subject = "'onClass'")
        class TestClass implements TestInterfaceAnnotatedOnInterfaceAndMethod {
            @PreEnforce(subject = "'onMethod'")
            public void doSomething() {
                // NOOP test dummy
            }
        }

        expectSubjectExpressionStringInAttribute(TestClass.class, "'onMethod'");
    }

    @Test
    void whenAnnotationOnMethod_ThenReturnsOnMethodForPost() {

        class TestClass {
            @PostEnforce(subject = "'onMethod'")
            public void doSomething() {
                // NOOP test dummy
            }
        }

        expectSubjectExpressionStringInAttribute(TestClass.class, "'onMethod'");
    }

    @Test
    void whenAnnotationOnMethod_ThenReturnsOnMethodForEnforceTillDenied() {

        class TestClass {
            @EnforceTillDenied(subject = "'onMethod'")
            public void doSomething() {
                // NOOP test dummy
            }
        }

        expectSubjectExpressionStringInAttribute(TestClass.class, "'onMethod'");
    }

    @Test
    void whenAnnotationOnMethod_ThenReturnsOnMethodForEnforceDropWhileDenied() {

        class TestClass {
            @EnforceDropWhileDenied(subject = "'onMethod'")
            public void doSomething() {
                // NOOP test dummy
            }
        }

        expectSubjectExpressionStringInAttribute(TestClass.class, "'onMethod'");
    }

    @Test
    void whenAnnotationOnMethod_ThenReturnsOnMethodForEnforceRecoverableIfDenied() {

        class TestClass {
            @EnforceRecoverableIfDenied(subject = "'onMethod'")
            public void doSomething() {
                // NOOP test dummy
            }
        }

        expectSubjectExpressionStringInAttribute(TestClass.class, "'onMethod'");
    }

    interface DefaultMethodInterface {
        @PostEnforce(subject = "'onDefaultInterfaceMethod'")
        default void doSomething() {
            // NOOP test dummy
        }
    }

    @Test
    void whenAnnotationOnDefaultMethodInInterface_ThenReturnsThat() {
        class TestClass implements DefaultMethodInterface {
        }
        expectSubjectExpressionStringInAttribute(TestClass.class, "'onDefaultInterfaceMethod'");
    }

    @Test
    void whenStreamEnforceCanonicalAnnotation_ThenStreamModeIsResolved() {

        class TestClass {
            @StreamEnforce(mode = StreamMode.DROP_WHILE_DENIED, subject = "'s'")
            public void doSomething() {
                // NOOP test dummy
            }
        }

        expectStreamModeInAttribute(TestClass.class, StreamMode.DROP_WHILE_DENIED);
    }

    @Test
    void whenEnforceTillDeniedAlias_ThenStreamModeIsTillDenied() {

        class TestClass {
            @EnforceTillDenied(subject = "'s'")
            public void doSomething() {
                // NOOP test dummy
            }
        }

        expectStreamModeInAttribute(TestClass.class, StreamMode.TILL_DENIED);
    }

    @Test
    void whenEnforceDropWhileDeniedAlias_ThenStreamModeIsDropWhileDenied() {

        class TestClass {
            @EnforceDropWhileDenied(subject = "'s'")
            public void doSomething() {
                // NOOP test dummy
            }
        }

        expectStreamModeInAttribute(TestClass.class, StreamMode.DROP_WHILE_DENIED);
    }

    @Test
    void whenEnforceAccessAwareAlias_ThenStreamModeIsAccessAware() {

        class TestClass {
            @EnforceAccessAware(subject = "'s'")
            public void doSomething() {
                // NOOP test dummy
            }
        }

        expectStreamModeInAttribute(TestClass.class, StreamMode.ACCESS_AWARE);
    }

    @Test
    void whenEnforceRecoverableIfDeniedLegacyAlias_ThenStreamModeIsAccessAware() {

        class TestClass {
            @EnforceRecoverableIfDenied(subject = "'s'")
            public void doSomething() {
                // NOOP test dummy
            }
        }

        expectStreamModeInAttribute(TestClass.class, StreamMode.ACCESS_AWARE);
    }

    @Test
    void whenPreEnforce_ThenStreamModeIsNull() {

        class TestClass {
            @PreEnforce(subject = "'s'")
            public void doSomething() {
                // NOOP test dummy
            }
        }

        final var sut       = new SaplAttributeRegistry();
        final var mi        = MethodInvocationUtils.createFromClass(TestClass.class, "doSomething");
        final var attribute = sut.getSaplAttributeForAnnotationType(mi, PreEnforce.class);
        assertThat(attribute).hasValueSatisfying(a -> assertThat(a.streamMode()).isNull());
    }

    private void expectSubjectExpressionStringInAttribute(Class<?> clazz, String expectedExpressionString) {
        final var sut        = new SaplAttributeRegistry();
        final var mi         = MethodInvocationUtils.createFromClass(clazz, "doSomething");
        final var attributes = sut.getAllSaplAttributes(mi);
        assertThat(attributes.values()).anySatisfy(
                attr -> assertThat(attr.subjectExpression().getExpressionString()).isEqualTo(expectedExpressionString));
    }

    private void expectStreamModeInAttribute(Class<?> clazz, StreamMode expectedMode) {
        final var sut       = new SaplAttributeRegistry();
        final var mi        = MethodInvocationUtils.createFromClass(clazz, "doSomething");
        final var attribute = sut.getSaplAttributeForAnnotationType(mi, StreamEnforce.class);
        assertThat(attribute).hasValueSatisfying(a -> assertThat(a.streamMode()).isEqualTo(expectedMode));
    }

}
