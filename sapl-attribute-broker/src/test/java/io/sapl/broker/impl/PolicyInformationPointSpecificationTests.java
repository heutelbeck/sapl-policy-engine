package io.sapl.broker.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.lang.annotation.Annotation;
import java.util.List;

import org.junit.jupiter.api.Test;

class PolicyInformationPointSpecificationTests {

    @Test
    void whenConstructionOfPolicyInformationPointSpecificationHasBadParametersThenThrowElseDoNotThrow() {
        final List<Annotation>       entityValidators    = List.of();
        final List<List<Annotation>> parameterValidators = List.of();
        assertThatThrownBy(() -> new PolicyInformationPointSpecification(null, true, 0, true, entityValidators,
                parameterValidators)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                () -> new PolicyInformationPointSpecification("abc.def", true, 0, true, null, parameterValidators))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                () -> new PolicyInformationPointSpecification("abc.def", true, 0, true, entityValidators, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PolicyInformationPointSpecification(" abc.def ", true, 0, true, entityValidators,
                parameterValidators)).isInstanceOf(IllegalArgumentException.class);
        assertDoesNotThrow(() -> new PolicyInformationPointSpecification("abc.def", true, 0, true, entityValidators,
                parameterValidators));
    }

    @Test
    void whenVarArgsCheckedThenVarArgsCorrectlyDetected() {
        var withVarArgs = new PolicyInformationPointSpecification("abc.def", true,
                PolicyInformationPointSpecification.HAS_VARIABLE_NUMBER_OF_ARGUMENTS, true, List.of(), List.of());
        assertThat(withVarArgs.hasVariableNumberOfArguments()).isTrue();
        var notWithVarArgs = new PolicyInformationPointSpecification("abc.def", true, 0, true, List.of(), List.of());
        assertThat(notWithVarArgs.hasVariableNumberOfArguments()).isFalse();
    }

}
