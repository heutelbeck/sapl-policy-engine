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
package io.sapl.test.integration;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.Maps;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.prp.Document;
import io.sapl.prp.DocumentMatch;
import io.sapl.prp.PolicyRetrievalPoint;
import io.sapl.prp.PolicyRetrievalResult;
import io.sapl.test.SaplTestException;
import io.sapl.test.utils.ClasspathHelper;
import io.sapl.test.utils.DocumentHelper;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
public final class ClasspathPolicyRetrievalPoint implements PolicyRetrievalPoint {

    private static final String POLICIES_FILE_GLOB_PATTERN = "*.sapl";

    private final Map<String, Document> documents;

    private boolean consistent = true;

    ClasspathPolicyRetrievalPoint(Path path, SAPLInterpreter interpreter) {
        this.documents = readPoliciesFromDirectory(path.toString(), interpreter);
    }

    ClasspathPolicyRetrievalPoint(final Collection<String> saplDocumentNames, final SAPLInterpreter interpreter) {
        this.documents = readPoliciesFromSaplDocumentNames(saplDocumentNames, interpreter);
    }

    private Map<String, Document> readPoliciesFromDirectory(String path, SAPLInterpreter interpreter) {
        Map<String, Document> documentsByName     = new HashMap<>();
        Path                  policyDirectoryPath = ClasspathHelper.findPathOnClasspath(getClass().getClassLoader(),
                path);
        log.debug("reading policies from directory {}", policyDirectoryPath);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(policyDirectoryPath, POLICIES_FILE_GLOB_PATTERN)) {
            for (Path filePath : stream) {
                log.info("loading policy: {}", filePath.toAbsolutePath());
                final var document = interpreter.parseDocument(Files.newInputStream(filePath));
                if (document.isInvalid()) {
                    throw new PolicyEvaluationException("Detected error in document: " + document.errorMessage());
                }
                final var previous = documentsByName.put(document.name(), document);
                if (previous != null || document.isInvalid()) {
                    this.consistent = false;
                }
            }
        } catch (IOException | PolicyEvaluationException e) {
            throw Exceptions.propagate(e);
        }
        return documentsByName;
    }

    private Map<String, Document> readPoliciesFromSaplDocumentNames(final Collection<String> saplDocumentNames,
            final SAPLInterpreter interpreter) {
        if (saplDocumentNames == null || saplDocumentNames.isEmpty()) {
            return Collections.emptyMap();
        }

        if (saplDocumentNames.stream()
                .anyMatch(saplDocumentName -> saplDocumentName == null || saplDocumentName.isEmpty())) {
            throw new SaplTestException("Encountered invalid policy name");
        }

        Map<String, Document> documentsByName = Maps.newHashMapWithExpectedSize(saplDocumentNames.size());
        for (var saplDocumentName : saplDocumentNames) {
            final var document = DocumentHelper.readSaplDocument(saplDocumentName, interpreter);
            if (document.isInvalid()) {
                throw new PolicyEvaluationException(
                        "'" + saplDocumentName + "' is invalid. Error: " + document.errorMessage());
            }
            final var previous = documentsByName.put(document.name(), document);
            if (previous != null || document.isInvalid()) {
                this.consistent = false;
            }
        }
        return documentsByName;
    }

    @Override
    public Mono<PolicyRetrievalResult> retrievePolicies() {
        final var documentMatches = Flux
                .merge(documents.values().stream()
                        .map(document -> document.sapl().matches()
                                .map(targetExpressionResult -> new DocumentMatch(document, targetExpressionResult)))
                        .toList());
        return documentMatches.reduce(new PolicyRetrievalResult(), PolicyRetrievalResult::withMatch);
    }

    @Override
    public Collection<Document> allDocuments() {
        return documents.values();
    }

    @Override
    public boolean isConsistent() {
        return consistent;
    }

}
