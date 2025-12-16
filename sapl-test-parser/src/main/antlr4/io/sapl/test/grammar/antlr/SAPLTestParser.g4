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
    : SCENARIO name=STRING given? whenStep expectation SEMI
    ;

// Given block
given
    : GIVEN
      (DASH document)?
      (DASH pdpVariables)?
      (DASH pdpCombiningAlgorithm)?
      (DASH environment)?
      (DASH givenStep)*
    ;

givenStep
    : mockDefinition  # mockGivenStep
    | importStatement # importGivenStep
    ;

// Document specification
document
    : POLICY identifier=STRING                                                              # singleDocument
    | SET identifier=STRING                                                                 # documentSet
    | POLICIES identifiers+=STRING (COMMA identifiers+=STRING)+
      (WITH PDP CONFIGURATION pdpConfigurationIdentifier=STRING)?                           # multipleDocuments
    ;

// PDP configuration
pdpVariables
    : PDP VARIABLES variables=jsonObject
    ;

pdpCombiningAlgorithm
    : PDP COMBINING_ALGORITHM combiningAlgorithm
    ;

combiningAlgorithm
    : DENY_OVERRIDES      # denyOverridesAlgorithm
    | PERMIT_OVERRIDES    # permitOverridesAlgorithm
    | ONLY_ONE_APPLICABLE # onlyOneApplicableAlgorithm
    | DENY_UNLESS_PERMIT  # denyUnlessPermitAlgorithm
    | PERMIT_UNLESS_DENY  # permitUnlessDenyAlgorithm
    ;

environment
    : ENVIRONMENT env=jsonObject
    ;

// Import statements
importStatement
    : importType identifier=STRING
    ;

importType
    : PIP                     # pipImport
    | STATIC_PIP              # staticPipImport
    | FUNCTION_LIBRARY        # functionLibraryImport
    | STATIC_FUNCTION_LIBRARY # staticFunctionLibraryImport
    ;

// Mock definitions
mockDefinition
    : FUNCTION name=STRING (OF parameterMatchers=functionParameterMatchers)?
      MAPS TO returnValue=valueOrError (IS CALLED timesCalled=numericAmount)?              # functionMock
    | FUNCTION name=STRING MAPS TO STREAM returnValues+=valueOrError
      (COMMA returnValues+=valueOrError)*                                                   # functionStreamMock
    | ATTRIBUTE name=STRING EMITS returnValues+=valueOrError (COMMA returnValues+=valueOrError)*
      (WITH TIMING timing=duration)?                                                        # attributeMock
    | ATTRIBUTE name=STRING OF LT parentMatcher=valMatcher GT
      parameterMatchers=attributeParameterMatchers? EMITS returnValue=valueOrError          # attributeWithParametersMock
    | VIRTUAL_TIME                                                                          # virtualTimeMock
    ;

functionParameterMatchers
    : LPAREN matchers+=valMatcher (COMMA matchers+=valMatcher)* RPAREN
    ;

attributeParameterMatchers
    : LPAREN matchers+=valMatcher (COMMA matchers+=valMatcher)* RPAREN
    ;

numericAmount
    : ONCE                # onceAmount
    | amount=NUMBER TIMES # multipleAmount
    ;

duration
    : value=STRING
    ;

// When step
whenStep
    : WHEN authorizationSubscription
    ;

authorizationSubscription
    : SUBJECT? subject=jsonValue ATTEMPTS ACTION? action=jsonValue
      ON RESOURCE? resource=jsonValue (IN ENVIRONMENT? env=jsonObject)?
    ;

// Expectation
expectation
    : EXPECT authorizationDecision                                                          # singleExpectation
    | EXPECT DECISION matchers+=authorizationDecisionMatcher
      (COMMA matchers+=authorizationDecisionMatcher)*                                       # matcherExpectation
    | expectOrAdjustBlock+                                                                  # repeatedExpectation
    ;

expectOrAdjustBlock
    : expectBlock  # expectBlockElement
    | adjustBlock  # adjustBlockElement
    ;

expectBlock
    : EXPECT (DASH expectStep)+
    ;

adjustBlock
    : THEN (DASH adjustStep)+
    ;

expectStep
    : expectedDecision=authorizationDecisionType amount=numericAmount                       # nextDecisionStep
    | expectedDecision=authorizationDecision                                                # nextWithDecisionStep
    | DECISION matchers+=authorizationDecisionMatcher
      (COMMA matchers+=authorizationDecisionMatcher)*                                       # nextWithMatcherStep
    | NO_EVENT FOR noEventDuration=duration                                                 # noEventStep
    ;

adjustStep
    : ATTRIBUTE attribute=STRING EMITS returnValue=jsonValue                                # attributeAdjustmentStep
    | WAIT waitDuration=duration                                                            # awaitStep
    ;

