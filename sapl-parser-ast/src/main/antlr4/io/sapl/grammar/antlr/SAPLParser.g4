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
parser grammar SAPLParser;

options { tokenVocab = SAPLLexer; }

// Entry point
sapl
    : importStatement* schemaStatement* policyElement EOF
    ;

importStatement
    : IMPORT libSteps+=saplId (DOT libSteps+=saplId)* DOT functionName=saplId (AS functionAlias=saplId)?
    ;

schemaStatement
    : subscriptionElement=reservedId enforced=ENFORCED? SCHEMA schemaExpression=expression
    ;

policyElement
    : policySet  # policySetElement
    | policy     # policyOnlyElement
    ;

policySet
    : SET saplName=STRING combiningAlgorithm
      (FOR targetExpression=expression)?
      (valueDefinition SEMI)*
      policy+
    ;

combiningAlgorithm
    : DENY_OVERRIDES       # denyOverridesAlgorithm
    | PERMIT_OVERRIDES     # permitOverridesAlgorithm
    | FIRST_APPLICABLE     # firstApplicableAlgorithm
    | ONLY_ONE_APPLICABLE  # onlyOneApplicableAlgorithm
    | DENY_UNLESS_PERMIT   # denyUnlessPermitAlgorithm
    | PERMIT_UNLESS_DENY   # permitUnlessDenyAlgorithm
    ;

policy
    : POLICY saplName=STRING entitlement targetExpression=expression?
      policyBody?
      (OBLIGATION obligations+=expression)*
      (ADVICE adviceExpressions+=expression)*
      (TRANSFORM transformation=expression)?
    ;

entitlement
    : PERMIT  # permitEntitlement
    | DENY    # denyEntitlement
    ;

policyBody
    : WHERE (statements+=statement SEMI)+
    ;

statement
    : valueDefinition  # valueDefinitionStatement
    | expression       # conditionStatement
    ;

valueDefinition
    : VAR name=ID ASSIGN eval=expression
      (SCHEMA schemaVarExpression+=expression (COMMA schemaVarExpression+=expression)*)?
    ;

// Expressions - operator precedence from lowest to highest
expression
    : lazyOr
    ;

lazyOr
    : lazyAnd (OR lazyAnd)*
    ;

lazyAnd
    : eagerOr (AND eagerOr)*
    ;

eagerOr
    : exclusiveOr (BITOR exclusiveOr)*
    ;

exclusiveOr
    : eagerAnd (BITXOR eagerAnd)*
    ;

eagerAnd
    : equality (BITAND equality)*
    ;

equality
    : comparison ((EQ | NEQ | REGEX) comparison)?
    ;

comparison
    : addition ((LT | LE | GT | GE | IN) addition)?
    ;

addition
    : multiplication ((PLUS | MINUS) multiplication)*
    ;

multiplication
    : unaryExpression ((STAR | SLASH | PERCENT) unaryExpression)*
    ;

unaryExpression
    : NOT unaryExpression      # notExpression
    | MINUS unaryExpression    # unaryMinusExpression
    | PLUS unaryExpression     # unaryPlusExpression
    | basicExpression          # basicExpr
    ;

basicExpression
    : basic (FILTER filterComponent | SUBTEMPLATE basicExpression)?
    ;

basic
    : basicGroup                    # groupBasic
    | basicValue                    # valueBasic
    | basicFunction                 # functionBasic
    | basicIdentifier               # identifierBasic
    | basicRelative                 # relativeBasic
    | basicRelativeLocation         # relativeLocationBasic
    | basicEnvironmentAttribute     # envAttributeBasic
    | basicEnvironmentHeadAttribute # envHeadAttributeBasic
    ;

basicGroup
    : LPAREN expression RPAREN step*
    ;

basicValue
    : value step*
    ;

basicFunction
    : functionIdentifier arguments step*
    ;

basicEnvironmentAttribute
    : LT functionIdentifier arguments? (LBRACKET attributeFinderOptions=expression RBRACKET)? GT step*
    ;

basicEnvironmentHeadAttribute
    : PIPE_LT functionIdentifier arguments? (LBRACKET attributeFinderOptions=expression RBRACKET)? GT step*
    ;

functionIdentifier
    : idFragment+=saplId (DOT idFragment+=saplId)*
    ;

