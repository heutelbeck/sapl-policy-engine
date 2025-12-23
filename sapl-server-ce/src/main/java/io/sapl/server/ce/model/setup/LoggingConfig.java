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
package io.sapl.server.ce.model.setup;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoggingConfig {
    static final String SAPL_LOGGING_PATH         = "logging.level.[io.sapl]";
    static final String SPRING_LOGGING_PATH       = "logging.level.[org.springframework]";
    static final String SAPL_SERVER_LOGGING_PATH  = "logging.level.[io.sapl.server.ce]";
    static final String PRINT_TRACE_PATH          = "io.sapl.pdp.embedded.print-trace";
    static final String PRINT_JSON_REPORT_PATH    = "io.sapl.pdp.embedded.print-json-report";
    static final String PRINT_TEXT_REPORT_PATH    = "io.sapl.pdp.embedded.print-text-report";
    static final String PRETTY_PRINT_REPORTS_PATH = "io.sapl.pdp.embedded.pretty-print-reports";

    private LoggingLevel saplLoggingLevel;
    private LoggingLevel springLoggingLevel;
    private LoggingLevel saplServerLoggingLevel;
    private boolean      printTrace         = false;
    private boolean      printJsonReport    = false;
    private boolean      printTextReport    = false;
    private boolean      prettyPrintReports = false;

    private boolean saved = false;
}