// Authorization decision
authorizationDecision
    : decision=authorizationDecisionType
      (WITH OBLIGATIONS obligations+=jsonValue (COMMA obligations+=jsonValue)*)?
      (WITH RESOURCE resource=jsonValue)?
      (WITH ADVICE advice+=jsonValue (COMMA advice+=jsonValue)*)?
    ;

authorizationDecisionType
    : PERMIT        # permitDecision
    | DENY          # denyDecision
    | INDETERMINATE # indeterminateDecision
    | NOT_APPLICABLE # notApplicableDecision
    ;

// Authorization decision matchers
authorizationDecisionMatcher
    : ANY                                                                                   # anyDecisionMatcher
    | IS decision=authorizationDecisionType                                                 # isDecisionMatcher
    | WITH matcherType=(OBLIGATION | ADVICE) extendedMatcher=extendedObjectMatcher?         # hasObligationOrAdviceMatcher
    | WITH RESOURCE defaultMatcher=defaultObjectMatcher?                                    # hasResourceMatcher
    ;

// Value matchers
valMatcher
    : ANY                                                                                   # anyValMatcher
    | jsonValue                                                                             # valueValMatcher
    | MATCHING jsonNodeMatcher                                                              # matchingValMatcher
    ;

defaultObjectMatcher
    : EQUALS equalTo=jsonValue                                                              # exactMatchObjectMatcher
    | MATCHING jsonNodeMatcher                                                              # matchingObjectMatcher
    ;

extendedObjectMatcher
    : defaultObjectMatcher                                                                  # defaultExtendedMatcher
    | CONTAINING KEY key=STRING (WITH VALUE MATCHING matcher=jsonNodeMatcher)?              # keyValueObjectMatcher
    ;

// JSON node matchers
jsonNodeMatcher
    : NULL_KEYWORD                                                                          # nullMatcher
    | TEXT stringOrStringMatcher?                                                           # textMatcher
    | NUMBER_KEYWORD number=NUMBER?                                                         # numberMatcher
    | BOOLEAN_KEYWORD booleanLiteral?                                                       # booleanMatcher
    | ARRAY (WHERE jsonArrayMatcher)?                                                       # arrayMatcher
    | OBJECT (WHERE jsonObjectMatcher)?                                                     # objectMatcher
    ;

stringOrStringMatcher
    : text=STRING       # plainStringMatcher
    | stringMatcher     # complexStringMatcher
    ;

stringMatcher
    : NULL_KEYWORD                                                                          # stringIsNull
    | BLANK                                                                                 # stringIsBlank
    | EMPTY                                                                                 # stringIsEmpty
    | NULL_OR_EMPTY                                                                         # stringIsNullOrEmpty
    | NULL_OR_BLANK                                                                         # stringIsNullOrBlank
    | EQUAL TO value=STRING WITH COMPRESSED WHITESPACE                                      # stringEqualCompressedWhitespace
    | EQUAL TO value=STRING CASE_INSENSITIVE                                                # stringEqualIgnoringCase
    | WITH REGEX regex=STRING                                                               # stringMatchesRegex
    | STARTING WITH prefix=STRING caseInsensitive=CASE_INSENSITIVE?                         # stringStartsWith
    | ENDING WITH postfix=STRING caseInsensitive=CASE_INSENSITIVE?                          # stringEndsWith
    | CONTAINING text=STRING caseInsensitive=CASE_INSENSITIVE?                              # stringContains
    | CONTAINING STREAM substrings+=STRING (COMMA substrings+=STRING)* IN ORDER             # stringContainsInOrder
    | WITH LENGTH length=NUMBER                                                             # stringWithLength
    ;

jsonArrayMatcher
    : LBRACKET matchers+=jsonNodeMatcher (COMMA matchers+=jsonNodeMatcher)* RBRACKET
    ;

jsonObjectMatcher
    : LBRACE members+=jsonObjectMatcherPair (AND members+=jsonObjectMatcherPair)* RBRACE
    ;

jsonObjectMatcherPair
    : key=STRING IS matcher=jsonNodeMatcher
    ;

// JSON values (subset used in SAPLTest)
jsonValue
    : jsonObject      # objectValue
    | jsonArray       # arrayValue
    | numberLiteral   # numberValue
    | stringLiteral   # stringValue
    | booleanLiteral  # booleanValue
    | nullLiteral     # nullValue
    | undefinedLiteral # undefinedValue
    ;

valueOrError
    : jsonValue       # regularValue
    | errorValue      # errorVal
    ;

errorValue
    : ERROR (LPAREN message=STRING RPAREN)?
    ;

jsonObject
    : LBRACE (pair (COMMA pair)*)? RBRACE
    ;

pair
    : key=STRING COLON value=jsonValue
    ;

jsonArray
    : LBRACKET (items+=jsonValue (COMMA items+=jsonValue)*)? RBRACKET
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
