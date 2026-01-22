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
lexer grammar SAPLTestLexer;

// Structure keywords
REQUIREMENT : 'requirement';
SCENARIO    : 'scenario';
GIVEN       : 'given';
WHEN        : 'when';
THEN        : 'then';
EXPECT      : 'expect';
VERIFY      : 'verify';

// Authorization keywords
SUBJECT     : 'subject';
ACTION      : 'action';
RESOURCE    : 'resource';
ENVIRONMENT : 'environment';
ATTEMPTS    : 'attempts';
ON          : 'on';

// Decision keywords
PERMIT        : 'permit';
DENY          : 'deny';
INDETERMINATE : 'indeterminate';
NOT_APPLICABLE : 'not-applicable';
DECISION      : 'decision';

// Obligation/Advice keywords
OBLIGATION  : 'obligation';
ADVICE      : 'advice';
WITH        : 'with';
OBLIGATIONS : 'obligations';

// Mock keywords
FUNCTION      : 'function';
ATTRIBUTE     : 'attribute';
MAPS          : 'maps';
TO            : 'to';
EMITS         : 'emits';
STREAM        : 'stream';
OF            : 'of';
IS            : 'is';
CALLED        : 'called';
ERROR         : 'error';

// PDP configuration keywords
VARIABLES : 'variables';

// Combining algorithm components (same as sapl-parser)
KW_OR     : 'or';
ERRORS    : 'errors';
ABSTAIN   : 'abstain';
PROPAGATE : 'propagate';
FIRST     : 'first';
PRIORITY  : 'priority';
STRICT    : 'strict';
UNANIMOUS : 'unanimous';
UNIQUE    : 'unique';

// Document keywords
DOCUMENT  : 'document';
DOCUMENTS : 'documents';

// Matcher keywords - JSON types
NULL_KEYWORD    : 'null';
TEXT            : 'text';
NUMBER_KEYWORD  : 'number';
BOOLEAN_KEYWORD : 'boolean';
ARRAY           : 'array';
OBJECT          : 'object';
WHERE           : 'where';
MATCHING        : 'matching';
ANY             : 'any';
EQUALS          : 'equals';
CONTAINING      : 'containing';
KEY             : 'key';
VALUE           : 'value';

// String matcher keywords
BLANK               : 'blank';
EMPTY               : 'empty';
NULL_OR_EMPTY       : 'null-or-empty';
NULL_OR_BLANK       : 'null-or-blank';
EQUAL               : 'equal';
COMPRESSED          : 'compressed';
WHITESPACE          : 'whitespace';
CASE_INSENSITIVE    : 'case-insensitive';
REGEX               : 'regex';
STARTING            : 'starting';
ENDING              : 'ending';
LENGTH              : 'length';
ORDER               : 'order';

// Expect step keywords
NEXT  : 'next';
ONCE  : 'once';
TIMES : 'times';

// Boolean literals
TRUE  : 'true';
FALSE : 'false';

// Special value
UNDEFINED : 'undefined';

// Punctuation
LBRACE   : '{';
RBRACE   : '}';
LBRACKET : '[';
RBRACKET : ']';
LPAREN   : '(';
RPAREN   : ')';
COMMA    : ',';
COLON    : ':';
SEMI     : ';';
DASH     : '-';
DOT      : '.';
LT       : '<';
GT       : '>';
AND      : 'and';
IN       : 'in';

// Literals
NUMBER
    : [+\-]? ('0' | [1-9] DIGIT*) ('.' DIGIT+)? ([eE] [+\-]? DIGIT+)?
    ;

STRING
    : '"' (ESC | ~["\\])* '"'
    ;

// Identifiers
ID
    : [a-zA-Z_$] [a-zA-Z_$0-9]*
    ;

// Whitespace and Comments (hidden)
WS
    : [ \t\r\n]+ -> channel(HIDDEN)
    ;

ML_COMMENT
    : '/*' .*? '*/' -> channel(HIDDEN)
    ;

SL_COMMENT
    : '//' ~[\r\n]* -> channel(HIDDEN)
    ;

// Fragments
fragment DIGIT
    : [0-9]
    ;

fragment ESC
    : '\\' (["\\/bfnrt] | UNICODE)
    ;

fragment UNICODE
    : 'u' HEX HEX HEX HEX
    ;

fragment HEX
    : [0-9a-fA-F]
    ;
