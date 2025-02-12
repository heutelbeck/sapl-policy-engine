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
package io.sapl.interpreter;

import java.io.InputStream;
import java.util.Map;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.prp.Document;
import reactor.core.publisher.Flux;

public interface SAPLInterpreter {

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

    /**
     * Convenience method for unit tests which evaluates a String representing a
     * SAPL document (containing a policy set or policy) against an authorization
     * subscription object within a given attribute context and function context and
     * returns a {@link Flux} of {@link AuthorizationDecision} objects.
     *
     * @param authzSubscription the authorization subscription object
     * @param saplDocumentSource the String representing the SAPL document
     * @param attributeContext the PDP's AttributeContext
     * @param functionContext the PDP's FunctionContext
     * @param environmentVariables map containing the PDP's environment variables
     * @return A {@link Flux} of {@link AuthorizationDecision} objects.
     */
    Flux<AuthorizationDecision> evaluate(AuthorizationSubscription authzSubscription, String saplDocumentSource,
            AttributeContext attributeContext, FunctionContext functionContext, Map<String, Val> environmentVariables);

}
