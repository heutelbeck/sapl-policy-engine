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
parser grammar SAPLTestParser;

options { tokenVocab = SAPLTestLexer; }

// Entry point
saplTest
    : requirement+ EOF
    ;

// Top-level structure
requirement
    : REQUIREMENT name=STRING LBRACE given? scenario+ RBRACE
    ;

scenario
    : SCENARIO name=STRING given? whenStep expectOrThenExpect verifyBlock? SEMI
    ;

// Expectation sequence: initial expect, then optional (then -> expect)*
expectOrThenExpect
    : expectation (thenExpect)*
    ;

thenExpect
    : thenBlock expectation
    ;

thenBlock
    : THEN (DASH thenStep)+
    ;

thenStep
    : ATTRIBUTE mockId=STRING EMITS emittedValue=valueOrError    # attributeEmitStep
    ;

// Given block - completely optional
// If omitted: uses all documents with security default algorithm
given
    : GIVEN givenItem+
    ;

givenItem
    : DASH documentSpecification  # documentGivenItem
    | DASH combiningAlgorithm     # algorithmGivenItem
    | DASH variablesDefinition    # variablesGivenItem
    | DASH mockDefinition         # mockGivenItem
    ;

// Document specification
// - document "X"             -> unit test (single document)
// - documents "X", "Y"       -> integration test (explicit subset)
// - (omitted)                -> integration test (all documents)
documentSpecification
    : DOCUMENT identifier=STRING                                    # singleDocument
    | DOCUMENTS identifiers+=STRING (COMMA identifiers+=STRING)*    # multipleDocuments
    ;

// Combining algorithm - same syntax as sapl-parser
// Example: first or abstain errors propagate
combiningAlgorithm
    : votingMode KW_OR defaultDecision (COMMA? ERRORS errorHandling)?
    ;

votingMode
    : FIRST            # first
    | PRIORITY DENY    # priorityDeny
    | PRIORITY PERMIT  # priorityPermit
    | UNANIMOUS STRICT # unanimousStrict
    | UNANIMOUS        # unanimous
    | UNIQUE           # unique
    ;

defaultDecision
    : DENY    # denyDefault
    | ABSTAIN # abstainDefault
    | PERMIT  # permitDefault
    ;

errorHandling
    : ABSTAIN   # abstainErrors
    | PROPAGATE # propagateErrors
    ;

// Variables definition - local test variables override security variables
variablesDefinition
    : VARIABLES variables=objectValue
    ;

// Mock definitions
// - function time.dayOfWeek() maps to "MONDAY"
// - function time.dayOfWeek(any) maps to "MONDAY"
// - attribute "mockId" <pip.attr> emits value
mockDefinition
    : FUNCTION functionFullName=functionName functionParameters?
      MAPS TO returnValue=valueOrError                                               # functionMock
    | ATTRIBUTE mockId=STRING attributeReference (EMITS initialValue=valueOrError)?  # attributeMock
    ;

// Dotted identifier for function names (e.g., time.dayOfWeek, filter.blacken)
functionName
    : parts+=testId (DOT parts+=testId)*
    ;

// Function parameters - matchers for arguments
functionParameters
    : LPAREN (matchers+=valMatcher (COMMA matchers+=valMatcher)*)? RPAREN
    ;

// Attribute reference following SAPL syntax
// - <pip.attr>                   -> environment attribute (no entity, no params)
// - <pip.attr("x")>              -> environment attribute with parameters
// - any.<pip.attr>               -> attribute with any entity matcher
// - {"id":1}.<pip.attr("x")>     -> attribute with specific entity and parameters
attributeReference
    : LT attributeFullName=attributeName attributeParameters? GT                         # environmentAttributeReference
    | entityMatcher=valMatcher DOT LT attributeFullName=attributeName attributeParameters? GT  # entityAttributeReference
    ;

// Dotted identifier for attribute names (e.g., pip.attr, user.location)
attributeName
    : parts+=testId (DOT parts+=testId)*
    ;

// Parameters are only needed when there are matchers to specify
attributeParameters
    : LPAREN matchers+=valMatcher (COMMA matchers+=valMatcher)* RPAREN
    ;

numericAmount
    : ONCE                # onceAmount
    | amount=NUMBER TIMES # multipleAmount
    ;

// Verify block - post-test assertions on call counts
// - function time.dayOfWeek() is called once
// - function logger.log(any) is called 3 times
// - attribute <time.now> is called once
// - attribute any.<user.location> is called 2 times
verifyBlock
    : VERIFY (DASH verifyStep)+
    ;

verifyStep
    : FUNCTION functionFullName=functionName functionParameters?
      IS CALLED timesCalled=numericAmount                            # functionVerification
    | ATTRIBUTE attributeReference IS CALLED timesCalled=numericAmount  # attributeVerification
    ;

// When step
whenStep
    : WHEN authorizationSubscription
    ;

authorizationSubscription
    : SUBJECT? subject=value ATTEMPTS ACTION? action=value
      ON RESOURCE? resource=value (IN ENVIRONMENT? env=objectValue)?
    ;

// Expectation
expectation
    : EXPECT authorizationDecision                                                  # singleExpectation
    | EXPECT DECISION matchers+=authorizationDecisionMatcher
      (COMMA matchers+=authorizationDecisionMatcher)*                               # matcherExpectation
    | EXPECT (DASH expectStep)+                                                     # streamExpectation
    ;

