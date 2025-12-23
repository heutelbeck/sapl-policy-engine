/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import io.sapl.grammar.antlr.SAPLParser.SaplContext;

import java.io.InputStream;

/**
 * Parser interface for SAPL documents.
 * Uses the ANTLR-based parser to generate parse trees.
 */
public interface SAPLParser {

    /**
     * Parses a SAPL document from a String.
     *
     * @param saplDefinition a String containing a SAPL document
     * @return the ANTLR parse tree of the document
     * @throws SaplParserException if parsing fails
     */
    SaplContext parse(String saplDefinition);

    /**
     * Parses a SAPL document from an InputStream.
     *
     * @param saplInputStream an InputStream containing a SAPL document
     * @return the ANTLR parse tree of the document
     * @throws SaplParserException if parsing fails
     */
    SaplContext parse(InputStream saplInputStream);

    /**
     * Parses a SAPL document and returns a Document wrapper with validation info.
     *
     * @param saplDefinition a String containing a SAPL document
     * @return Document with the parse tree and validation status
     */
    Document parseDocument(String saplDefinition);

    /**
     * Parses a SAPL document from an InputStream and returns a Document wrapper.
     *
     * @param saplInputStream an InputStream containing a SAPL document
     * @return Document with the parse tree and validation status
     */
    Document parseDocument(InputStream saplInputStream);

    /**
     * Parses a SAPL document with a specific document ID.
     *
     * @param id the document ID
     * @param saplDefinition a String containing a SAPL document
     * @return Document with the given ID
     */
    Document parseDocument(String id, String saplDefinition);

    /**
     * Parses a SAPL document from an InputStream with a specific document ID.
     *
     * @param id the document ID
     * @param saplInputStream an InputStream containing a SAPL document
     * @return Document with the given ID
     */
    Document parseDocument(String id, InputStream saplInputStream);

}
