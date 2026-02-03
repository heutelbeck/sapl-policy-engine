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
(function (mod) {
    if (typeof exports == "object" && typeof module == "object")
        mod(require("codemirror/lib/codemirror"), require("codemirror/addon/mode/simple"));
    else if (typeof define == "function" && define.amd)
        define(["codemirror/lib/codemirror", "codemirror/addon/mode/simple"], mod);
    else
        mod(CodeMirror);
})(function (CodeMirror) {
    "use strict";

    CodeMirror.defineSimpleMode("xtext/sapl", {
        start: [
            // Comments
            { regex: /\/\/.*/, token: "comment" },
            { regex: /\/\*/, token: "comment", next: "comment" },

            // Strings
            { regex: /"(?:[^\\"]|\\.)*"/, token: "string" },

            // Numbers
            { regex: /-?\b\d+(\.\d+)?([eE][+-]?\d+)?\b/, token: "number" },

            // Entitlements (decision types)
            { regex: /\b(permit|deny)\b/, token: "keyword" },

            // Algorithms (combining algorithms)
            { regex: /\b(first-applicable|only-one-applicable|deny-overrides|permit-overrides|deny-unless-permit|permit-unless-deny)\b/, token: "builtin" },

            // Authorization subscription variables
            { regex: /\b(subject|action|resource|environment)\b/, token: "variable-2" },

            // Keywords
            { regex: /\b(policy|set|for|where|var|as|import|schema|enforced|each|advice|obligation|transform|in)\b/, token: "keyword" },

            // Constants
            { regex: /\b(true|false|null|undefined)\b/, token: "atom" },

            // Attributes (stream access)
            { regex: /\|?<[a-zA-Z_][a-zA-Z0-9_]*(\.[a-zA-Z_][a-zA-Z0-9_]*)*(\([^)]*\))?(\[[^\]]*])?>/, token: "attribute" },

            // Functions (followed by parenthesis)
            { regex: /\b[a-zA-Z_][a-zA-Z0-9_]*(\.[a-zA-Z_][a-zA-Z0-9_]*)*(?=\s*\()/, token: "def" },

            // Operators
            { regex: /&&|\|\||!/, token: "operator" },
            { regex: /==|!=|<=|>=|<|>|=~/, token: "operator" },
            { regex: /[+\-*\/%^&|]/, token: "operator" },
            { regex: /\|-/, token: "operator" },
            { regex: /::/, token: "operator" },
            { regex: /@/, token: "operator" },

            // Punctuation
            { regex: /[\[\]{}().,;:]/, token: "punctuation" },

            // Identifiers
            { regex: /[a-zA-Z_][a-zA-Z0-9_]*/, token: "variable" }
        ],
        comment: [
            { regex: /.*?\*\//, token: "comment", next: "start" },
            { regex: /.*/, token: "comment" }
        ],
        meta: {
            dontIndentStates: ["comment"],
            lineComment: "//"
        }
    });

    CodeMirror.defineMIME("text/x-sapl", "xtext/sapl");
});