expectStep
    : expectedDecision=authorizationDecisionType amount=numericAmount               # nextDecisionStep
    | expectedDecision=authorizationDecision                                        # nextWithDecisionStep
    | DECISION matchers+=authorizationDecisionMatcher
      (COMMA matchers+=authorizationDecisionMatcher)*                               # nextWithMatcherStep
    ;

// Authorization decision
authorizationDecision
    : decision=authorizationDecisionType
      (WITH OBLIGATIONS obligations+=value (COMMA obligations+=value)*)?
      (WITH RESOURCE resource=value)?
      (WITH ADVICE advice+=value (COMMA advice+=value)*)?
    ;

authorizationDecisionType
    : PERMIT        # permitDecision
    | DENY          # denyDecision
    | INDETERMINATE # indeterminateDecision
    | NOT_APPLICABLE # notApplicableDecision
    ;

// Authorization decision matchers
authorizationDecisionMatcher
    : ANY                                                                           # anyDecisionMatcher
    | IS decision=authorizationDecisionType                                         # isDecisionMatcher
    | WITH matcherType=(OBLIGATION | ADVICE) extendedMatcher=extendedObjectMatcher? # hasObligationOrAdviceMatcher
    | WITH RESOURCE defaultMatcher=defaultObjectMatcher?                            # hasResourceMatcher
    ;

// Value matchers
valMatcher
    : ANY                                                                           # anyValMatcher
    | value                                                                         # valueValMatcher
    | MATCHING nodeMatcher                                                          # matchingValMatcher
    ;

defaultObjectMatcher
    : EQUALS equalTo=value                                                          # exactMatchObjectMatcher
    | MATCHING nodeMatcher                                                          # matchingObjectMatcher
    ;

extendedObjectMatcher
    : defaultObjectMatcher                                                          # defaultExtendedMatcher
    | CONTAINING KEY key=STRING (WITH VALUE MATCHING matcher=nodeMatcher)?          # keyValueObjectMatcher
    ;

// Node matchers
nodeMatcher
    : NULL_KEYWORD                                                                  # nullMatcher
    | TEXT stringOrStringMatcher?                                                   # textMatcher
    | NUMBER_KEYWORD number=NUMBER?                                                 # numberMatcher
    | BOOLEAN_KEYWORD booleanLiteral?                                               # booleanMatcher
    | ARRAY (WHERE arrayMatcherBody)?                                               # arrayMatcher
    | OBJECT (WHERE objectMatcherBody)?                                             # objectMatcher
    ;

stringOrStringMatcher
    : text=STRING       # plainStringMatcher
    | stringMatcher     # complexStringMatcher
    ;

stringMatcher
    : NULL_KEYWORD                                                                  # stringIsNull
    | BLANK                                                                         # stringIsBlank
    | EMPTY                                                                         # stringIsEmpty
    | NULL_OR_EMPTY                                                                 # stringIsNullOrEmpty
    | NULL_OR_BLANK                                                                 # stringIsNullOrBlank
    | EQUAL TO matchValue=STRING WITH COMPRESSED WHITESPACE                         # stringEqualCompressedWhitespace
    | EQUAL TO matchValue=STRING CASE_INSENSITIVE                                   # stringEqualIgnoringCase
    | WITH REGEX regex=STRING                                                       # stringMatchesRegex
    | STARTING WITH prefix=STRING caseInsensitive=CASE_INSENSITIVE?                 # stringStartsWith
    | ENDING WITH postfix=STRING caseInsensitive=CASE_INSENSITIVE?                  # stringEndsWith
    | CONTAINING text=STRING caseInsensitive=CASE_INSENSITIVE?                      # stringContains
    | CONTAINING STREAM substrings+=STRING (COMMA substrings+=STRING)* IN ORDER     # stringContainsInOrder
    | WITH LENGTH length=NUMBER                                                     # stringWithLength
    ;

arrayMatcherBody
    : LBRACKET matchers+=nodeMatcher (COMMA matchers+=nodeMatcher)* RBRACKET
    ;

objectMatcherBody
    : LBRACE members+=objectMatcherPair (AND members+=objectMatcherPair)* RBRACE
    ;

objectMatcherPair
    : key=STRING IS matcher=nodeMatcher
    ;

// Values (subset used in SAPLTest)
value
    : objectValue     # objectVal
    | arrayValue      # arrayVal
    | numberLiteral   # numberVal
    | stringLiteral   # stringVal
    | booleanLiteral  # booleanVal
    | nullLiteral     # nullVal
    | undefinedLiteral # undefinedVal
    ;

valueOrError
    : value           # regularValue
    | errorValue      # errorVal
    ;

errorValue
    : ERROR (LPAREN message=STRING RPAREN)?
    ;

objectValue
    : LBRACE (pair (COMMA pair)*)? RBRACE
    ;

pair
    : key=STRING COLON pairValue=value
    ;

arrayValue
    : LBRACKET (items+=value (COMMA items+=value)*)? RBRACKET
    ;

booleanLiteral
    : TRUE  # trueLiteral
    | FALSE # falseLiteral
    ;

nullLiteral
    : NULL_KEYWORD
    ;

undefinedLiteral
    : UNDEFINED
    ;

stringLiteral
    : STRING
    ;

numberLiteral
    : NUMBER
    ;

// Identifiers - allows combining algorithm keywords to be used as identifiers
testId
    : ID
    | ABSTAIN
    | ERRORS
    | FIRST
    | PRIORITY
    | PROPAGATE
    | STRICT
    | UNANIMOUS
    | UNIQUE
    ;
