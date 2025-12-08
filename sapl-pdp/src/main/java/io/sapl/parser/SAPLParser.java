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
package io.sapl.parser;

import io.sapl.grammar.sapl.SAPL;

import java.io.InputStream;

public interface SAPLParser {

    /**
     * Method which applies the SAPL parser to a String containing a SAPL document
     * and generates the matching parse-tree.
     *
     * @param saplDefinition a String containing a SAPL document
     * @return A parse tree of the document in case an error occurs during parsing.
     * This may be either a syntax error or an IO error.
     */
    SAPL parse(String saplDefinition);

    /**
     * Method which applies the SAPL parser to an InputStream containing a SAPL
     * document and generates the matching parse-tree.
     *
     * @param saplInputStream an InputStream containing a SAPL document
     * @return A parse tree of the document in case an error occurs during parsing.
     * This may be either a syntax error or an IO error.
     */
    SAPL parse(InputStream saplInputStream);

    /**
     * Method which applies the SAPL parser to a String containing a SAPL document
     * and generates the matching Document.
     *
     * @param saplDefinition a String containing a SAPL document
     * @return Document with the name of the document as Id
     */
    Document parseDocument(String saplDefinition);

    /**
     * Method which applies the SAPL parser to an InputStream containing a SAPL
     * document and generates the matching parse-tree.
     *
     * @param saplInputStream an InputStream containing a SAPL document
     * @return Document with the name of the document as Id
     */
    Document parseDocument(InputStream saplInputStream);

    /**
     * Method which applies the SAPL parser to a String containing a SAPL document
     * and generates the matching Document.
     *
     * @param id the document Id
     * @param saplDefinition a String containing a SAPL document
     * @return Document with the given Id
     */
    Document parseDocument(String id, String saplDefinition);

    /**
     * Method which applies the SAPL parser to an InputStream containing a SAPL
     * document and generates the matching parse-tree.
     *
     * @param id the document Id
     * @param saplInputStream an InputStream containing a SAPL document
     * @return Document with the given Id
     */
    Document parseDocument(String id, InputStream saplInputStream);

}
