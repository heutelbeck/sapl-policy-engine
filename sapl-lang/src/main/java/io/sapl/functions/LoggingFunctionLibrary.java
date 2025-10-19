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
 * This function library can be used in policies to print values to the PDPs
 * console for debugging and testing.
 */
@Slf4j
@UtilityClass
@FunctionLibrary(name = LoggingFunctionLibrary.NAME, description = LoggingFunctionLibrary.DESCRIPTION)
public class LoggingFunctionLibrary {

    public static final String  NAME        = "log";
    public static final String  DESCRIPTION = "Utility functions for dumping data from policy evaluation on the PDP console for debugging of policies.";
    private static final String TEMPLATE    = "[SAPL] {} {}";

    /**
     * Returns the original message on log level INFO followed by the inspected
     * value.
     *
     * @param message a text massage.
     * @param value a value
     * @return the value
     */
    @Function(docs = """
            ```infoSpy(TEXT message, value)```: Logs the provided ```value```, prepended with the ```message```, to the
            console at the INFO log level.
            The function behaves like the identity funtion, returning ```value``` unchanged.
            This allows it to be used to wrap any value in a SAPL expression without changing the overall structure of the policy.

            **Example:**
            ```sapl
            policy "logging"
            permit
            where
              log.infoSpy(subject.name) == "testUser";
            ```
            """)
    public static Val infoSpy(@Text Val message, Val value) {
        log.info(TEMPLATE, message.getText(), value);
        return value;
    }

    /**
     * Returns the original message on log level ERROR followed by the inspected
     * value.
     *
     * @param message a text massage.
     * @param value a value
     * @return the value
     */
    @Function(docs = """
            ```errorSpy(TEXT message, value)```: Logs the ```value``` prepended with the ```message``` to the
            console at the ERROR log level.
            The function behaves like the identity funtion, returning ```value``` unchanged.
            This allows it to be used to wrap any value in a SAPL expression without changing the overall structure of the policy.

            **Example:**
            ```sapl
            policy "logging"
            permit
            where
              log.errorSpy(subject.name) == "testUser";
            ```
            """)
    public static Val errorSpy(@Text Val message, Val value) {
        log.error(TEMPLATE, message.getText(), value);
        return value;
    }

    /**
     * Returns the original message on log level TRACE followed by the inspected
     * value.
     *
     * @param message a text massage.
     * @param value a value
     * @return the value
     */
    @Function(docs = """
            ```traceSpy(TEXT message, value)```: Logs the ```value``` prepended with the ```message``` to the
            console at the TRACE log level.
            The function behaves like the identity funtion, returning ```value``` unchanged.
            This allows it to be used to wrap any value in a SAPL expression without changing the overall structure of the policy.

            **Example:**
            ```sapl
            policy "logging"
            permit
            where
              log.traceSpy(subject.name) == "testUser";
            ```
            """)
    public static Val traceSpy(@Text Val message, Val value) {
        log.trace(TEMPLATE, message.getText(), value);
        return value;
    }

    /**
     * Returns the original message on log level WARN followed by the inspected
     * value.
     *
     * @param message a text massage.
     * @param value a value
     * @return the value
     */
    @Function(docs = """
            ```warnSpy(TEXT message, value)```: Logs the ```value``` prepended with the ```message``` to the
            console at the WARN log level.
            The function behaves like the identity funtion, returning ```value``` unchanged.
            This allows it to be used to wrap any value in a SAPL expression without changing the overall structure of the policy.

            **Example:**
            ```sapl
            policy "logging"
            permit
            where
              log.warnSpy(subject.name) == "testUser";
            ```
            """)
    public static Val warnSpy(@Text Val message, Val value) {
        log.warn(TEMPLATE, message.getText(), value);
        return value;
    }

    /**
     * Returns the original message on log level DEBUG followed by the inspected
     * value.
     *
     * @param message a text massage.
     * @param value a value
     * @return the value
     */
    @Function(docs = """
            ```debugSpy(TEXT message, value)```: Logs the ```value``` prepended with the ```message``` to the
            console at the DEBUG log level.
            The function behaves like the identity funtion, returning ```value``` unchanged.
            This allows it to be used to wrap any value in a SAPL expression without changing the overall structure of the policy.

            **Example:**
            ```sapl
            policy "logging"
            permit
            where
              log.debugSpy(subject.name) == "testUser";
            ```
            """)
    public static Val debugSpy(@Text Val message, Val value) {
        log.debug(TEMPLATE, message.getText(), value);
        return value;
    }

    /**
     * Returns a constant TRUE and prints the message on log level INFO followed by
     * the inspected value.
     *
     * @param message a text massage.
     * @param value a value
     * @return the value
     */
    @Function(docs = """
            ```info(TEXT message, value)```: Logs the ```value``` prepended with the ```message``` to the
            console at the DEBUG log level.
            It is useful to add an additional statement line in a ```where``` block of a policy.
            As the function always returns ```true```, the rest of the policy evaluation is not affected.
            *Note:* If a statement above the logging statement evaluates to ```false```, the logger will
            not be triggered, as the evaluation of statements is lazy.

            **Example:**
            ```sapl
            policy "logging"
            permit
            where
              log.info(action.amount);
              subject.name == "testUser";
            ```
            """)
    public static Val info(@Text Val message, Val value) {
        log.info(TEMPLATE, message.getText(), value);
        return Val.TRUE;
    }

    /**
     * Returns a constant TRUE and prints the message on log level ERROR followed by
     * the inspected value.
     *
     * @param message a text massage.
     * @param value a value
     * @return the value
     */
    @Function(docs = """
            ```error(TEXT message, value)```: Logs the ```value``` prepended with the ```message``` to the
            console at the ERROR log level.
            This function is useful to add an additional statement line in a where block of a policy.
            As the function always returns ```true```, the rest of the policy evaluation is not affected.
            *Note:* If a statement above the logging statement evaluates to ```false```, the logger will
            not be triggered, as the evaluation of statements is lazy.

            **Example:**
            ```sapl
            policy "logging"
            permit
            where
              log.error(action.amount);
              subject.name == "testUser";
            ```
            """)
    public static Val error(@Text Val message, Val value) {
        log.error(TEMPLATE, message.getText(), value);
        return Val.TRUE;
    }

    /**
     * Returns a constant TRUE and prints the message on log level TRACE followed by
     * the inspected value.
     *
     * @param message a text massage.
     * @param value a value
     * @return the value
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
            policy "logging"
            permit
            where
              log.trace(action.amount);
              subject.name == "testUser";
            ```
            """)
    public static Val trace(@Text Val message, Val value) {
        log.trace(TEMPLATE, message.getText(), value);
        return Val.TRUE;
    }

    /**
     * Returns a constant TRUE and prints the message on log level WARN followed by
     * the inspected value.
     *
     * @param message a text massage.
     * @param value a value
     * @return the value
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
            policy "logging"
            permit
            where
              log.warn(action.amount);
              subject.name == "testUser";
            ```
            """)
    public static Val warn(@Text Val message, Val value) {
        log.warn(TEMPLATE, message.getText(), value);
        return Val.TRUE;
    }

    /**
     * Returns a constant TRUE and prints the message on log level DEBUG followed by
     * the inspected value.
     *
     * @param message a text massage.
     * @param value a value
     * @return the value
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
            policy "logging"
            permit
            where
              log.debug(action.amount);
              subject.name == "testUser";
            ```
            """)
    public static Val debug(@Text Val message, Val value) {
        log.debug(TEMPLATE, message.getText(), value);
        return Val.TRUE;
    }

}
