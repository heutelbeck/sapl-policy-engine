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

import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.ParserUtil;
import io.sapl.test.grammar.sAPLTest.AuthorizationSubscription;
import io.sapl.test.grammar.sAPLTest.StringLiteral;
import io.sapl.test.grammar.services.SAPLTestGrammarAccess;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthorizationSubscriptionInterpreterTest {
    @Mock
    private ValInterpreter                       valInterpreterMock;
    @InjectMocks
    private AuthorizationSubscriptionInterpreter authorizationSubscriptionInterpreter;

    private final MockedStatic<io.sapl.api.pdp.AuthorizationSubscription> authorizationSubscriptionMockedStatic = mockStatic(
            io.sapl.api.pdp.AuthorizationSubscription.class);

    @AfterEach
    void tearDown() {
        authorizationSubscriptionMockedStatic.close();
    }

    private AuthorizationSubscription buildAuthorizationSubscription(final String input) {
        return ParserUtil.buildExpression(input, SAPLTestGrammarAccess::getAuthorizationSubscriptionRule);
    }

    private void mockValInterpreter(Map<String, Val> expectedValueToReturnValue) {
        when(valInterpreterMock.getValFromValue(any())).thenAnswer(invocationOnMock -> {
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
    void constructAuthorizationSubscription_correctlyInterpretsAuthorizationSubscription_returnsSAPLAuthorizationSubscription() {
        final var authorizationSubscription = buildAuthorizationSubscription(
                "subject \"foo\" attempts action \"action\" on resource \"resource\" with environment \"environment\"");

        final var subject     = Val.of("subject");
        final var action      = Val.of("action");
        final var resource    = Val.of("resource");
        final var environment = Val.of("environment");

        mockValInterpreter(Map.of("foo", subject, "action", action, "resource", resource, "environment", environment));

        final var saplAuthorizationSubscriptionMock = mock(io.sapl.api.pdp.AuthorizationSubscription.class);
        authorizationSubscriptionMockedStatic.when(() -> io.sapl.api.pdp.AuthorizationSubscription.of(subject.get(),
                action.get(), resource.get(), environment.get())).thenReturn(saplAuthorizationSubscriptionMock);

        final var result = authorizationSubscriptionInterpreter
                .constructAuthorizationSubscription(authorizationSubscription);

        assertEquals(saplAuthorizationSubscriptionMock, result);
    }
}
