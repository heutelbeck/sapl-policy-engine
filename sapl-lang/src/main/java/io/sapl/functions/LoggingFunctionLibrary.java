/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.functions;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Text;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Function library for logging values during policy evaluation.
 * <p>
 * This library is primarily useful when running an embedded PDP where the
 * application has direct access to the PDP's log output. Functions allow
 * inspection of values during policy evaluation for debugging and testing
 * without affecting authorization decisions.
 * <p>
 * Two function categories are provided:
 * <ul>
 * <li>Spy functions: Return the inspected value unchanged, allowing inline
 * wrapping of expressions</li>
 * <li>Logging functions: Always return {@code true}, enabling additional
 * statement lines in policy {@code where} blocks</li>
 * </ul>
 */
@Slf4j
@UtilityClass
@FunctionLibrary(name = LoggingFunctionLibrary.NAME, description = LoggingFunctionLibrary.DESCRIPTION)
public class LoggingFunctionLibrary {

    public static final String  NAME        = "log";
    public static final String  DESCRIPTION = "Utility functions for dumping data from policy evaluation on the PDP console for debugging of policies.";
    private static final String TEMPLATE    = "[SAPL] {} {}";

    private enum LogLevel {
        TRACE,
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    private static Val spy(LogLevel level, Val message, Val value) {
        var text = message.getText();
        switch (level) {
        case TRACE -> log.trace(TEMPLATE, text, value);
        case DEBUG -> log.debug(TEMPLATE, text, value);
        case INFO  -> log.info(TEMPLATE, text, value);
        case WARN  -> log.warn(TEMPLATE, text, value);
        case ERROR -> log.error(TEMPLATE, text, value);
        }
        return value;
    }

    private static Val logStatement(LogLevel level, Val message, Val value) {
        spy(level, message, value);
        return Val.TRUE;
    }

    /**
     * Logs a value at TRACE level and returns it unchanged.
     *
     * @param message a text message
     * @param value a value
     * @return the value
     */
    @Function(docs = """
            ```traceSpy(TEXT message, value)```: Logs the ```value``` prepended with the ```message``` to the
            console at the TRACE log level.
            The function behaves like the identity function, returning ```value``` unchanged.
            This allows wrapping any value in a SAPL expression without changing the overall structure of the policy.

            **Example:**
            ```sapl
            policy "audit_user_access"
            permit
            where
              log.traceSpy("Checking user", subject.name) == "admin";
            ```
            """)
    public static Val traceSpy(@Text Val message, Val value) {
        return spy(LogLevel.TRACE, message, value);
    }

    /**
     * Logs a value at DEBUG level and returns it unchanged.
     *
     * @param message a text message
     * @param value a value
     * @return the value
     */
    @Function(docs = """
            ```debugSpy(TEXT message, value)```: Logs the ```value``` prepended with the ```message``` to the
            console at the DEBUG log level.
            The function behaves like the identity function, returning ```value``` unchanged.
            This allows wrapping any value in a SAPL expression without changing the overall structure of the policy.

            **Example:**
            ```sapl
            policy "validate_permissions"
            permit
            where
              log.debugSpy("Permissions list", subject.permissions) |> filter.contains("read");
            ```
            """)
    public static Val debugSpy(@Text Val message, Val value) {
        return spy(LogLevel.DEBUG, message, value);
    }

    /**
     * Logs a value at INFO level and returns it unchanged.
     *
     * @param message a text message
     * @param value a value
     * @return the value
     */
    @Function(docs = """
            ```infoSpy(TEXT message, value)```: Logs the provided ```value```, prepended with the ```message```, to the
            console at the INFO log level.
            The function behaves like the identity function, returning ```value``` unchanged.
            This allows wrapping any value in a SAPL expression without changing the overall structure of the policy.

            **Example:**
            ```sapl
            policy "check_resource_owner"
            permit
            where
              log.infoSpy("Resource owner", resource.ownerId) == subject.id;
            ```
            """)
    public static Val infoSpy(@Text Val message, Val value) {
        return spy(LogLevel.INFO, message, value);
    }

    /**
     * Logs a value at WARN level and returns it unchanged.
     *
     * @param message a text message
     * @param value a value
     * @return the value
     */
    @Function(docs = """
            ```warnSpy(TEXT message, value)```: Logs the ```value``` prepended with the ```message``` to the
            console at the WARN log level.
            The function behaves like the identity function, returning ```value``` unchanged.
            This allows wrapping any value in a SAPL expression without changing the overall structure of the policy.

            **Example:**
            ```sapl
            policy "monitor_suspicious_access"
            permit
            where
              log.warnSpy("Access attempt from", subject.ipAddress) in resource.allowedIPs;
            ```
            """)
    public static Val warnSpy(@Text Val message, Val value) {
        return spy(LogLevel.WARN, message, value);
    }

