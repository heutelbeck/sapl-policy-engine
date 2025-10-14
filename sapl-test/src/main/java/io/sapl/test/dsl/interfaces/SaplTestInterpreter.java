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
package io.sapl.test.dsl.interfaces;

import io.sapl.test.dsl.setup.TestDocument;
import io.sapl.test.grammar.sapltest.SAPLTest;

import java.io.InputStream;

/**
 * Allows to define a custom Interpreter to create a {@link SAPLTest} instance
 * from a text input. Can be used in a class derived from
 * {@link io.sapl.test.dsl.setup.BaseTestAdapter} to customize the interpreter
 * logic.
 */
public interface SaplTestInterpreter {
    SAPLTest loadAsResource(InputStream inputStream);

    SAPLTest loadAsResource(String input);

    /**
     * Method which applies the SAPLTest parser to a String containing a SAPLTest
     * document and generates the matching TestDocument.
     *
     * @param source a String containing a SAPL document
     * @return TestDocument parsed test document
     */
    TestDocument parseDocument(String source);

}
