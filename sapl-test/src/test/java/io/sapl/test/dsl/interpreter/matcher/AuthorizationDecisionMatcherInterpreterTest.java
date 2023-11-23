package io.sapl.test.dsl.interpreter.matcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.interpreter.Val;
import io.sapl.hamcrest.HasAdvice;
import io.sapl.hamcrest.HasAdviceContainingKeyValue;
import io.sapl.hamcrest.HasObligation;
import io.sapl.hamcrest.HasObligationContainingKeyValue;
import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.interpreter.ValInterpreter;
import io.sapl.test.grammar.sAPLTest.AnyDecision;
import io.sapl.test.grammar.sAPLTest.AuthorizationDecisionMatcher;
import io.sapl.test.grammar.sAPLTest.AuthorizationDecisionMatcherType;
import io.sapl.test.grammar.sAPLTest.AuthorizationDecisionType;
import io.sapl.test.grammar.sAPLTest.DefaultObjectMatcher;
import io.sapl.test.grammar.sAPLTest.ExtendedObjectMatcher;
import io.sapl.test.grammar.sAPLTest.IsDecision;
import io.sapl.test.grammar.sAPLTest.JsonNodeMatcher;
import io.sapl.test.grammar.sAPLTest.ObjectWithExactMatch;
import io.sapl.test.grammar.sAPLTest.ObjectWithKeyValueMatcher;
import io.sapl.test.grammar.sAPLTest.ObjectWithMatcher;
import io.sapl.test.grammar.sAPLTest.Value;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthorizationDecisionMatcherInterpreterTest {
    @Mock
    private ValInterpreter valInterpreterMock;
    @Mock
    private JsonNodeMatcherInterpreter jsonNodeMatcherInterpreterMock;
    @InjectMocks
    private AuthorizationDecisionMatcherInterpreter authorizationDecisionMatcherInterpreter;

    private final MockedStatic<Matchers> hamcrestMatchersMockedStatic = mockStatic(Matchers.class);
    private final MockedStatic<CoreMatchers> hamcrestCoreMatchersMockedStatic = mockStatic(CoreMatchers.class);
    private final MockedStatic<io.sapl.hamcrest.Matchers> saplMatchersMockedStatic = mockStatic(io.sapl.hamcrest.Matchers.class);

    @AfterEach
    void tearDown() {
        hamcrestMatchersMockedStatic.close();
        hamcrestCoreMatchersMockedStatic.close();
        saplMatchersMockedStatic.close();
    }

    @Test
    void getHamcrestAuthorizationDecisionMatcher_handlesNullAuthorizationDecisionMatcher_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class, () -> authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(null));

        assertEquals("Unknown type of AuthorizationDecisionMatcher", exception.getMessage());
    }

    @Test
    void getHamcrestAuthorizationDecisionMatcher_handlesUnknownAuthorizationDecisionMatcher_throwsSaplTestException() {
        final var authorizationDecisionMatcherMock = mock(AuthorizationDecisionMatcher.class);

        final var exception = assertThrows(SaplTestException.class, () -> authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(authorizationDecisionMatcherMock));

        assertEquals("Unknown type of AuthorizationDecisionMatcher", exception.getMessage());
    }

    @Test
    void getHamcrestAuthorizationDecisionMatcher_handlesAnyDecision_returnsAnyDecisionMatcher() {
        final var anyDecisionMock = mock(AnyDecision.class);

        final var matcherMock = mock(Matcher.class);
        saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::anyDecision).thenReturn(matcherMock);

        final var result = authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(anyDecisionMock);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestAuthorizationDecisionMatcher_handlesNullDecision_throwsSaplTestException() {
        final var isDecisionMock = mock(IsDecision.class);
        when(isDecisionMock.getDecision()).thenReturn(null);

        final var exception = assertThrows(SaplTestException.class, () -> authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(isDecisionMock));

        assertEquals("Decision is null", exception.getMessage());
    }

    @Test
    void getHamcrestAuthorizationDecisionMatcher_handlesIsDecisionPermit_returnsPermitMatcher() {
        final var isDecisionMock = mock(IsDecision.class);
        when(isDecisionMock.getDecision()).thenReturn(AuthorizationDecisionType.PERMIT);

        final var matcherMock = mock(Matcher.class);
        saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::isPermit).thenReturn(matcherMock);

        final var result = authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(isDecisionMock);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestAuthorizationDecisionMatcher_handlesIsDecisionDeny_returnsDenyMatcher() {
        final var isDecisionMock = mock(IsDecision.class);
        when(isDecisionMock.getDecision()).thenReturn(AuthorizationDecisionType.DENY);

        final var matcherMock = mock(Matcher.class);
        saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::isDeny).thenReturn(matcherMock);

        final var result = authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(isDecisionMock);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestAuthorizationDecisionMatcher_handlesIsDecisionIndeterminate_returnsIndeterminateMatcher() {
        final var isDecisionMock = mock(IsDecision.class);
        when(isDecisionMock.getDecision()).thenReturn(AuthorizationDecisionType.INDETERMINATE);

        final var matcherMock = mock(Matcher.class);
        saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::isIndeterminate).thenReturn(matcherMock);

        final var result = authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(isDecisionMock);

        assertEquals(matcherMock, result);
    }

    @Test
    void getHamcrestAuthorizationDecisionMatcher_handlesIsDecisionNotApplicable_returnsNotApplicableMatcher() {
        final var isDecisionMock = mock(IsDecision.class);
        when(isDecisionMock.getDecision()).thenReturn(AuthorizationDecisionType.NOT_APPLICABLE);

        final var matcherMock = mock(Matcher.class);
        saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::isNotApplicable).thenReturn(matcherMock);

        final var result = authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(isDecisionMock);

        assertEquals(matcherMock, result);
    }

    @Nested
    @DisplayName("HasObligationOrAdvice tests")
    class HasObligationOrAdvice {
        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithDefaultObjectMatcherWithUnknownTypeForObligation_returnsHasAnyObligation() {
            final var hasObligationOrAdviceMock = mock(io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice.class);

            final var defaultObjectMatcher = mock(DefaultObjectMatcher.class);
            when(hasObligationOrAdviceMock.getMatcher()).thenReturn(defaultObjectMatcher);
            when(hasObligationOrAdviceMock.getType()).thenReturn(AuthorizationDecisionMatcherType.OBLIGATION);

            final var hasObligationMatcherMock = mock(HasObligation.class);
            saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::hasObligation).thenReturn(hasObligationMatcherMock);

            final var result = authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdviceMock);

            assertEquals(hasObligationMatcherMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithDefaultObjectMatcherWithUnknownTypeForAdvice_returnsHasAnyAdvice() {
            final var hasObligationOrAdviceMock = mock(io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice.class);

            final var defaultObjectMatcher = mock(DefaultObjectMatcher.class);
            when(hasObligationOrAdviceMock.getMatcher()).thenReturn(defaultObjectMatcher);
            when(hasObligationOrAdviceMock.getType()).thenReturn(AuthorizationDecisionMatcherType.ADVICE);

            final var hasObligationMatcherMock = mock(HasObligation.class);
            saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::hasAdvice).thenReturn(hasObligationMatcherMock);

            final var result = authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdviceMock);

            assertEquals(hasObligationMatcherMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithExactMatchForObligationWithNullMatcher_returnsHasAnyObligation() {
            final var hasObligationOrAdviceMock = mock(io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice.class);

            final var objectWithExactMatchMock = mock(ObjectWithExactMatch.class);
            when(hasObligationOrAdviceMock.getMatcher()).thenReturn(objectWithExactMatchMock);
            when(hasObligationOrAdviceMock.getType()).thenReturn(AuthorizationDecisionMatcherType.OBLIGATION);

            final var valueMock = mock(Value.class);
            when(objectWithExactMatchMock.getObject()).thenReturn(valueMock);

            final var valMock = mock(Val.class);
            when(valInterpreterMock.getValFromValue(valueMock)).thenReturn(valMock);

            final var jsonNodeMock = mock(JsonNode.class);
            when(valMock.get()).thenReturn(jsonNodeMock);

            hamcrestMatchersMockedStatic.when(() -> Matchers.is(jsonNodeMock)).thenReturn(null);

            final var hasObligationMatcherMock = mock(HasObligation.class);
            saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::hasObligation).thenReturn(hasObligationMatcherMock);

            final var result = authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdviceMock);

            assertEquals(hasObligationMatcherMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithExactMatchForAdviceWithNullMatcher_returnsHasAnyAdvice() {
            final var hasObligationOrAdviceMock = mock(io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice.class);

            final var objectWithExactMatchMock = mock(ObjectWithExactMatch.class);
            when(hasObligationOrAdviceMock.getMatcher()).thenReturn(objectWithExactMatchMock);
            when(hasObligationOrAdviceMock.getType()).thenReturn(AuthorizationDecisionMatcherType.ADVICE);

            final var valueMock = mock(Value.class);
            when(objectWithExactMatchMock.getObject()).thenReturn(valueMock);

            final var valMock = mock(Val.class);
            when(valInterpreterMock.getValFromValue(valueMock)).thenReturn(valMock);

            final var jsonNodeMock = mock(JsonNode.class);
            when(valMock.get()).thenReturn(jsonNodeMock);

            hamcrestMatchersMockedStatic.when(() -> Matchers.is(jsonNodeMock)).thenReturn(null);

            final var hasAdviceMatcherMock = mock(HasAdvice.class);
            saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::hasAdvice).thenReturn(hasAdviceMatcherMock);

            final var result = authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdviceMock);

            assertEquals(hasAdviceMatcherMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithExactMatchForObligation_returnsHasObligationWithMatcher() {
            final var hasObligationOrAdviceMock = mock(io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice.class);

            final var objectWithExactMatchMock = mock(ObjectWithExactMatch.class);
            when(hasObligationOrAdviceMock.getMatcher()).thenReturn(objectWithExactMatchMock);
            when(hasObligationOrAdviceMock.getType()).thenReturn(AuthorizationDecisionMatcherType.OBLIGATION);

            final var valueMock = mock(Value.class);
            when(objectWithExactMatchMock.getObject()).thenReturn(valueMock);

            final var valMock = mock(Val.class);
            when(valInterpreterMock.getValFromValue(valueMock)).thenReturn(valMock);

            final var jsonNodeMock = mock(JsonNode.class);
            when(valMock.get()).thenReturn(jsonNodeMock);

            final var isMatcherMock = mock(Matcher.class);
            hamcrestMatchersMockedStatic.when(() -> Matchers.is(jsonNodeMock)).thenReturn(isMatcherMock);

            final var hasObligationMatcherMock = mock(HasObligation.class);
            saplMatchersMockedStatic.when(() -> io.sapl.hamcrest.Matchers.hasObligation(isMatcherMock)).thenReturn(hasObligationMatcherMock);

            final var result = authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdviceMock);

            assertEquals(hasObligationMatcherMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithExactMatchForAdvice_returnsHasAdviceWithMatcher() {
            final var hasObligationOrAdviceMock = mock(io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice.class);

            final var objectWithExactMatchMock = mock(ObjectWithExactMatch.class);
            when(hasObligationOrAdviceMock.getMatcher()).thenReturn(objectWithExactMatchMock);
            when(hasObligationOrAdviceMock.getType()).thenReturn(AuthorizationDecisionMatcherType.ADVICE);

            final var valueMock = mock(Value.class);
            when(objectWithExactMatchMock.getObject()).thenReturn(valueMock);

            final var valMock = mock(Val.class);
            when(valInterpreterMock.getValFromValue(valueMock)).thenReturn(valMock);

            final var jsonNodeMock = mock(JsonNode.class);
            when(valMock.get()).thenReturn(jsonNodeMock);

            final var isMatcherMock = mock(Matcher.class);
            hamcrestMatchersMockedStatic.when(() -> Matchers.is(jsonNodeMock)).thenReturn(isMatcherMock);

            final var hasAdviceMatcherMock = mock(HasAdvice.class);
            saplMatchersMockedStatic.when(() -> io.sapl.hamcrest.Matchers.hasAdvice(isMatcherMock)).thenReturn(hasAdviceMatcherMock);

            final var result = authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdviceMock);

            assertEquals(hasAdviceMatcherMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithMatcherForObligationWithNullMatcher_returnsHasAnyObligation() {
            final var hasObligationOrAdviceMock = mock(io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice.class);

            final var objectWithMatcherMock = mock(ObjectWithMatcher.class);
            when(hasObligationOrAdviceMock.getMatcher()).thenReturn(objectWithMatcherMock);
            when(hasObligationOrAdviceMock.getType()).thenReturn(AuthorizationDecisionMatcherType.OBLIGATION);

            final var jsonNodeMatcherMock = mock(JsonNodeMatcher.class);
            when(objectWithMatcherMock.getMatcher()).thenReturn(jsonNodeMatcherMock);

            when(jsonNodeMatcherInterpreterMock.getHamcrestJsonNodeMatcher(jsonNodeMatcherMock)).thenReturn(null);

            final var hasObligationMatcherMock = mock(HasObligation.class);
            saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::hasObligation).thenReturn(hasObligationMatcherMock);

            final var result = authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdviceMock);

            assertEquals(hasObligationMatcherMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithMatcherForAdviceWithNullMatcher_returnsHasAnyAdvice() {
            final var hasObligationOrAdviceMock = mock(io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice.class);

            final var objectWithMatcherMock = mock(ObjectWithMatcher.class);
            when(hasObligationOrAdviceMock.getMatcher()).thenReturn(objectWithMatcherMock);
            when(hasObligationOrAdviceMock.getType()).thenReturn(AuthorizationDecisionMatcherType.ADVICE);

            final var jsonNodeMatcherMock = mock(JsonNodeMatcher.class);
            when(objectWithMatcherMock.getMatcher()).thenReturn(jsonNodeMatcherMock);

            when(jsonNodeMatcherInterpreterMock.getHamcrestJsonNodeMatcher(jsonNodeMatcherMock)).thenReturn(null);

            final var hasAdviceMatcherMock = mock(HasAdvice.class);
            saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::hasAdvice).thenReturn(hasAdviceMatcherMock);

            final var result = authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdviceMock);

            assertEquals(hasAdviceMatcherMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithMatcherForObligation_returnsHasObligationWithMatcher() {
            final var hasObligationOrAdviceMock = mock(io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice.class);

            final var objectWithMatcherMock = mock(ObjectWithMatcher.class);
            when(hasObligationOrAdviceMock.getMatcher()).thenReturn(objectWithMatcherMock);
            when(hasObligationOrAdviceMock.getType()).thenReturn(AuthorizationDecisionMatcherType.OBLIGATION);

            final var jsonNodeMatcherMock = mock(JsonNodeMatcher.class);
            when(objectWithMatcherMock.getMatcher()).thenReturn(jsonNodeMatcherMock);

            final var matcherMock = mock(Matcher.class);
            when(jsonNodeMatcherInterpreterMock.getHamcrestJsonNodeMatcher(jsonNodeMatcherMock)).thenReturn(matcherMock);

            final var hasObligationMatcherMock = mock(HasObligation.class);
            saplMatchersMockedStatic.when(() -> io.sapl.hamcrest.Matchers.hasObligation(matcherMock)).thenReturn(hasObligationMatcherMock);

            final var result = authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdviceMock);

            assertEquals(hasObligationMatcherMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithMatcherForAdvice_returnsHasAdviceWithMatcher() {
            final var hasObligationOrAdviceMock = mock(io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice.class);

            final var objectWithMatcherMock = mock(ObjectWithMatcher.class);
            when(hasObligationOrAdviceMock.getMatcher()).thenReturn(objectWithMatcherMock);
            when(hasObligationOrAdviceMock.getType()).thenReturn(AuthorizationDecisionMatcherType.ADVICE);

            final var jsonNodeMatcherMock = mock(JsonNodeMatcher.class);
            when(objectWithMatcherMock.getMatcher()).thenReturn(jsonNodeMatcherMock);

            final var matcherMock = mock(Matcher.class);
            when(jsonNodeMatcherInterpreterMock.getHamcrestJsonNodeMatcher(jsonNodeMatcherMock)).thenReturn(matcherMock);

            final var hasAdviceMatcherMock = mock(HasAdvice.class);
            saplMatchersMockedStatic.when(() -> io.sapl.hamcrest.Matchers.hasAdvice(matcherMock)).thenReturn(hasAdviceMatcherMock);

            final var result = authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdviceMock);

            assertEquals(hasAdviceMatcherMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithKeyValueMatcherForObligationWithNullValue_returnsHasObligationWithKey() {
            final var hasObligationOrAdviceMock = mock(io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice.class);

            final var objectWithKeyValueMatcherMock = mock(ObjectWithKeyValueMatcher.class);
            when(hasObligationOrAdviceMock.getMatcher()).thenReturn(objectWithKeyValueMatcherMock);
            when(hasObligationOrAdviceMock.getType()).thenReturn(AuthorizationDecisionMatcherType.OBLIGATION);

            final var valueMatcherMock = mock(JsonNodeMatcher.class);
            when(objectWithKeyValueMatcherMock.getKey()).thenReturn("foo");
            when(objectWithKeyValueMatcherMock.getValue()).thenReturn(valueMatcherMock);

            when(jsonNodeMatcherInterpreterMock.getHamcrestJsonNodeMatcher(valueMatcherMock)).thenReturn(null);

            final var hasObligationMatcherMock = mock(HasObligationContainingKeyValue.class);
            saplMatchersMockedStatic.when(() -> io.sapl.hamcrest.Matchers.hasObligationContainingKeyValue("foo")).thenReturn(hasObligationMatcherMock);

            final var result = authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdviceMock);

            assertEquals(hasObligationMatcherMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithKeyValueMatcherForAdviceWithNullValue_returnsHasAdviceWithKey() {
            final var hasObligationOrAdviceMock = mock(io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice.class);

            final var objectWithKeyValueMatcherMock = mock(ObjectWithKeyValueMatcher.class);
            when(hasObligationOrAdviceMock.getMatcher()).thenReturn(objectWithKeyValueMatcherMock);
            when(hasObligationOrAdviceMock.getType()).thenReturn(AuthorizationDecisionMatcherType.ADVICE);

            final var valueMatcherMock = mock(JsonNodeMatcher.class);
            when(objectWithKeyValueMatcherMock.getKey()).thenReturn("foo");
            when(objectWithKeyValueMatcherMock.getValue()).thenReturn(valueMatcherMock);

            when(jsonNodeMatcherInterpreterMock.getHamcrestJsonNodeMatcher(valueMatcherMock)).thenReturn(null);

            final var hasAdviceContainingKeyValueMock = mock(HasAdviceContainingKeyValue.class);
            saplMatchersMockedStatic.when(() -> io.sapl.hamcrest.Matchers.hasAdviceContainingKeyValue("foo")).thenReturn(hasAdviceContainingKeyValueMock);

            final var result = authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdviceMock);

            assertEquals(hasAdviceContainingKeyValueMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithKeyValueMatcherForObligation_returnsHasObligationWithKeyValue() {
            final var hasObligationOrAdviceMock = mock(io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice.class);

            final var objectWithKeyValueMatcherMock = mock(ObjectWithKeyValueMatcher.class);
            when(hasObligationOrAdviceMock.getMatcher()).thenReturn(objectWithKeyValueMatcherMock);
            when(hasObligationOrAdviceMock.getType()).thenReturn(AuthorizationDecisionMatcherType.OBLIGATION);

            final var valueMatcherMock = mock(JsonNodeMatcher.class);
            when(objectWithKeyValueMatcherMock.getKey()).thenReturn("foo");
            when(objectWithKeyValueMatcherMock.getValue()).thenReturn(valueMatcherMock);

            final var jsonNodeMatcherMock = mock(Matcher.class);
            when(jsonNodeMatcherInterpreterMock.getHamcrestJsonNodeMatcher(valueMatcherMock)).thenReturn(jsonNodeMatcherMock);

            final var hasObligationMatcherMock = mock(HasObligationContainingKeyValue.class);
            saplMatchersMockedStatic.when(() -> io.sapl.hamcrest.Matchers.hasObligationContainingKeyValue("foo", jsonNodeMatcherMock)).thenReturn(hasObligationMatcherMock);

            final var result = authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdviceMock);

            assertEquals(hasObligationMatcherMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithObjectWithKeyValueMatcherForAdvice_returnsHasAdviceWithKeyValue() {
            final var hasObligationOrAdviceMock = mock(io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice.class);

            final var objectWithKeyValueMatcherMock = mock(ObjectWithKeyValueMatcher.class);
            when(hasObligationOrAdviceMock.getMatcher()).thenReturn(objectWithKeyValueMatcherMock);
            when(hasObligationOrAdviceMock.getType()).thenReturn(AuthorizationDecisionMatcherType.ADVICE);

            final var valueMatcherMock = mock(JsonNodeMatcher.class);
            when(objectWithKeyValueMatcherMock.getKey()).thenReturn("foo");
            when(objectWithKeyValueMatcherMock.getValue()).thenReturn(valueMatcherMock);

            final var jsonNodeMatcherMock = mock(Matcher.class);
            when(jsonNodeMatcherInterpreterMock.getHamcrestJsonNodeMatcher(valueMatcherMock)).thenReturn(jsonNodeMatcherMock);

            final var hasAdviceContainingKeyValueMock = mock(HasAdviceContainingKeyValue.class);
            saplMatchersMockedStatic.when(() -> io.sapl.hamcrest.Matchers.hasAdviceContainingKeyValue("foo", jsonNodeMatcherMock)).thenReturn(hasAdviceContainingKeyValueMock);

            final var result = authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdviceMock);

            assertEquals(hasAdviceContainingKeyValueMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithNullObjectMatcherForObligation_throwsSaplTestException() {
            final var hasObligationOrAdviceMock = mock(io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice.class);

            when(hasObligationOrAdviceMock.getMatcher()).thenReturn(null);
            when(hasObligationOrAdviceMock.getType()).thenReturn(AuthorizationDecisionMatcherType.OBLIGATION);

            final var exception = assertThrows(SaplTestException.class, () -> authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdviceMock));

            assertEquals("Unknown type of ExtendedObjectMatcher", exception.getMessage());
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithNullObjectMatcherForAdvice_throwsSaplTestException() {
            final var hasObligationOrAdviceMock = mock(io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice.class);

            when(hasObligationOrAdviceMock.getMatcher()).thenReturn(null);
            when(hasObligationOrAdviceMock.getType()).thenReturn(AuthorizationDecisionMatcherType.ADVICE);

            final var exception = assertThrows(SaplTestException.class, () -> authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdviceMock));

            assertEquals("Unknown type of ExtendedObjectMatcher", exception.getMessage());
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithUnknownObjectMatcherForObligation_throwsSaplTestException() {
            final var hasObligationOrAdviceMock = mock(io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice.class);

            final var unknownObjectMatcher = mock(ExtendedObjectMatcher.class);
            when(hasObligationOrAdviceMock.getMatcher()).thenReturn(unknownObjectMatcher);
            when(hasObligationOrAdviceMock.getType()).thenReturn(AuthorizationDecisionMatcherType.OBLIGATION);

            final var exception = assertThrows(SaplTestException.class, () -> authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdviceMock));

            assertEquals("Unknown type of ExtendedObjectMatcher", exception.getMessage());
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasObligationOrAdviceWithUnknownObjectMatcherForAdvice_throwsSaplTestException() {
            final var hasObligationOrAdviceMock = mock(io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice.class);

            final var unknownObjectMatcher = mock(ExtendedObjectMatcher.class);
            when(hasObligationOrAdviceMock.getMatcher()).thenReturn(unknownObjectMatcher);
            when(hasObligationOrAdviceMock.getType()).thenReturn(AuthorizationDecisionMatcherType.ADVICE);

            final var exception = assertThrows(SaplTestException.class, () -> authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(hasObligationOrAdviceMock));

            assertEquals("Unknown type of ExtendedObjectMatcher", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("HasResource tests")
    class HasResourceTests {
        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasResourceWithObjectWithExactMatchWithNullMatcher_returnsHasAnyResource() {
            final var hasResourceMock = mock(io.sapl.test.grammar.sAPLTest.HasResource.class);

            final var objectWithExactMatchMock = mock(ObjectWithExactMatch.class);
            when(hasResourceMock.getMatcher()).thenReturn(objectWithExactMatchMock);

            final var valueMock = mock(Value.class);
            when(objectWithExactMatchMock.getObject()).thenReturn(valueMock);

            final var valMock = mock(Val.class);
            when(valInterpreterMock.getValFromValue(valueMock)).thenReturn(valMock);

            final var jsonNodeMock = mock(JsonNode.class);
            when(valMock.get()).thenReturn(jsonNodeMock);

            hamcrestMatchersMockedStatic.when(() -> Matchers.is(jsonNodeMock)).thenReturn(null);

            final var hasResourceMatcherMock = mock(io.sapl.hamcrest.HasResource.class);
            saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::hasResource).thenReturn(hasResourceMatcherMock);

            final var result = authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(hasResourceMock);

            assertEquals(hasResourceMatcherMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasResourceWithObjectWithExactMatch_returnsHasResourceWithMatcher() {
            final var hasResourceMock = mock(io.sapl.test.grammar.sAPLTest.HasResource.class);

            final var objectWithExactMatchMock = mock(ObjectWithExactMatch.class);
            when(hasResourceMock.getMatcher()).thenReturn(objectWithExactMatchMock);

            final var valueMock = mock(Value.class);
            when(objectWithExactMatchMock.getObject()).thenReturn(valueMock);

            final var valMock = mock(Val.class);
            when(valInterpreterMock.getValFromValue(valueMock)).thenReturn(valMock);

            final var jsonNodeMock = mock(JsonNode.class);
            when(valMock.get()).thenReturn(jsonNodeMock);

            final var isMatcherMock = mock(Matcher.class);
            hamcrestMatchersMockedStatic.when(() -> Matchers.is(jsonNodeMock)).thenReturn(isMatcherMock);

            final var hasResourceMatcherMock = mock(io.sapl.hamcrest.HasResource.class);
            saplMatchersMockedStatic.when(() -> io.sapl.hamcrest.Matchers.hasResource(isMatcherMock)).thenReturn(hasResourceMatcherMock);

            final var result = authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(hasResourceMock);

            assertEquals(hasResourceMatcherMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasResourceWithObjectWithMatcherWithNullMatcher_returnsHasAnyResource() {
            final var hasResourceMock = mock(io.sapl.test.grammar.sAPLTest.HasResource.class);

            final var objectWithMatcherMock = mock(ObjectWithMatcher.class);
            when(hasResourceMock.getMatcher()).thenReturn(objectWithMatcherMock);

            final var jsonNodeMatcherMock = mock(JsonNodeMatcher.class);
            when(objectWithMatcherMock.getMatcher()).thenReturn(jsonNodeMatcherMock);

            when(jsonNodeMatcherInterpreterMock.getHamcrestJsonNodeMatcher(jsonNodeMatcherMock)).thenReturn(null);

            final var hasResourceMatcherMock = mock(io.sapl.hamcrest.HasResource.class);
            saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::hasResource).thenReturn(hasResourceMatcherMock);

            final var result = authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(hasResourceMock);

            assertEquals(hasResourceMatcherMock, result);
        }


        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasResourceWithObjectWithMatcher_returnsHasResourceWithMatcher() {
            final var hasResourceMock = mock(io.sapl.test.grammar.sAPLTest.HasResource.class);

            final var objectWithMatcherMock = mock(ObjectWithMatcher.class);
            when(hasResourceMock.getMatcher()).thenReturn(objectWithMatcherMock);

            final var jsonNodeMatcherMock = mock(JsonNodeMatcher.class);
            when(objectWithMatcherMock.getMatcher()).thenReturn(jsonNodeMatcherMock);

            final var matcherMock = mock(Matcher.class);
            when(jsonNodeMatcherInterpreterMock.getHamcrestJsonNodeMatcher(jsonNodeMatcherMock)).thenReturn(matcherMock);

            final var hasResourceMatcherMock = mock(io.sapl.hamcrest.HasResource.class);
            saplMatchersMockedStatic.when(() -> io.sapl.hamcrest.Matchers.hasResource(matcherMock)).thenReturn(hasResourceMatcherMock);

            final var result = authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(hasResourceMock);

            assertEquals(hasResourceMatcherMock, result);
        }

        @Test
        void getHamcrestAuthorizationDecisionMatcher_handlesHasResourceWithDefaultObjectMatcherWithUnknownType_returnsHasAnyResource() {
            final var hasResource = mock(io.sapl.test.grammar.sAPLTest.HasResource.class);

            final var defaultObjectMatcher = mock(DefaultObjectMatcher.class);
            when(hasResource.getMatcher()).thenReturn(defaultObjectMatcher);

            final var hasResourceMatcherMock = mock(HasObligation.class);
            saplMatchersMockedStatic.when(io.sapl.hamcrest.Matchers::hasResource).thenReturn(hasResourceMatcherMock);

            final var result = authorizationDecisionMatcherInterpreter.getHamcrestAuthorizationDecisionMatcher(hasResource);

            assertEquals(hasResourceMatcherMock, result);
        }
    }

}