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
lexer grammar SAPLLexer;

// Keywords
IMPORT      : 'import';
AS          : 'as';
SET         : 'set';
POLICY      : 'policy';
PERMIT      : 'permit';
DENY        : 'deny';
SUSPEND     : 'suspend';
VAR         : 'var';
SCHEMA      : 'schema';
ENFORCED    : 'enforced';
OBLIGATION  : 'obligation';
ADVICE      : 'advice';
TRANSFORM   : 'transform';
TRUE        : 'true';
FALSE       : 'false';
NULL        : 'null';
UNDEFINED   : 'undefined';
IN          : 'in';
HAS         : 'has';
ANY         : 'any';
ALL         : 'all';
EACH        : 'each';
FOR         : 'for';

// Reserved Identifiers (also valid as identifiers)
SUBJECT     : 'subject';
ACTION      : 'action';
RESOURCE    : 'resource';
ENVIRONMENT : 'environment';

// Combining Algorithm Components
KW_OR               : 'or';
ERRORS              : 'errors';
ABSTAIN             : 'abstain';
PROPAGATE           : 'propagate';
FIRST               : 'first';
PRIORITY            : 'priority';
STRICT              : 'strict';
UNANIMOUS           : 'unanimous';
UNIQUE              : 'unique';

// Operators
FILTER      : '|-';
SUBTEMPLATE : '::';
OR          : '||';
AND         : '&&';
BITOR       : '|';
BITXOR      : '^';
BITAND      : '&';
EQ          : '==';
NEQ         : '!=';
REGEX       : '=~';
LT          : '<';
LE          : '<=';
GT          : '>';
GE          : '>=';
PLUS        : '+';
MINUS       : '-';
STAR        : '*';
SLASH       : '/';
PERCENT     : '%';
NOT         : '!';
AT          : '@';
HASH        : '#';
DOT         : '.';
DOTDOT      : '..';
COMMA       : ',';
COLON       : ':';
SEMI        : ';';
ASSIGN      : '=';
LPAREN      : '(';
RPAREN      : ')';
LBRACKET    : '[';
RBRACKET    : ']';
LBRACE      : '{';
RBRACE      : '}';
PIPE_LT     : '|<';
QUESTION    : '?';

// Literals
NUMBER
    : ('0' | [1-9] DIGIT*) ([eE] [+\-]? DIGIT+)?
    | ('0' | [1-9] DIGIT*) '.' DIGIT+ ([eE] [+\-]? DIGIT+)?
    ;

// A string ends on the line it starts on. A line break in a text is written as an escape.
STRING
    : '"' (ESC | ~["\\\r\n])* '"'
    | '\'' (ESC | ~['\\\r\n])* '\''
    ;

// Identifiers
ID
    : [a-zA-Z_$] [a-zA-Z_$0-9]*
    ;

// Backtick-escaped identifier: a literal name even when it spells a keyword.
BACKTICK_ID
    : '`' [a-zA-Z_$] [a-zA-Z_$0-9]* '`'
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

// Mistakes. The parser accepts none of these tokens, so a document that holds
// one is refused. They let the lexer read every text to its end, and they keep
// what was meant together, so that the mistake can be named as a whole.

// A string with an escape sequence that does not exist.
MALFORMED_STRING
    : '"' DOUBLE_QUOTED_TEXT '"'
    | '\'' SINGLE_QUOTED_TEXT '\''
    ;

// A string that is not closed on its line. It takes the rest of the line.
UNCLOSED_STRING
    : '"' DOUBLE_QUOTED_TEXT '\\'?
    | '\'' SINGLE_QUOTED_TEXT '\\'?
    ;

// A text that a typographic quotation mark opens. A typographic quotation mark
// on the same line closes it, or a plain one if there is none.
TYPOGRAPHIC_STRING
    : TYPOGRAPHIC_QUOTE (TYPOGRAPHIC_TEXT | '"')* TYPOGRAPHIC_QUOTE
    | TYPOGRAPHIC_QUOTE TYPOGRAPHIC_TEXT* '"'
    ;

// A block comment that is not closed. It takes the rest of the document.
UNCLOSED_COMMENT
    : '/*' (~'*' | '*'+ ~[*/])* '*'*
    ;

// A character that starts no token.
UNKNOWN_CHARACTER
    : .
    ;

// Fragments
fragment DIGIT
    : [0-9]
    ;

fragment ESC
    : '\\' (["'\\/bfnrt] | UNICODE)
    ;

fragment UNICODE
    : 'u' HEX HEX HEX HEX
    ;

fragment HEX
    : [0-9a-fA-F]
    ;

// The text of a string after its opening quotation mark, in which a backslash
// takes any character but a line break with it.
fragment DOUBLE_QUOTED_TEXT
    : ('\\' ~[\r\n] | ~["\\\r\n])*
    ;

fragment SINGLE_QUOTED_TEXT
    : ('\\' ~[\r\n] | ~['\\\r\n])*
    ;

// The typographic quotation marks: the double and single angle quotation marks,
// and the quotation marks from U+2018 to U+201F.
fragment TYPOGRAPHIC_QUOTE
    : [\u00AB\u00BB\u2018-\u201F\u2039\u203A]
    ;

// A character of a text in typographic quotation marks other than a plain
// quotation mark. A set cannot refer to TYPOGRAPHIC_QUOTE, so it names the
// typographic quotation marks again.
fragment TYPOGRAPHIC_TEXT
    : ~[\r\n"\u00AB\u00BB\u2018-\u201F\u2039\u203A]
    ;
