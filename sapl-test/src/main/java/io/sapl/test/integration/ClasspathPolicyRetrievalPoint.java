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
package io.sapl.test.integration;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.SAPLInterpreter;
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
public class ClasspathPolicyRetrievalPoint implements PolicyRetrievalPoint {

    private static final String POLICIES_FILE_GLOB_PATTERN = "*.sapl";

    private final Map<String, SAPL> documents;

    ClasspathPolicyRetrievalPoint(Path path, SAPLInterpreter interpreter) {
        this.documents = readPoliciesFromDirectory(path.toString(), interpreter);
    }

    ClasspathPolicyRetrievalPoint(final Collection<String> saplDocumentNames, final SAPLInterpreter interpreter) {
        this.documents = readPoliciesFromSaplDocumentNames(saplDocumentNames, interpreter);
    }

    private Map<String, SAPL> readPoliciesFromDirectory(String path, SAPLInterpreter interpreter) {
        Map<String, SAPL> documentsByName     = new HashMap<>();
        Path              policyDirectoryPath = ClasspathHelper.findPathOnClasspath(getClass().getClassLoader(), path);
        log.debug("reading policies from directory {}", policyDirectoryPath);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(policyDirectoryPath, POLICIES_FILE_GLOB_PATTERN)) {
            for (Path filePath : stream) {
                log.info("loading policy: {}", filePath.toAbsolutePath());
                SAPL sapl = interpreter.parse(Files.newInputStream(filePath));
                documentsByName.put(sapl.getPolicyElement().getSaplName(), sapl);
            }
        } catch (IOException | PolicyEvaluationException e) {
            throw Exceptions.propagate(e);
        }
        return documentsByName;
    }

    private Map<String, SAPL> readPoliciesFromSaplDocumentNames(final Collection<String> saplDocumentNames,
            final SAPLInterpreter interpreter) {
        if (saplDocumentNames == null || saplDocumentNames.isEmpty()) {
            return Collections.emptyMap();
        }

        if (saplDocumentNames.stream()
                .anyMatch(saplDocumentName -> saplDocumentName == null || saplDocumentName.isEmpty())) {
            throw new SaplTestException("Encountered invalid policy name");
        }

        return saplDocumentNames.stream()
                .map(saplDocumentName -> DocumentHelper.readSaplDocument(saplDocumentName, interpreter))
                .collect(Collectors.toMap(sapl -> sapl.getPolicyElement().getSaplName(), Function.identity(),
                        (oldKey, newKey) -> newKey));
    }

    @Override
    public Flux<PolicyRetrievalResult> retrievePolicies() {
        var retrieval = Mono.just(new PolicyRetrievalResult());
        for (SAPL document : documents.values()) {
            retrieval = retrieval.flatMap(retrievalResult -> document.matches().map(match -> {
                if (match.isError()) {
                    return retrievalResult.withError();
                }
                if (match.getBoolean()) {
                    return retrievalResult.withMatch(document.getPolicyElement().getSaplName(), document, Val.TRUE);
                }
                return retrievalResult;
            }));
        }

        return Flux.from(retrieval);
    }

}
