package io.sapl.broker.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NameValidatorTests {

    @ParameterizedTest
    @ValueSource(strings = { "", " ", " abc.def", "abc.def ", " abc.def ", "abc. def", "abc", "abc.123as",
            "a.b.c.d.e.f.g.h.i.j.k" })
    void whenPresentedWithInvalidNamesThenAssertionThrowsIllegalArgumentException(String invalidName) {
        assertThatThrownBy(() -> NameValidator.assertIsValidName(invalidName))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "a.b", "a1.b2", "a2.d.x333", "a.b.c.d.e.f.g.h.i.j" })
    void whenPresentedWithValidNamesThenAssertionDoesNotThrow(String validName) {
        assertDoesNotThrow(() -> NameValidator.assertIsValidName(validName));
    }

}
