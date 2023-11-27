package io.sapl.test.dsl.interpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sAPLTest.CombiningAlgorithmEnum;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PDPCombiningAlgorithmInterpreterTest {
    private PDPCombiningAlgorithmInterpreter pdpCombiningAlgorithmInterpreter;

    @BeforeEach
    void setUp() {
        pdpCombiningAlgorithmInterpreter = new PDPCombiningAlgorithmInterpreter();
    }

    @Test
    void interpretPdpCombiningAlgorithm_handlesUnknownCombiningAlgorithm_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class, () -> pdpCombiningAlgorithmInterpreter.interpretPdpCombiningAlgorithm(null));

        assertEquals("CombiningAlgorithm is null", exception.getMessage());
    }

    private static Stream<Arguments> provideCombiningAlgorithmToPolicyDocumentCombiningAlgorithmMapping() {
        return Stream.of(
                Arguments.of(CombiningAlgorithmEnum.DENY_OVERRIDES, PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES),
                Arguments.of(CombiningAlgorithmEnum.PERMIT_OVERRIDES, PolicyDocumentCombiningAlgorithm.PERMIT_OVERRIDES),
                Arguments.of(CombiningAlgorithmEnum.ONLY_ONE_APPLICABLE, PolicyDocumentCombiningAlgorithm.ONLY_ONE_APPLICABLE),
                Arguments.of(CombiningAlgorithmEnum.DENY_UNLESS_PERMIT, PolicyDocumentCombiningAlgorithm.DENY_UNLESS_PERMIT),
                Arguments.of(CombiningAlgorithmEnum.PERMIT_UNLESS_DENY, PolicyDocumentCombiningAlgorithm.PERMIT_UNLESS_DENY));
    }

    @ParameterizedTest
    @MethodSource("provideCombiningAlgorithmToPolicyDocumentCombiningAlgorithmMapping")
    void interpretPdpCombiningAlgorithm_handlesGivenCombiningAlgorithm_returnsPolicyDocumentCombiningAlgorithm(final CombiningAlgorithmEnum combiningAlgorithm, final PolicyDocumentCombiningAlgorithm expectedCombiningAlgorithm) {
        final var result = pdpCombiningAlgorithmInterpreter.interpretPdpCombiningAlgorithm(combiningAlgorithm);

        assertEquals(expectedCombiningAlgorithm, result);
    }
}