    /**
     * Logs a value at ERROR level and returns it unchanged.
     *
     * @param message a text message
     * @param value a value
     * @return the value
     */
    @Function(docs = """
            ```errorSpy(TEXT message, value)```: Logs the ```value``` prepended with the ```message``` to the
            console at the ERROR log level.
            The function behaves like the identity function, returning ```value``` unchanged.
            This allows wrapping any value in a SAPL expression without changing the overall structure of the policy.

            **Example:**
            ```sapl
            policy "track_authorization_failures"
            permit
            where
              log.errorSpy("Failed auth for user", subject.username) != "guest";
            ```
            """)
    public static Val errorSpy(@Text Val message, Val value) {
        return spy(LogLevel.ERROR, message, value);
    }

    /**
     * Logs a value at TRACE level and returns true.
     *
     * @param message a text message
     * @param value a value
     * @return true
     */
    @Function(docs = """
            ```trace(TEXT message, value)```: Logs the ```value``` prepended with the ```message``` to the
            console at the TRACE log level.
            This function is useful to add an additional statement line in a ```where``` block of a policy.
            As the function always returns ```true```, the rest of the policy evaluation is not affected.
            *Note:* If a statement above the logging statement evaluates to ```false```, the logger will
            not be triggered, as the evaluation of statements is lazy.

            **Example:**
            ```sapl
            policy "detailed_access_log"
            permit
            where
              log.trace("Request details", action);
              subject.role == "auditor";
            ```
            """)
    public static Val trace(@Text Val message, Val value) {
        return logStatement(LogLevel.TRACE, message, value);
    }

    /**
     * Logs a value at DEBUG level and returns true.
     *
     * @param message a text message
     * @param value a value
     * @return true
     */
    @Function(docs = """
            ```debug(TEXT message, value)```: Logs the ```value``` prepended with the ```message``` to the
            console at the DEBUG log level.
            This function is useful to add an additional statement line in a ```where``` block of a policy.
            As the function always returns ```true```, the rest of the policy evaluation is not affected.
            *Note:* If a statement above the logging statement evaluates to ```false```, the logger will
            not be triggered, as the evaluation of statements is lazy.

            **Example:**
            ```sapl
            policy "debug_authorization"
            permit
            where
              log.debug("Evaluating permissions", subject.permissions);
              subject.department == "engineering";
            ```
            """)
    public static Val debug(@Text Val message, Val value) {
        return logStatement(LogLevel.DEBUG, message, value);
    }

    /**
     * Logs a value at INFO level and returns true.
     *
     * @param message a text message
     * @param value a value
     * @return true
     */
    @Function(docs = """
            ```info(TEXT message, value)```: Logs the ```value``` prepended with the ```message``` to the
            console at the INFO log level.
            This function is useful to add an additional statement line in a ```where``` block of a policy.
            As the function always returns ```true```, the rest of the policy evaluation is not affected.
            *Note:* If a statement above the logging statement evaluates to ```false```, the logger will
            not be triggered, as the evaluation of statements is lazy.

            **Example:**
            ```sapl
            policy "audit_policy_execution"
            permit
            where
              log.info("Transaction amount", action.amount);
              subject.approvalLimit >= action.amount;
            ```
            """)
    public static Val info(@Text Val message, Val value) {
        return logStatement(LogLevel.INFO, message, value);
    }

    /**
     * Logs a value at WARN level and returns true.
     *
     * @param message a text message
     * @param value a value
     * @return true
     */
    @Function(docs = """
            ```warn(TEXT message, value)```: Logs the ```value``` prepended with the ```message``` to the
            console at the WARN log level.
            This function is useful to add an additional statement line in a ```where``` block of a policy.
            As the function always returns ```true```, the rest of the policy evaluation is not affected.
            *Note:* If a statement above the logging statement evaluates to ```false```, the logger will
            not be triggered, as the evaluation of statements is lazy.

            **Example:**
            ```sapl
            policy "flag_unusual_access"
            permit
            where
              log.warn("Access outside business hours", time.now());
              subject.role in ["admin", "oncall"];
            ```
            """)
    public static Val warn(@Text Val message, Val value) {
        return logStatement(LogLevel.WARN, message, value);
    }

    /**
     * Logs a value at ERROR level and returns true.
     *
     * @param message a text message
     * @param value a value
     * @return true
     */
    @Function(docs = """
            ```error(TEXT message, value)```: Logs the ```value``` prepended with the ```message``` to the
            console at the ERROR log level.
            This function is useful to add an additional statement line in a ```where``` block of a policy.
            As the function always returns ```true```, the rest of the policy evaluation is not affected.
            *Note:* If a statement above the logging statement evaluates to ```false```, the logger will
            not be triggered, as the evaluation of statements is lazy.

            **Example:**
            ```sapl
            policy "log_critical_errors"
            permit
            where
              log.error("Critical system access", subject.userId);
              subject.clearanceLevel == "top-secret";
            ```
            """)
    public static Val error(@Text Val message, Val value) {
        return logStatement(LogLevel.ERROR, message, value);
    }

}