basicIdentifier
    : saplId step*
    ;

basicRelative
    : AT step*
    ;

basicRelativeLocation
    : HASH step*
    ;

arguments
    : LPAREN (args+=expression (COMMA args+=expression)*)? RPAREN
    ;

step
    : DOT attributeFinderStep          # attributeFinderDotStep
    | DOT headAttributeFinderStep      # headAttributeFinderDotStep
    | DOT keyStep                      # keyDotStep
    | DOT escapedKeyStep               # escapedKeyDotStep
    | DOT wildcardStep                 # wildcardDotStep
    | LBRACKET subscript RBRACKET      # bracketStep
    | DOTDOT recursiveKeyStep          # recursiveKeyDotDotStep
    | DOTDOT recursiveWildcardStep     # recursiveWildcardDotDotStep
    | DOTDOT recursiveIndexStep        # recursiveIndexDotDotStep
    ;

subscript
    : escapedKeyStep     # escapedKeySubscript
    | wildcardStep       # wildcardSubscript
    | indexStep          # indexSubscript
    | arraySlicingStep   # slicingSubscript
    | expressionStep     # expressionSubscript
    | conditionStep      # conditionSubscript
    | indexUnionStep     # indexUnionSubscript
    | attributeUnionStep # attributeUnionSubscript
    ;

keyStep
    : saplId
    ;

escapedKeyStep
    : STRING
    ;

wildcardStep
    : STAR
    ;

attributeFinderStep
    : LT functionIdentifier arguments? (LBRACKET attributeFinderOptions=expression RBRACKET)? GT
    ;

headAttributeFinderStep
    : PIPE_LT functionIdentifier arguments? (LBRACKET attributeFinderOptions=expression RBRACKET)? GT
    ;

recursiveKeyStep
    : saplId                    # recursiveIdKeyStep
    | LBRACKET STRING RBRACKET  # recursiveStringKeyStep
    ;

recursiveWildcardStep
    : STAR                      # simpleRecursiveWildcard
    | LBRACKET STAR RBRACKET    # bracketRecursiveWildcard
    ;

recursiveIndexStep
    : LBRACKET signedNumber RBRACKET
    ;

indexStep
    : signedNumber
    ;

arraySlicingStep
    : index=signedNumber? COLON to=signedNumber? (COLON stepValue=signedNumber)?
    | index=signedNumber? SUBTEMPLATE stepValue=signedNumber?
    ;

expressionStep
    : LPAREN expression RPAREN
    ;

conditionStep
    : QUESTION LPAREN expression RPAREN
    ;

indexUnionStep
    : indices+=signedNumber COMMA indices+=signedNumber (COMMA indices+=signedNumber)*
    ;

attributeUnionStep
    : attributes+=STRING COMMA attributes+=STRING (COMMA attributes+=STRING)*
    ;

// Values - labeled alternatives for pattern matching
value
    : object           # objectValue
    | array            # arrayValue
    | numberLiteral    # numberValue
    | stringLiteral    # stringValue
    | booleanLiteral   # booleanValue
    | nullLiteral      # nullValue
    | undefinedLiteral # undefinedValue
    ;

object
    : LBRACE (pair (COMMA pair)*)? RBRACE
    ;

pair
    : pairKey COLON pairValue=expression
    ;

pairKey
    : STRING  # stringPairKey
    | saplId  # idPairKey
    ;

array
    : LBRACKET (items+=expression (COMMA items+=expression)*)? RBRACKET
    ;

booleanLiteral
    : TRUE   # trueLiteral
    | FALSE  # falseLiteral
    ;

nullLiteral
    : NULL
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

// Filter components
filterComponent
    : each=EACH? functionIdentifier arguments?                # filterSimple
    | LBRACE filterStatement (COMMA filterStatement)* RBRACE  # filterExtended
    ;

filterStatement
    : each=EACH? target=basicRelative COLON functionIdentifier arguments?
    ;

signedNumber
    : MINUS? NUMBER
    ;

// Identifiers - labeled alternatives
saplId
    : ID         # plainId
    | reservedId # reservedIdentifier
    ;

reservedId
    : SUBJECT     # subjectId
    | ACTION      # actionId
    | RESOURCE    # resourceId
    | ENVIRONMENT # environmentId
    ;
