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
package io.sapl.pdp.interceptors;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.Traced;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.TracedDecision;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.pdp.PolicyDecisionPointFactory;
import io.sapl.pdp.interceptors.ErrorReportGenerator.OutputFormat;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class ErrorReportGeneratorTests {

    @Test
    void a() throws IOException {
        var e = ParserUtil.expression("""                
                 false | 1 / 0
         
                     """);
        var r = e.evaluate().log().blockFirst();
        dumpErrors(r, true, OutputFormat.ANSI_TEXT);
    }

    @Test
    void runIt() throws IOException, InitializationException {
        var documents = List.of("""
                policy "some policy"
                permit true == false
                     | "X" != 1 / 0
                     | 123 != "123" where true;
                """);

        var pdp = PolicyDecisionPointFactory.fixedInRamPolicyDecisionPoint(documents,
                PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, Map.of());

        var authorizationSubscription = AuthorizationSubscription.of("willi", "eat", "ice cream");

        TracedDecision decision = pdp.decideTraced(authorizationSubscription).blockFirst();
        log.info("Result: {}", decision);
        log.info("-----------------------------");
        var trace  = decision.getTrace();
        var report = ReportBuilderUtil.reduceTraceToReport(trace);
        log.info("-----------------------------");
        log.info("{}", trace);
        multiLog(ReportTextRenderUtil.textReport(report, false, new ObjectMapper()));
        log.info("-----------------------------");
        dumpErrors(decision, true, OutputFormat.ANSI_TEXT);
//        dumpErrors(result, false, OutputFormat.ANSI_TEXT);
//        dumpErrors(result, true, OutputFormat.PLAIN_TEXT);
//        dumpErrors(result, false, OutputFormat.PLAIN_TEXT);
//        dumpErrors(result, true, OutputFormat.HTML);
//        dumpErrors(result, false, OutputFormat.HTML);
    }

    private void dumpErrors(Traced traced, boolean enumerateLines, OutputFormat format) {
        for (var error : traced.getErrorsFromTrace()) {
            multiLog(ErrorReportGenerator.errorReport(error, enumerateLines, format));
            log.info("-----------------------------");

        }
    }

    void multiLog(String s) {
        for (var line : s.split("\n")) {
            log.info(line);
        }
    }

}
