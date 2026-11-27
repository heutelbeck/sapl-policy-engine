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
package io.sapl.conformance;

import io.sapl.compiler.document.DocumentCompiler;
import io.sapl.conformance.ConformanceCorpus.Case;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.Optional;

/**
 * The verdicts of the parser corpus. The heading of a case starts with the
 * verdict, "valid" or "invalid", followed by a colon.
 */
@UtilityClass
public class ParserVerdicts {

    private static final String INVALID = "invalid: ";
    private static final String VALID   = "valid: ";

    private static final String ERROR_HOLDS_A_REPORT = "The case at %s holds a report. A case of the parser corpus holds a verdict and a document, and no '---'.";
    private static final String ERROR_NO_VERDICT     = "The heading of the case at %s does not start with 'valid: ' or 'invalid: '.";

    /**
     * @param parserCase a case of the parser corpus
     * @return whether the case states that its document is valid
     * @throws IllegalStateException if the heading of the case holds no verdict
     * or the case holds a report
     */
    public static boolean isStatedValid(Case parserCase) {
        requireNoReport(parserCase);
        if (parserCase.heading().startsWith(VALID)) {
            return true;
        }
        if (parserCase.heading().startsWith(INVALID)) {
            return false;
        }
        throw new IllegalStateException(ERROR_NO_VERDICT.formatted(parserCase.origin()));
    }

    /**
     * @param document a SAPL document
     * @return what this engine reports if it refuses the document, empty if it
     * accepts the document
     */
    public static Optional<String> refusalOf(String document) {
        val parsed = DocumentCompiler.parseDocument(document);
        return parsed.isInvalid() ? Optional.of(parsed.errors()) : Optional.empty();
    }

    /**
     * @param parserCase a case of the parser corpus, with or without a verdict
     * in its heading
     * @return the case with the verdict of this engine in its heading
     * @throws IllegalStateException if the case holds a report
     */
    public static Case withVerdict(Case parserCase) {
        requireNoReport(parserCase);
        val verdict = refusalOf(parserCase.document()).isEmpty() ? VALID : INVALID;
        return parserCase.withHeading(verdict + withoutVerdict(parserCase.heading()));
    }

    private static String withoutVerdict(String heading) {
        if (heading.startsWith(VALID)) {
            return heading.substring(VALID.length());
        }
        if (heading.startsWith(INVALID)) {
            return heading.substring(INVALID.length());
        }
        return heading;
    }

    private static void requireNoReport(Case parserCase) {
        if (!parserCase.expectation().isEmpty()) {
            throw new IllegalStateException(ERROR_HOLDS_A_REPORT.formatted(parserCase.origin()));
        }
    }
}
