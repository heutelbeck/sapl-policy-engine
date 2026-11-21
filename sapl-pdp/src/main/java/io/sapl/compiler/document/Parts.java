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
package io.sapl.compiler.document;

import io.sapl.api.model.DocumentLines;
import io.sapl.compiler.diagnostics.Diagnostic;
import io.sapl.grammar.antlr.SAPLLexer;
import lombok.val;
import org.antlr.v4.runtime.Token;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * The parts of a document that are checked on their own: an import, a schema,
 * the header of a policy, a statement, an obligation, an advice or a
 * transformation. A part starts after each {@code ;} and at each keyword that
 * starts a part.
 * <p>
 * One mistake often makes the parser stumble again further on in the same
 * part. What it finds there would only repeat the first problem in other
 * words. So a part gets at most one problem from the parser, and none if it has
 * a problem already.
 */
final class Parts {

    private final int[]        starts;
    private final Set<Integer> withAProblem = new HashSet<>();

    private Parts(int[] starts) {
        this.starts = starts;
    }

    /**
     * @param read the tokens the parser reads, in the order of the document
     * @param lines the lines of the document
     * @return the parts of the document
     */
    static Parts of(List<? extends Token> read, DocumentLines lines) {
        val starts = IntStream.builder();
        for (val token : read) {
            if (token.getType() == SAPLLexer.SEMI) {
                starts.add(Tokens.endOf(token, lines));
            } else if (Tokens.startsAPart(token.getType())) {
                starts.add(Tokens.startOf(token, lines));
            }
        }
        return new Parts(starts.build().toArray());
    }

    /**
     * @param found problems of the document
     * @return the same parts, of which those that hold a problem found have a
     * problem, and no other
     */
    Parts withProblemsOf(List<Diagnostic> found) {
        val parts = new Parts(starts);
        found.forEach(parts::takes);
        return parts;
    }

    /**
     * @param offset a place of the document
     * @return the number of the part the place lies in
     */
    int numberAt(int offset) {
        var low  = 0;
        var high = starts.length;
        while (low < high) {
            val middle = (low + high) >>> 1;
            if (starts[middle] <= offset) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    /**
     * Notes a problem in its part.
     *
     * @param problem a problem of the document
     * @return whether it is the first problem of its part
     */
    boolean takes(Diagnostic problem) {
        return withAProblem.add(numberAt(problem.region().start()));
    }

}
