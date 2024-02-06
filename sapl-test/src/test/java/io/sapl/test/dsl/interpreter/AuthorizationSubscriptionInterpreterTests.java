/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

package io.sapl.test.dsl.interpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.sapl.test.grammar.sapltest.Object;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.ParserUtil;
import io.sapl.test.grammar.sapltest.AuthorizationSubscription;
import io.sapl.test.grammar.sapltest.StringLiteral;
import io.sapl.test.grammar.services.SAPLTestGrammarAccess;

@ExtendWith(MockitoExtension.class)
class AuthorizationSubscriptionInterpreterTests {
    @Mock
    private ValueInterpreter                       valueInterpreterMock;
    @InjectMocks
    protected AuthorizationSubscriptionInterpreter authorizationSubscriptionInterpreter;

    protected final MockedStatic<io.sapl.api.pdp.AuthorizationSubscription> authorizationSubscriptionMockedStatic = mockStatic(
            io.sapl.api.pdp.AuthorizationSubscription.class);

    @AfterEach
    void tearDown() {
        authorizationSubscriptionMockedStatic.close();
    }

    private AuthorizationSubscription buildAuthorizationSubscription(final String input) {
        return ParserUtil.parseInputByRule(input, SAPLTestGrammarAccess::getAuthorizationSubscriptionRule,
                AuthorizationSubscription.class);
    }

    private void mockValInterpreter(Map<String, Val> expectedValueToReturnValue) {
        when(valueInterpreterMock.getValFromValue(any(StringLiteral.class))).thenAnswer(invocationOnMock -> {
            final StringLiteral value = invocationOnMock.getArgument(0);
            return expectedValueToReturnValue.get(value.getString());
        });
    }

    @Test
    void constructAuthorizationSubscription_handlesNullAuthorizationSubscription_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class,
                () -> authorizationSubscriptionInterpreter.constructAuthorizationSubscription(null));

        assertEquals("AuthorizationSubscription is null", exception.getMessage());
    }

    @ParameterizedTest
    @MethodSource("SubjectOrActionOrResourceNull")
    void constructAuthorizationSubscription_handlesNullSubject_throwsSaplTestException(final Map<String, Val> mapping) {
        final var authorizationSubscription = buildAuthorizationSubscription(
                "subject \"subject\" attempts action \"action\" on resource \"resource\"");

        mockValInterpreter(mapping);

        final var exception = assertThrows(SaplTestException.class, () -> authorizationSubscriptionInterpreter
                .constructAuthorizationSubscription(authorizationSubscription));

        assertEquals("subject or action or resource is null", exception.getMessage());
    }

    private static Stream<Arguments> SubjectOrActionOrResourceNull() {
        return Stream.of(Arguments.of(Collections.emptyMap()), Arguments.of(Map.of("subject", Val.of("subject"))),
                Arguments.of(Map.of("action", Val.of("action"))), Arguments.of(Map.of("resource", Val.of("resource"))),
                Arguments.of(Map.of("subject", Val.of("subject"), "action", Val.of("action"))),
                Arguments.of(Map.of("subject", Val.of("subject"), "resource", Val.of("resource"))),
                Arguments.of(Map.of("action", Val.of("action"), "resource", Val.of("resource"))));
    }

    @Test
    void constructAuthorizationSubscription_correctlyInterpretsAuthorizationSubscriptionWithMissingEnvironment_returnsSAPLAuthorizationSubscription() {
        final var authorizationSubscription = buildAuthorizationSubscription(
                "subject \"subject\" attempts action \"action\" on resource \"resource\"");

        final var subject  = Val.of("subject");
        final var action   = Val.of("action");
        final var resource = Val.of("resource");

        mockValInterpreter(Map.of("subject", subject, "action", action, "resource", resource));

        final var saplAuthorizationSubscriptionMock = mock(io.sapl.api.pdp.AuthorizationSubscription.class);
        authorizationSubscriptionMockedStatic
                .when(() -> io.sapl.api.pdp.AuthorizationSubscription.of(subject.get(), action.get(), resource.get()))
                .thenReturn(saplAuthorizationSubscriptionMock);

        final var result = authorizationSubscriptionInterpreter
                .constructAuthorizationSubscription(authorizationSubscription);

        assertEquals(saplAuthorizationSubscriptionMock, result);
    }

    @Test
    void constructAuthorizationSubscription_correctlyInterpretsAuthorizationSubscriptionWithNullMappedEnvironment_throwsSaplTestException() {
        final var authorizationSubscription = buildAuthorizationSubscription(
                "subject \"subject\" attempts action \"action\" on resource \"resource\" with environment { }");

        final var subject  = Val.of("subject");
        final var action   = Val.of("action");
        final var resource = Val.of("resource");

        mockValInterpreter(Map.of("subject", subject, "action", action, "resource", resource));

        final var exception = assertThrows(SaplTestException.class, () -> authorizationSubscriptionInterpreter
                .constructAuthorizationSubscription(authorizationSubscription));

        assertEquals("Environment is null", exception.getMessage());
    }

    @Test
    void constructAuthorizationSubscription_correctlyInterpretsAuthorizationSubscription_returnsSAPLAuthorizationSubscription() {
        final var authorizationSubscription = buildAuthorizationSubscription(
                "subject \"foo\" attempts action \"action\" on resource \"resource\" with environment { }");

        final var subject     = Val.of("subject");
        final var action      = Val.of("action");
        final var resource    = Val.of("resource");
        final var environment = Val.ofEmptyObject();

        mockValInterpreter(Map.of("foo", subject, "action", action, "resource", resource));

        when(valueInterpreterMock.getValFromValue(any(io.sapl.test.grammar.sapltest.Object.class)))
                .thenReturn(environment);

        final var saplAuthorizationSubscriptionMock = mock(io.sapl.api.pdp.AuthorizationSubscription.class);
        authorizationSubscriptionMockedStatic.when(() -> io.sapl.api.pdp.AuthorizationSubscription.of(subject.get(),
                action.get(), resource.get(), environment.get())).thenReturn(saplAuthorizationSubscriptionMock);

        final var result = authorizationSubscriptionInterpreter
                .constructAuthorizationSubscription(authorizationSubscription);

        assertEquals(saplAuthorizationSubscriptionMock, result);
    }
}
