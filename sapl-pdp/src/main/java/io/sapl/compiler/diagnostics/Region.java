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
package io.sapl.compiler.diagnostics;

/**
 * A region of a SAPL document. A region that starts and ends at the same
 * position marks a place between two characters.
 * <p>
 * The offsets address the text of the document as Java holds it. Lines and
 * columns are counted as all implementations of SAPL report them: a line ends
 * with a line feed, with a carriage return, or with a carriage return and a
 * line feed, and a column counts Unicode code points.
 *
 * @param start the offset of the first character of the region, in UTF-16 code
 * units, counted from 0
 * @param end the offset after the last character of the region, in UTF-16 code
 * units
 * @param line the line of the first character, counted from 1
 * @param column the column of the first character, counted from 1
 * @param endLine the line of the position after the last character
 * @param endColumn the column of the position after the last character
 */
public record Region(int start, int end, int line, int column, int endLine, int endColumn) {}
