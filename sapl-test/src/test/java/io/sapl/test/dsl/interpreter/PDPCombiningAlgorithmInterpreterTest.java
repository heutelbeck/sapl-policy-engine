package io.sapl.test.dsl.interpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.test.grammar.sAPLTest.CombiningAlgorithm;
import io.sapl.test.grammar.sAPLTest.DenyOverridesCombiningAlgorithm;
import io.sapl.test.grammar.sAPLTest.OnlyOneApplicableCombiningAlgorithm;
import io.sapl.test.grammar.sAPLTest.PermitOverridesCombiningAlgorithm;
import io.sapl.test.grammar.sAPLTest.PermitUnlessDenyCombiningAlgorithm;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PDPCombiningAlgorithmInterpreterTest {

    private PDPCombiningAlgorithmInterpreter pdpCombiningAlgorithmInterpreter;

    @BeforeEach
    void setUp() {
        pdpCombiningAlgorithmInterpreter = new PDPCombiningAlgorithmInterpreter();
    }

    @ParameterizedTest
    @MethodSource("provideCombiningAlgorithmToPolicyDocumentCombiningAlgorithmMapping")
    void interpretPdpCombiningAlgorithm(final Class<CombiningAlgorithm> combiningAlgorithmClass, final PolicyDocumentCombiningAlgorithm expectedCombiningAlgorithm) {
        final var combiningAlgorithmMock = mock(combiningAlgorithmClass);

        final var result = pdpCombiningAlgorithmInterpreter.interpretPdpCombiningAlgorithm(combiningAlgorithmMock);

        assertEquals(expectedCombiningAlgorithm, result);
    }

    private static Stream<Arguments> provideCombiningAlgorithmToPolicyDocumentCombiningAlgorithmMapping() {
        return Stream.of(
                Arguments.of(DenyOverridesCombiningAlgorithm.class, PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES),
                Arguments.of(PermitOverridesCombiningAlgorithm.class, PolicyDocumentCombiningAlgorithm.PERMIT_OVERRIDES),
                Arguments.of(OnlyOneApplicableCombiningAlgorithm.class, PolicyDocumentCombiningAlgorithm.ONLY_ONE_APPLICABLE),
                Arguments.of(PermitUnlessDenyCombiningAlgorithm.class, PolicyDocumentCombiningAlgorithm.PERMIT_UNLESS_DENY),
                Arguments.of(CombiningAlgorithm.class, PolicyDocumentCombiningAlgorithm.DENY_UNLESS_PERMIT));
    }
}