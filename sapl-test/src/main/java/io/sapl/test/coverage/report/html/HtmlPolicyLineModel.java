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
package io.sapl.test.coverage.report.html;

/**
 * Model for a single line in the HTML policy coverage view.
 *
 * @param lineContent the source code text of the line
 * @param cssClass the CSS class for coverage highlighting
 * @param popoverContent optional tooltip content for partially covered lines
 */
record HtmlPolicyLineModel(String lineContent, String cssClass, String popoverContent) {}
