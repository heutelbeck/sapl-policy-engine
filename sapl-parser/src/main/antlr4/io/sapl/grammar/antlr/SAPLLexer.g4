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
FOR         : 'for';
POLICY      : 'policy';
PERMIT      : 'permit';
DENY        : 'deny';
WHERE       : 'where';
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
EACH        : 'each';

// Reserved Identifiers (also valid as identifiers)
SUBJECT     : 'subject';
ACTION      : 'action';
RESOURCE    : 'resource';
ENVIRONMENT : 'environment';

// Combining Algorithms
DENY_OVERRIDES      : 'deny-overrides';
PERMIT_OVERRIDES    : 'permit-overrides';
FIRST_APPLICABLE    : 'first-applicable';
ONLY_ONE_APPLICABLE : 'only-one-applicable';
DENY_UNLESS_PERMIT  : 'deny-unless-permit';
PERMIT_UNLESS_DENY  : 'permit-unless-deny';

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

STRING
    : '"' (ESC | ~["\\])* '"'
    ;

// Identifiers
ID
    : '^'? [a-zA-Z_$] [a-zA-Z_$0-9]*
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
