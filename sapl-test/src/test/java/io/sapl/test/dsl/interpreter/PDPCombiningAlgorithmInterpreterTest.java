package io.sapl.test.dsl.interpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sAPLTest.CombiningAlgorithm;
import io.sapl.test.grammar.sAPLTest.DenyOverridesCombiningAlgorithm;
import io.sapl.test.grammar.sAPLTest.DenyUnlessPermitCombiningAlgorithm;
import io.sapl.test.grammar.sAPLTest.OnlyOneApplicableCombiningAlgorithm;
import io.sapl.test.grammar.sAPLTest.PermitOverridesCombiningAlgorithm;
import io.sapl.test.grammar.sAPLTest.PermitUnlessDenyCombiningAlgorithm;
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

    private static Stream<Arguments> provideCombiningAlgorithmToPolicyDocumentCombiningAlgorithmMapping() {
        return Stream.of(
                Arguments.of(DenyOverridesCombiningAlgorithm.class, PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES),
                Arguments.of(PermitOverridesCombiningAlgorithm.class, PolicyDocumentCombiningAlgorithm.PERMIT_OVERRIDES),
                Arguments.of(OnlyOneApplicableCombiningAlgorithm.class, PolicyDocumentCombiningAlgorithm.ONLY_ONE_APPLICABLE),
                Arguments.of(DenyUnlessPermitCombiningAlgorithm.class, PolicyDocumentCombiningAlgorithm.DENY_UNLESS_PERMIT),
                Arguments.of(PermitUnlessDenyCombiningAlgorithm.class, PolicyDocumentCombiningAlgorithm.PERMIT_UNLESS_DENY));
    }

    @ParameterizedTest
    @MethodSource("provideCombiningAlgorithmToPolicyDocumentCombiningAlgorithmMapping")
    void interpretPdpCombiningAlgorithm_handlesGivenCombiningAlgorithm_returnsPolicyDocumentCombiningAlgorithm(final Class<CombiningAlgorithm> combiningAlgorithmClass, final PolicyDocumentCombiningAlgorithm expectedCombiningAlgorithm) {
        final var combiningAlgorithmMock = mock(combiningAlgorithmClass);

        final var result = pdpCombiningAlgorithmInterpreter.interpretPdpCombiningAlgorithm(combiningAlgorithmMock);

        assertEquals(expectedCombiningAlgorithm, result);
    }

    @Test
    void interpretPdpCombiningAlgorithm_handlesUnknownCombiningAlgorithm_throwsSaplTestException() {
        final var combiningAlgorithmMock = mock(CombiningAlgorithm.class);

        final var exception = assertThrows(SaplTestException.class, () -> pdpCombiningAlgorithmInterpreter.interpretPdpCombiningAlgorithm(combiningAlgorithmMock));

        assertEquals("Unknown type of CombiningAlgorithm", exception.getMessage());
    }
}