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

import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The catalogue of what can be wrong with a SAPL document. All implementations
 * of SAPL share it: the same identifiers and the same texts, word for word.
 * <p>
 * Each constant is one message. It belongs to a class of problems, named by
 * its identifier, has a short form that says what is wrong, and an explanation
 * that says what to do. Where a message exists in several wordings, each
 * wording is a constant of its own.
 * <p>
 * The texts name the values of an occurrence, such as the operator found. A
 * constant states which values it takes and in which order. A value is text of
 * the document or a number, with two exceptions that the catalogue words
 * itself: a count written as a word ({@link #countInWords(int)}) and a list of
 * what can stand at a place ({@link #oneOf(List)}, {@link #delimiters(List)}).
 */
public enum Problem {

    /**
     * The document exceeds the size limit.
     * <p>
     * Values: the size of the document in bytes, the limit in bytes.
     */
    DOCUMENT_TOO_LARGE(
            "document-too-large",
            "Document too large.",
            "The document has %1$s bytes. At most %2$s bytes are allowed."),

    /**
     * The document contains a bidirectional embedding, override or isolate
     * control.
     * <p>
     * Values: the code point of the character in hexadecimal, with at least four
     * digits.
     */
    BIDIRECTIONAL_CONTROL(
            "bidirectional-control-character",
            "Bidirectional control character.",
            "The character U+%1$s can make a document display differently from how it is evaluated. Remove it."),

    /**
     * A character stands in the document that is not part of the syntax.
     * <p>
     * Values: the character.
     */
    UNEXPECTED_CHARACTER(
            "unexpected-character",
            "Unknown character '%1$s'.",
            "It is not part of the SAPL syntax. If it belongs to a text, put the text in quotation marks."),

    /**
     * A typographic quotation mark stands where a string would begin, and the
     * text it opens is closed on the same line.
     * <p>
     * Values: the text between the quotation marks.
     */
    TYPOGRAPHIC_QUOTE(
            Identifier.TYPOGRAPHIC_QUOTATION_MARK,
            "Typographic quotation mark.",
            "Write strings with plain quotation marks: \"%1$s\" or '%1$s'."),

    /**
     * A typographic quotation mark stands where a string would begin, and no
     * typographic quotation mark closes it on the same line.
     */
    TYPOGRAPHIC_QUOTE_WITHOUT_CONTENT(
            Identifier.TYPOGRAPHIC_QUOTATION_MARK,
            "Typographic quotation mark.",
            "Write strings with plain quotation marks: \"text\" or 'text'."),

    /**
     * A space or control character stands in the document that an editor does
     * not show, and that has no common name.
     * <p>
     * Values: the code point of the character in hexadecimal, with at least four
     * digits.
     */
    INVISIBLE_CHARACTER(
            Identifier.INVISIBLE_CHARACTER,
            ShortForm.INVISIBLE_CHARACTER,
            "The document contains the character U+%1$s. Replace it with a normal space."),

    /**
     * The document contains a null character.
     */
    INVISIBLE_NULL(
            Identifier.INVISIBLE_CHARACTER,
            ShortForm.INVISIBLE_CHARACTER,
            "The document contains the character U+0000 (null). Replace it with a normal space."),

    /**
     * The document contains a vertical tab.
     */
    INVISIBLE_VERTICAL_TAB(
            Identifier.INVISIBLE_CHARACTER,
            ShortForm.INVISIBLE_CHARACTER,
            "The document contains the character U+000B (vertical tab). Replace it with a normal space."),

    /**
     * The document contains a form feed.
     */
    INVISIBLE_FORM_FEED(
            Identifier.INVISIBLE_CHARACTER,
            ShortForm.INVISIBLE_CHARACTER,
            "The document contains the character U+000C (form feed). Replace it with a normal space."),

    /**
     * The document contains a no-break space.
     */
    INVISIBLE_NO_BREAK_SPACE(
            Identifier.INVISIBLE_CHARACTER,
            ShortForm.INVISIBLE_CHARACTER,
            "The document contains the character U+00A0 (no-break space). Replace it with a normal space."),

    /**
     * The document contains a zero width space.
     */
    INVISIBLE_ZERO_WIDTH_SPACE(
            Identifier.INVISIBLE_CHARACTER,
            ShortForm.INVISIBLE_CHARACTER,
            "The document contains the character U+200B (zero width space). Replace it with a normal space."),

    /**
     * The document contains a line separator.
     */
    INVISIBLE_LINE_SEPARATOR(
            Identifier.INVISIBLE_CHARACTER,
            ShortForm.INVISIBLE_CHARACTER,
            "The document contains the character U+2028 (line separator). Replace it with a normal space."),

    /**
     * The document contains a paragraph separator.
     */
    INVISIBLE_PARAGRAPH_SEPARATOR(
            Identifier.INVISIBLE_CHARACTER,
            ShortForm.INVISIBLE_CHARACTER,
            "The document contains the character U+2029 (paragraph separator). Replace it with a normal space."),

    /**
     * The document contains an ideographic space.
     */
    INVISIBLE_IDEOGRAPHIC_SPACE(
            Identifier.INVISIBLE_CHARACTER,
            ShortForm.INVISIBLE_CHARACTER,
            "The document contains the character U+3000 (ideographic space). Replace it with a normal space."),

    /**
     * The document contains a byte order mark.
     */
    INVISIBLE_BYTE_ORDER_MARK(
            Identifier.INVISIBLE_CHARACTER,
            ShortForm.INVISIBLE_CHARACTER,
            "The document contains the character U+FEFF (byte order mark). Replace it with a normal space."),

    /**
     * A name contains characters that names cannot have.
     * <p>
     * Values: the name as written.
     */
    INVALID_NAME(
            Identifier.INVALID_NAME_CHARACTER,
            "Invalid name '%1$s'.",
            "Names consist of the letters a-z and A-Z, digits, '_' and '$'."),

    /**
     * A name that stands after a dot contains characters that names cannot
     * have.
     * <p>
     * Values: the name as written, how to select a key of that name.
     */
    INVALID_KEY_NAME(
            Identifier.INVALID_NAME_CHARACTER,
            "Invalid name '%1$s'.",
            "Names consist of the letters a-z and A-Z, digits, '_' and '$'. Write the key as a string: %2$s"),

    /**
     * A string is not closed on its line.
     */
    UNTERMINATED_STRING("unterminated-string", "Unclosed string.", "Add the closing quotation mark."),

    /**
     * A block comment is not closed.
     */
    UNTERMINATED_COMMENT("unterminated-comment", "Unclosed comment.", "Add '*/' where the comment ends."),

    /**
     * A backslash in a string is followed by a character that forms no escape
     * sequence.
     * <p>
     * Values: the escape as written.
     */
    INVALID_ESCAPE(
            "invalid-escape",
            "Unknown escape '%1$s'.",
            "To write a backslash, use '\\\\'. The escape sequences are \\\" \\' \\\\ \\/ \\b \\f \\n \\r \\t and \\uXXXX."),

    /**
     * A unicode escape has fewer than four hexadecimal digits.
     */
    INCOMPLETE_UNICODE_ESCAPE(
            "incomplete-unicode-escape",
            "Incomplete unicode escape.",
            "'\\u' must be followed by four hexadecimal digits, for example '\\u00FC'."),

    /**
     * A string escape denotes a bidirectional control.
     * <p>
     * Values: the escape as written.
     */
    ESCAPED_BIDIRECTIONAL_CONTROL(
            "escaped-bidirectional-control",
            "Escaped bidirectional control character.",
            "The escape '%1$s' denotes a character that can make a text display differently from how it is evaluated. Remove it."),

    /**
     * A string escape denotes one half of a surrogate pair without the other.
     * <p>
     * Values: the escape as written.
     */
    LONE_SURROGATE_ESCAPE(
            "lone-surrogate-escape",
            "Incomplete surrogate pair.",
            "The escape '%1$s' is one half of a surrogate pair and not a character on its own. Add the second half, for example '\\uD83D\\uDE00', or remove it."),

    /**
     * Parentheses, brackets and braces are nested beyond the limit.
     * <p>
     * Values: the limit.
     */
    NESTING_TOO_DEEP(
            "nesting-too-deep",
            "Nesting too deep.",
            "At most %1$s levels of parentheses, brackets and braces are allowed."),

    /**
     * A bracket is not closed before the document ends.
     * <p>
     * Values: the opening bracket, the closing bracket of its kind.
     */
    UNCLOSED_BRACKET(Identifier.UNCLOSED_BRACKET, "Unclosed '%1$s'.", "Add the matching '%2$s'."),

    /**
     * A bracket is not closed where the construct around it ends.
     * <p>
     * Values: the closing bracket that is missing, the opening bracket, the line
     * of the opening bracket, the column of the opening bracket.
     */
    MISSING_CLOSING_BRACKET(
            Identifier.UNCLOSED_BRACKET,
            "Missing '%1$s'.",
            "It must close the '%2$s' opened at line %3$s, column %4$s."),

    /**
     * A closing bracket is of another kind than the bracket it would close.
     * <p>
     * Values: the closing bracket found, the opening bracket, the line of the
     * opening bracket, the column of the opening bracket, the closing bracket
     * that matches the opening bracket.
     */
    MISMATCHED_BRACKET(
            "mismatched-bracket",
            "Mismatched '%1$s'.",
            "It does not match the '%2$s' opened at line %3$s, column %4$s. Close that with '%5$s'."),

    /**
     * A closing bracket has no opening bracket.
     * <p>
     * Values: the closing bracket found, the opening bracket of its kind.
     */
    UNMATCHED_CLOSING_BRACKET(
            "unmatched-closing-bracket",
            "Unmatched '%1$s'.",
            "It has no matching '%2$s'. Remove it or add the missing '%2$s'."),

    /**
     * A number starts with a zero that is followed by further digits.
     * <p>
     * Values: the number without the leading zeros.
     */
    LEADING_ZERO(
            "leading-zero",
            "Leading zero.",
            "A number must not start with '0' followed by further digits. Write '%1$s'."),

    /**
     * A number ends with a point.
     * <p>
     * Values: the digits before the point.
     */
    NUMBER_ENDS_WITH_POINT(Identifier.MALFORMED_NUMBER, "Number ends with a point.", "Write '%1$s' or '%1$s.0'."),

    /**
     * A number starts with a point.
     * <p>
     * Values: the digits after the point.
     */
    NUMBER_STARTS_WITH_POINT(Identifier.MALFORMED_NUMBER, "Number starts with a point.", "Write '0.%1$s'."),

    /**
     * Letters follow the digits of a number directly.
     * <p>
     * Values: the number and the letters as written.
     */
    NUMBER_WITH_LETTERS(
            Identifier.MALFORMED_NUMBER,
            "Invalid number '%1$s'.",
            "Numbers are written in decimal, for example '31', '1.5' or '2e3'."),

    /**
     * A number stands after a dot where a key is selected.
     * <p>
     * Values: the selection written with brackets.
     */
    INDEX_AFTER_DOT(
            Identifier.KEY_MISSING,
            "Index after '.'.",
            "An element of an array is selected with brackets: %1$s"),

    /**
     * A number literal exceeds the length limit.
     * <p>
     * Values: the number of characters of the literal, the limit.
     */
    NUMBER_TOO_LONG(
            "number-too-long",
            "Number too long.",
            "The number has %1$s characters. At most %2$s characters are allowed."),

    /**
     * The exponent of a number exceeds the limit.
     * <p>
     * Values: the number as written, the limit.
     */
    NUMBER_OUT_OF_RANGE(
            "number-out-of-range",
            "Number out of range.",
            "The exponent of '%1$s' minus the number of digits after its decimal point is too large. It may be at most %2$s in magnitude."),

    /**
     * An index is written with a fraction or an exponent.
     * <p>
     * Values: the index as written.
     */
    INDEX_NOT_WHOLE(
            Identifier.INDEX_NOT_AN_INTEGER,
            "Invalid index '%1$s'.",
            "An index is a whole number, written without fraction and exponent."),

    /**
     * An index does not fit into the range of indices.
     * <p>
     * Values: the index as written, the smallest index, the largest index.
     */
    INDEX_TOO_LARGE(
            Identifier.INDEX_NOT_AN_INTEGER,
            "Index too large.",
            "The index '%1$s' must be between %2$s and %3$s."),

    /**
     * A function or attribute finder is used by a short name that no import
     * introduces.
     * <p>
     * Values: the short name.
     */
    NAME_NOT_IMPORTED(
            "name-not-imported",
            "Name '%1$s' not imported.",
            "Write it together with its library as 'library.%1$s', or import it at the top of the document."),

    /**
     * The name of a function or attribute finder has more than two parts.
     * <p>
     * Values: the name as written, the number of its parts as
     * {@link #countInWords(int)} writes it.
     */
    NAME_TOO_LONG(
            "name-too-long",
            "Name too long.",
            "A function or attribute finder is named 'library.name'. '%1$s' has %2$s parts."),

    /**
     * Two imports introduce the same short name.
     * <p>
     * Values: the short name, the fully qualified name the short name already
     * stands for, the fully qualified name of the conflicting import, an alias
     * for the conflicting import.
     */
    IMPORT_CONFLICT(
            Identifier.IMPORT_CONFLICT,
            "Import conflict.",
            "The short name '%1$s' is already imported from '%2$s'. Give this import an alias, for example: import %3$s as %4$s"),

    /**
     * Two imports introduce the same short name, and the name the short name
     * already stands for is too long to be quoted by every import that conflicts
     * with it.
     * <p>
     * Values: the short name, the fully qualified name of the conflicting import,
     * an alias for the conflicting import.
     */
    IMPORT_CONFLICT_WITHOUT_ORIGIN(
            Identifier.IMPORT_CONFLICT,
            "Import conflict.",
            "The short name '%1$s' is already imported. Give this import an alias, for example: import %2$s as %3$s"),

    /**
     * A schema expression holds an attribute finder.
     */
    ATTRIBUTE_FINDER_IN_SCHEMA(
            "attribute-in-schema",
            "Attribute finder in schema.",
            "Schemas are evaluated without access to policy information points. Remove the attribute finder."),

    /**
     * The target of a filter statement holds an attribute finder.
     */
    ATTRIBUTE_FINDER_IN_FILTER_TARGET(
            "attribute-in-filter-target",
            "Attribute finder in filter target.",
            "A target selects parts of the value that is being filtered. Remove the attribute finder."),

    /**
     * A variable takes the name of an element of the authorization subscription.
     * <p>
     * Values: the name.
     */
    RESERVED_VARIABLE_NAME(
            "reserved-variable-name",
            "Reserved name '%1$s'.",
            "The name is bound by the authorization subscription. Choose another name for the variable."),

    /**
     * The document holds no policy and no policy set.
     */
    EMPTY_DOCUMENT("empty-document", "No policy.", "A SAPL document holds exactly one policy or one policy set."),

    /**
     * The document does not start with a policy, a policy set, an import or a
     * schema.
     */
    MISSING_POLICY_HEADER(
            "document-start",
            "Missing policy header.",
            "A document starts with 'policy' or 'set', optionally preceded by imports and schemas. A policy starts like this: policy \"name\" permit"),

    /**
     * A policy has no name.
     */
    MISSING_POLICY_NAME(
            Identifier.NAME_MISSING,
            "Missing policy name.",
            "Add the name in quotation marks after 'policy', for example: policy \"doctors read records\""),

    /**
     * A policy set has no name.
     */
    MISSING_POLICY_SET_NAME(
            Identifier.NAME_MISSING,
            "Missing policy set name.",
            "Add the name in quotation marks after 'set', for example: set \"hospital policies\""),

    /**
     * The name of a policy is not in quotation marks.
     * <p>
     * Values: the name as written.
     */
    UNQUOTED_POLICY_NAME(
            Identifier.NAME_MISSING,
            "Unquoted policy name.",
            "Put the name in quotation marks: policy \"%1$s\""),

    /**
     * The name of a policy set is not in quotation marks.
     * <p>
     * Values: the name as written.
     */
    UNQUOTED_POLICY_SET_NAME(
            Identifier.NAME_MISSING,
            "Unquoted policy set name.",
            "Put the name in quotation marks: set \"%1$s\""),

    /**
     * A policy has no effect.
     */
    MISSING_EFFECT(
            Identifier.EFFECT_MISSING,
            "Missing effect.",
            "After the name of the policy one of 'permit', 'deny' or 'suspend' must follow."),

    /**
     * A word stands where the effect of a policy must stand.
     * <p>
     * Values: the word found, the effect that was probably meant.
     */
    UNKNOWN_EFFECT(
            Identifier.EFFECT_MISSING,
            "Unknown effect '%1$s'.",
            "Did you mean '%2$s'? The effects are 'permit', 'deny' and 'suspend'."),

    /**
     * A second policy follows a policy that is not inside a policy set.
     */
    SECOND_POLICY(
            "second-policy",
            "Second policy.",
            "A document holds exactly one policy. To combine several policies, put them into a policy set that starts like this: set \"name\" priority deny or abstain"),

    /**
     * An import stands after the start of the policy.
     */
    MISPLACED_IMPORT(
            Identifier.MISPLACED_IMPORT_OR_SCHEMA,
            "Misplaced import.",
            "Imports must stand at the top of the document, before the policy."),

    /**
     * A schema statement stands after the start of the policy.
     */
    MISPLACED_SCHEMA(
            Identifier.MISPLACED_IMPORT_OR_SCHEMA,
            "Misplaced schema.",
            "Schema statements must stand at the top of the document, before the policy."),

    /**
     * An import names only a library.
     */
    INCOMPLETE_IMPORT(
            Identifier.IMPORT_MALFORMED,
            "Incomplete import.",
            "An import names a function or attribute finder together with its library, for example: import filter.blacken"),

    /**
     * An import has the keyword as but no alias.
     */
    MISSING_ALIAS(
            Identifier.IMPORT_MALFORMED,
            "Missing alias.",
            "After 'as' the short name must follow, for example: import filter.blacken as redact"),

    /**
     * An import tries to import a whole library.
     */
    LIBRARY_IMPORT(
            Identifier.IMPORT_MALFORMED,
            "Library import.",
            "A whole library cannot be imported. Import each function or attribute finder with its own import, for example: import filter.blacken"),

    /**
     * A schema statement lacks the keyword schema.
     * <p>
     * Values: the element of the subscription the statement is about.
     */
    MISSING_SCHEMA_KEYWORD(
            Identifier.SCHEMA_STATEMENT_MALFORMED,
            "Missing 'schema'.",
            "At the top of a document '%1$s' starts a schema statement, for example: %1$s schema { \"type\": \"object\" }"),

    /**
     * A schema statement lacks the keyword schema after the keyword enforced.
     * <p>
     * Values: the element of the subscription the statement is about.
     */
    MISSING_SCHEMA_KEYWORD_AFTER_ENFORCED(
            Identifier.SCHEMA_STATEMENT_MALFORMED,
            "Missing 'schema'.",
            "After 'enforced' the keyword 'schema' must follow, for example: %1$s enforced schema { \"type\": \"object\" }"),

    /**
     * A policy set has no combining algorithm.
     * <p>
     * Values: the name of the policy set as written, with its quotation marks.
     */
    MISSING_ALGORITHM(
            Identifier.COMBINING_ALGORITHM_MALFORMED,
            "Missing combining algorithm.",
            "Add one after the name of the policy set, for example: set %1$s priority deny or abstain errors propagate"),

    /**
     * The voting mode priority lacks the decision that has priority.
     */
    INCOMPLETE_VOTING_MODE(
            Identifier.COMBINING_ALGORITHM_MALFORMED,
            "Incomplete combining algorithm.",
            "After 'priority' one of 'deny', 'permit' or 'suspend' must follow, for example: priority deny or abstain"),

    /**
     * A combining algorithm lacks the keyword or after its voting mode.
     * <p>
     * Values: the voting mode as written.
     */
    MISSING_OR(
            Identifier.COMBINING_ALGORITHM_MALFORMED,
            "Missing 'or'.",
            "After the voting mode '%1$s' the keyword 'or' and a default decision must follow, for example: %1$s or deny"),

    /**
     * A combining algorithm lacks the default decision after the keyword or.
     */
    MISSING_DEFAULT_DECISION(
            Identifier.COMBINING_ALGORITHM_MALFORMED,
            "Missing default decision.",
            "After 'or' one of 'permit', 'deny', 'suspend' or 'abstain' must follow."),

    /**
     * A word stands where the error handling of a combining algorithm must
     * stand.
     * <p>
     * Values: the word found.
     */
    UNKNOWN_ERROR_HANDLING(
            Identifier.COMBINING_ALGORITHM_MALFORMED,
            "Unknown error handling '%1$s'.",
            "After 'errors' either 'abstain' or 'propagate' must follow."),

    /**
     * A word stands where the decision after the keyword priority must stand.
     * <p>
     * Values: the word found, the decision that was probably meant.
     */
    UNKNOWN_PRIORITY(
            Identifier.COMBINING_ALGORITHM_MALFORMED,
            "Unknown keyword '%1$s'.",
            "Did you mean '%2$s'? After 'priority' one of 'deny', 'permit' or 'suspend' must follow."),

    /**
     * A combining algorithm is named as in SAPL 3.
     * <p>
     * Values: the same algorithm as written in SAPL 4.
     */
    LEGACY_ALGORITHM(Identifier.COMBINING_ALGORITHM_MALFORMED, "SAPL 3 algorithm name.", "In SAPL 4 write: %1$s"),

    /**
     * A policy set contains no policy.
     */
    EMPTY_POLICY_SET(
            "policy-set-without-policy",
            "Empty policy set.",
            "Add at least one policy after the header of the policy set."),

    /**
     * A policy set stands inside a policy set.
     */
    NESTED_POLICY_SET(
            "nested-policy-set",
            "Nested policy set.",
            "A policy set contains policies only, no further policy sets."),

    /**
     * A policy of a policy set starts with its name, without the keyword policy.
     * <p>
     * Values: the name of the policy as written, with its quotation marks.
     */
    MISSING_POLICY_KEYWORD(
            "policy-keyword-missing",
            "Missing 'policy'.",
            "Each policy of a policy set starts with the keyword 'policy': policy %1$s"),

    /**
     * A statement is not terminated by a semicolon.
     */
    MISSING_SEMICOLON("statement-not-terminated", "Missing ';'.", "Terminate this statement with ';'."),

    /**
     * Two values follow each other without an operator.
     */
    MISSING_OPERATOR(
            "operator-missing",
            "Missing operator.",
            "Two values follow each other. Add an operator such as '==' between them, or end the first statement with ';'."),

    /**
     * The sign of a definition is used to compare two values.
     */
    ASSIGNMENT_AS_COMPARISON(
            "assignment-instead-of-comparison",
            "Wrong operator '='.",
            "'=' only defines variables. To compare two values, use '=='."),

    /**
     * An operator of another language is used.
     * <p>
     * Values: the operator found, the operator of SAPL that does the same.
     */
    UNKNOWN_OPERATOR("unknown-operator", "Unknown operator '%1$s'.", "Use '%2$s'."),

    /**
     * A comparison is applied to the result of a comparison, and the second
     * comparison does not test for equality.
     * <p>
     * Values: the comparison written in a form that is valid.
     */
    CHAINED_COMPARISON(
            Identifier.CHAINED_COMPARISON,
            "Chained comparison.",
            "Write two comparisons joined by '&&': %1$s"),

    /**
     * A comparison is applied to the result of a comparison, and the second
     * comparison tests for equality. The author may then have meant to compare
     * the result of the first comparison.
     * <p>
     * Values: the comparison written in a form that is valid.
     */
    CHAINED_COMPARISON_OF_EQUALITY(
            Identifier.CHAINED_COMPARISON,
            "Chained comparison.",
            "Join two comparisons with '&&', or use parentheses to compare the result of a comparison: %1$s"),

    /**
     * An operator or opening bracket is not followed by a value.
     * <p>
     * Values: the text after which the value is missing.
     */
    MISSING_VALUE(Identifier.VALUE_MISSING, "Missing value.", "A value must follow '%1$s'."),

    /**
     * Parentheses with nothing between them.
     */
    EMPTY_PARENTHESES(Identifier.VALUE_MISSING, "Empty parentheses.", "Put a value between them or remove them."),

    /**
     * A semicolon that terminates nothing.
     */
    SUPERFLUOUS_SEMICOLON("empty-statement", "Superfluous ';'.", "Remove it."),

    /**
     * A keyword is used as the name of a variable.
     * <p>
     * Values: the keyword.
     */
    KEYWORD_AS_NAME(Identifier.KEYWORD_AS_NAME, "Keyword '%1$s' as name.", "Write it in backticks: `%1$s`"),

    /**
     * A keyword is used as a key after a dot.
     * <p>
     * Values: the keyword, the path before the dot as written.
     */
    KEYWORD_AS_KEY(
            Identifier.KEYWORD_AS_NAME,
            "Keyword '%1$s' as key.",
            "Write it in backticks or as a string: %2$s.`%1$s` or %2$s[\"%1$s\"]"),

    /**
     * A keyword is used as the key of an object entry.
     * <p>
     * Values: the keyword.
     */
    KEYWORD_AS_OBJECT_KEY(
            Identifier.KEYWORD_AS_NAME,
            "Keyword '%1$s' as object key.",
            "Write it as a string: \"%1$s\""),

    /**
     * A variable definition has no value.
     * <p>
     * Values: the name of the variable.
     */
    VARIABLE_WITHOUT_VALUE(
            Identifier.VARIABLE_DEFINITION_MALFORMED,
            "Missing value.",
            "The variable '%1$s' needs a value: var %1$s = <value>;"),

    /**
     * A variable definition uses the operator of equality.
     * <p>
     * Values: the definition as it should be written.
     */
    VARIABLE_WITH_COMPARISON(
            Identifier.VARIABLE_DEFINITION_MALFORMED,
            "Wrong operator '=='.",
            "A variable is defined with a single '=': %1$s"),

    /**
     * A variable definition has no name.
     */
    MISSING_VARIABLE_NAME(
            Identifier.VARIABLE_DEFINITION_MALFORMED,
            "Missing variable name.",
            "Write: var <name> = <value>;"),

    /**
     * The name of a variable is in quotation marks.
     * <p>
     * Values: the definition as it should be written.
     */
    QUOTED_VARIABLE_NAME(
            Identifier.VARIABLE_DEFINITION_MALFORMED,
            "Quoted variable name.",
            "The name of a variable is written without quotation marks: %1$s"),

    /**
     * A condition or variable definition follows an obligation, advice or
     * transformation.
     */
    MISPLACED_STATEMENT(
            "statement-after-obligation",
            "Misplaced statement.",
            "Conditions and variable definitions must stand before 'obligation', 'advice' and 'transform'. Move this statement up."),

    /**
     * A semicolon follows an obligation, advice or transformation.
     */
    SEMICOLON_AFTER_PART(
            "semicolon-after-obligation",
            "Superfluous ';'.",
            "An obligation, an advice and a transformation are not terminated by ';'. Remove it."),

    /**
     * An obligation or advice stands after an advice.
     * <p>
     * Values: the keyword of the part found.
     */
    MISPLACED_PART_AFTER_ADVICE(
            Identifier.POLICY_PART_ORDER,
            "Misplaced %1$s.",
            "An %1$s cannot follow an advice. The order in a policy is: conditions, obligations, advice, transformation."),

    /**
     * An obligation or advice stands after a transformation.
     * <p>
     * Values: the keyword of the part found.
     */
    MISPLACED_PART_AFTER_TRANSFORMATION(
            Identifier.POLICY_PART_ORDER,
            "Misplaced %1$s.",
            "An %1$s cannot follow a transformation. The order in a policy is: conditions, obligations, advice, transformation."),

    /**
     * A policy has a second transformation.
     */
    SECOND_TRANSFORMATION(
            Identifier.POLICY_PART_ORDER,
            "Second transformation.",
            "A policy can have only one transformation."),

    /**
     * The keyword where of SAPL 3 is used.
     */
    WHERE_KEYWORD(
            "where-keyword",
            "SAPL 3 keyword 'where'.",
            "In SAPL 4 all conditions follow the effect directly, each terminated by ';'. Replace 'where' by ';'."),

    /**
     * Elements of an array are not separated by a comma.
     */
    MISSING_COMMA_IN_ARRAY(
            Identifier.SEPARATOR_MISSING,
            ShortForm.MISSING_COMMA,
            "The elements of an array are separated by ','."),

    /**
     * Arguments of a function or attribute finder are not separated by a comma.
     */
    MISSING_COMMA_IN_ARGUMENTS(
            Identifier.SEPARATOR_MISSING,
            ShortForm.MISSING_COMMA,
            "Arguments are separated by ','."),

    /**
     * Entries of an object are not separated by a comma.
     */
    MISSING_COMMA_IN_OBJECT(
            Identifier.SEPARATOR_MISSING,
            ShortForm.MISSING_COMMA,
            "The entries of an object are separated by ','."),

    /**
     * A comma is not followed by an element.
     */
    TRAILING_COMMA("trailing-comma", "Trailing ','.", "No element follows it. Remove it."),

    /**
     * An object entry lacks the colon after its key.
     * <p>
     * Values: the key as written.
     */
    MISSING_COLON(
            Identifier.OBJECT_ENTRY_MALFORMED,
            "Missing ':'.",
            "After the key %1$s a ':' and a value must follow, for example: { \"type\": \"log\" }"),

    /**
     * An object entry uses the sign of a definition between key and value.
     */
    WRONG_KEY_SEPARATOR(
            Identifier.OBJECT_ENTRY_MALFORMED,
            "Wrong separator '='.",
            "Key and value of an object entry are separated by ':'."),

    /**
     * An object entry has a key that is neither a string nor a name.
     */
    INVALID_OBJECT_KEY(
            Identifier.OBJECT_ENTRY_MALFORMED,
            "Invalid object key.",
            "An object key must be a string or a name, for example: { \"type\": \"log\" }"),

    /**
     * A dot is not followed by a key.
     * <p>
     * Values: the path before the dot as written.
     */
    MISSING_KEY(
            Identifier.KEY_MISSING,
            "Missing key.",
            "After '.' a key must follow, for example: %1$s.name, %1$s.\"first name\" or %1$s.*"),

    /**
     * The recursive descent is not followed by a key.
     * <p>
     * Values: the path before the recursive descent as written.
     */
    MISSING_KEY_AFTER_RECURSIVE_DESCENT(
            Identifier.KEY_MISSING,
            "Missing key.",
            "After '..' a key, '*' or a subscript must follow, for example: %1$s..name"),

    /**
     * A subscript with nothing between its brackets.
     */
    EMPTY_SUBSCRIPT(Identifier.SUBSCRIPT_MALFORMED, "Empty subscript.", Explanation.KINDS_OF_SUBSCRIPT),

    /**
     * A subscript starts with something that no kind of subscript starts with.
     */
    INVALID_SUBSCRIPT(Identifier.SUBSCRIPT_MALFORMED, "Invalid subscript.", Explanation.KINDS_OF_SUBSCRIPT),

    /**
     * A subscript holds an expression that is not in parentheses.
     * <p>
     * Values: the expression as written.
     */
    EXPRESSION_IN_SUBSCRIPT(
            Identifier.SUBSCRIPT_MALFORMED,
            "Missing parentheses.",
            "An expression inside a subscript must be in parentheses: [(%1$s)]"),

    /**
     * A condition step whose condition is not in parentheses.
     * <p>
     * Values: the condition as written.
     */
    CONDITION_WITHOUT_PARENTHESES(
            Identifier.SUBSCRIPT_MALFORMED,
            "Missing parentheses.",
            "The condition of a condition step must be in parentheses: [?(%1$s)]"),

    /**
     * A union of indices and keys.
     */
    MIXED_UNION(
            Identifier.SUBSCRIPT_MALFORMED,
            "Mixed union.",
            "A union selects either indices or keys, not both: [0, 1] or [\"a\", \"b\"]"),

    /**
     * A slice with more than three parts.
     */
    TOO_MANY_SLICE_PARTS(
            Identifier.SUBSCRIPT_MALFORMED,
            "Too many slice parts.",
            "A slice has at most three parts: [from:to:step]"),

    /**
     * One of the keywords any and all is not followed by the keyword in.
     * <p>
     * Values: the keyword found.
     */
    MISSING_IN("operator-incomplete", "Missing 'in'.", "'%1$s' must be followed by 'in'."),

    /**
     * An attribute finder has no name.
     */
    MISSING_ATTRIBUTE_FINDER_NAME(
            "attribute-name-missing",
            "Missing attribute name.",
            "After '<' the name of the attribute finder must follow, for example: <user.roles>"),

    /**
     * The name of a function or attribute finder ends with a dot.
     */
    INCOMPLETE_NAME(
            "name-incomplete",
            "Incomplete name.",
            "After '.' a name must follow. A function or attribute finder is named 'library.name'."),

    /**
     * An attribute finder is not closed.
     * <p>
     * Values: the name of the attribute finder as written.
     */
    UNCLOSED_ATTRIBUTE_FINDER(Identifier.UNCLOSED_BRACKET, "Unclosed attribute finder.", "Add '>' after '%1$s'."),

    /**
     * A filter has no function.
     */
    MISSING_FILTER_FUNCTION(
            Identifier.FILTER_FUNCTION_MISSING,
            ShortForm.MISSING_FILTER_FUNCTION,
            "After '|-' a filter function or a block must follow, for example: resource |- filter.blacken"),

    /**
     * The keyword each of a filter is not followed by the function to apply.
     */
    MISSING_FILTER_FUNCTION_AFTER_EACH(
            Identifier.FILTER_FUNCTION_MISSING,
            ShortForm.MISSING_FILTER_FUNCTION,
            "After 'each' a filter function must follow, which is then applied to every element of an array, for example: resource.names |- each filter.blacken"),

    /**
     * A statement of a filter block does not start with its target.
     */
    MISSING_FILTER_TARGET(
            Identifier.FILTER_STATEMENT_MALFORMED,
            "Missing '@'.",
            "A filter statement starts with its target, which begins with '@', for example: @.name : filter.remove"),

    /**
     * The keyword each in a filter block is not followed by the target of its
     * statement.
     */
    MISSING_FILTER_TARGET_AFTER_EACH(
            Identifier.FILTER_STATEMENT_MALFORMED,
            "Missing '@'.",
            "After 'each' the target of the filter statement must follow, which begins with '@'. The filter function is then applied to every element of the target, for example: each @.names : filter.blacken"),

    /**
     * A statement of a filter block lacks the colon after its target.
     * <p>
     * Values: the target as written, the function that follows as written.
     */
    MISSING_FILTER_COLON(
            Identifier.FILTER_STATEMENT_MALFORMED,
            "Missing ':'.",
            "After the target '%1$s' a ':' and the filter function must follow: %1$s : %2$s"),

    /**
     * A statement of a filter block has no function after its colon.
     */
    FILTER_STATEMENT_WITHOUT_FUNCTION(
            Identifier.FILTER_STATEMENT_MALFORMED,
            ShortForm.MISSING_FILTER_FUNCTION,
            "After the ':' of a filter statement its function must follow, for example: @.name : filter.remove"),

    /**
     * A filter block without statements.
     */
    EMPTY_FILTER_BLOCK(
            Identifier.FILTER_STATEMENT_MALFORMED,
            "Empty filter block.",
            "Add at least one filter statement, for example: @.name : filter.remove"),

    /**
     * Something stands after a complete value where only an operator or a step
     * can follow, and no more specific problem applies.
     * <p>
     * Values: the text found.
     */
    UNEXPECTED_AFTER_VALUE(Identifier.UNEXPECTED_TOKEN, ShortForm.UNEXPECTED, Explanation.EXPECTED_AFTER_VALUE),

    /**
     * Something stands after a complete value where an operator, a step or the
     * end of the construct around the value can follow, and no more specific
     * problem applies.
     * <p>
     * Values: the text found, what ends the construct around the value as
     * {@link #delimiters(List)} lists it.
     */
    UNEXPECTED_AFTER_VALUE_WITH_DELIMITERS(
            Identifier.UNEXPECTED_TOKEN,
            ShortForm.UNEXPECTED,
            "After a value an operator, a step such as '.name' or %2$s is expected."),

    /**
     * Something stands where a value must start, and no more specific problem
     * applies.
     * <p>
     * Values: the text found.
     */
    UNEXPECTED_WHERE_VALUE_EXPECTED(Identifier.UNEXPECTED_TOKEN, ShortForm.UNEXPECTED, Explanation.EXPECTED_VALUE),

    /**
     * Something stands where the document is complete, and no more specific
     * problem applies.
     * <p>
     * Values: the text found.
     */
    UNEXPECTED_WHERE_END_EXPECTED(
            Identifier.UNEXPECTED_TOKEN,
            ShortForm.UNEXPECTED,
            Explanation.EXPECTED_END_OF_DOCUMENT),

    /**
     * Something stands where it cannot follow, and no more specific problem
     * applies.
     * <p>
     * Values: the text found, what can stand there instead as
     * {@link #oneOf(List)} lists it.
     */
    UNEXPECTED_WHERE_ONE_OF_EXPECTED(Identifier.UNEXPECTED_TOKEN, ShortForm.UNEXPECTED, "Here %2$s is expected."),

    /**
     * The document ends after a complete value where an operator or a step must
     * follow, and no more specific problem applies.
     */
    UNEXPECTED_END_AFTER_VALUE(Identifier.UNEXPECTED_TOKEN, ShortForm.UNEXPECTED_END, Explanation.EXPECTED_AFTER_VALUE),

    /**
     * The document ends after a complete value where an operator, a step or the
     * end of the construct around the value must follow, and no more specific
     * problem applies.
     * <p>
     * Values: what ends the construct around the value as
     * {@link #delimiters(List)} lists it.
     */
    UNEXPECTED_END_AFTER_VALUE_WITH_DELIMITERS(
            Identifier.UNEXPECTED_TOKEN,
            ShortForm.UNEXPECTED_END,
            "After a value an operator, a step such as '.name' or %1$s is expected."),

    /**
     * The document ends where a value must start, and no more specific problem
     * applies.
     */
    UNEXPECTED_END_WHERE_VALUE_EXPECTED(
            Identifier.UNEXPECTED_TOKEN,
            ShortForm.UNEXPECTED_END,
            Explanation.EXPECTED_VALUE),

    /**
     * The document ends where nothing more is expected. The catalogue can word
     * this, but no document has this problem.
     */
    UNEXPECTED_END_WHERE_END_EXPECTED(
            Identifier.UNEXPECTED_TOKEN,
            ShortForm.UNEXPECTED_END,
            Explanation.EXPECTED_END_OF_DOCUMENT),

    /**
     * The document ends where more must follow, and no more specific problem
     * applies.
     * <p>
     * Values: what must follow as {@link #oneOf(List)} lists it.
     */
    UNEXPECTED_END_WHERE_ONE_OF_EXPECTED(
            Identifier.UNEXPECTED_TOKEN,
            ShortForm.UNEXPECTED_END,
            "Here %1$s is expected."),

    /**
     * The document has more problems than are reported.
     * <p>
     * Values: the number of problems that are reported.
     */
    TOO_MANY_PROBLEMS(
            "too-many-problems",
            "Too many problems.",
            "Only the first %1$s problems are reported. Resolve them and check the document again.");

    /**
     * What a name is called in a list of what can stand at a place.
     */
    public static final String A_NAME = "a name";

    /**
     * What a number is called in a list of what can stand at a place.
     */
    public static final String A_NUMBER = "a number";

    /**
     * What a text in quotation marks is called in a list of what can stand at a
     * place.
     */
    public static final String A_TEXT = "a text in quotation marks";

    private static final String ERROR_NOTHING_TO_LIST = "A list in a message holds at least one entry.";

    // The texts of a problem name their values by a single digit.
    private static final int MAX_VALUES = 9;

    private final String identifier;
    private final String shortForm;
    private final String explanation;
    private final int    numberOfValues;

    Problem(String identifier, String shortForm, String explanation) {
        this.identifier     = identifier;
        this.shortForm      = shortForm;
        this.explanation    = explanation;
        this.numberOfValues = highestValueNamedIn(shortForm + explanation);
    }

    /**
     * @return the identifier of the class of the problem, which is the same in
     * all implementations of SAPL
     */
    public String identifier() {
        return identifier;
    }

    /**
     * @return the number of values that the texts of the problem name
     */
    public int numberOfValues() {
        return numberOfValues;
    }

    /**
     * Words a keyword or a symbol as a list of what can stand at a place names
     * it.
     *
     * @param token the keyword or symbol as it is written in a document
     * @return the token in single quotation marks
     */
    public static String token(String token) {
        return "'" + token + "'";
    }

    /**
     * Lists what can stand at a place, as a sentence names it: "a", "a or b",
     * "a, b or c".
     *
     * @param alternatives what can stand at the place, each worded by
     * {@link #token(String)} or as {@link #A_NAME}, {@link #A_NUMBER} or
     * {@link #A_TEXT}
     * @return the list
     * @throws IllegalArgumentException if there is no alternative
     */
    public static String oneOf(List<String> alternatives) {
        if (alternatives.isEmpty()) {
            throw new IllegalArgumentException(ERROR_NOTHING_TO_LIST);
        }
        val last = alternatives.getLast();
        if (alternatives.size() == 1) {
            return last;
        }
        return String.join(", ", alternatives.subList(0, alternatives.size() - 1)) + " or " + last;
    }

    /**
     * Lists what can end the construct around a value, as a sentence names it:
     * "';'", "',' or ']'".
     *
     * @param delimiters the delimiters as they are written in a document
     * @return the list
     * @throws IllegalArgumentException if there is no delimiter
     */
    public static String delimiters(List<String> delimiters) {
        if (delimiters.isEmpty()) {
            throw new IllegalArgumentException(ERROR_NOTHING_TO_LIST);
        }
        return delimiters.stream().map(Problem::token).collect(Collectors.joining(" or "));
    }

    /**
     * Writes a small count as a word, as it reads in a sentence. A name that is
     * too long has at least three parts, so the words start there.
     *
     * @param count the count
     * @return the word for a count from three to ten, the digits for any other
     */
    public static String countInWords(int count) {
        return switch (count) {
        case 3  -> "three";
        case 4  -> "four";
        case 5  -> "five";
        case 6  -> "six";
        case 7  -> "seven";
        case 8  -> "eight";
        case 9  -> "nine";
        case 10 -> "ten";
        default -> Integer.toString(count);
        };
    }

    String shortForm(List<String> values) {
        return shortForm.formatted(values.toArray());
    }

    String explanation(List<String> values) {
        return explanation.formatted(values.toArray());
    }

    private static int highestValueNamedIn(String texts) {
        var highest = 0;
        for (var number = 1; number <= MAX_VALUES; number++) {
            if (texts.contains("%" + number + "$s")) {
                highest = number;
            }
        }
        return highest;
    }

    /**
     * The identifiers that several problems share. Each is written once.
     */
    @UtilityClass
    private static class Identifier {
        private static final String CHAINED_COMPARISON            = "chained-comparison";
        private static final String COMBINING_ALGORITHM_MALFORMED = "combining-algorithm-malformed";
        private static final String EFFECT_MISSING                = "effect-missing";
        private static final String FILTER_FUNCTION_MISSING       = "filter-function-missing";
        private static final String FILTER_STATEMENT_MALFORMED    = "filter-statement-malformed";
        private static final String IMPORT_CONFLICT               = "import-conflict";
        private static final String IMPORT_MALFORMED              = "import-malformed";
        private static final String INDEX_NOT_AN_INTEGER          = "index-not-an-integer";
        private static final String INVALID_NAME_CHARACTER        = "invalid-name-character";
        private static final String INVISIBLE_CHARACTER           = "invisible-character";
        private static final String KEY_MISSING                   = "key-missing";
        private static final String KEYWORD_AS_NAME               = "keyword-as-name";
        private static final String MALFORMED_NUMBER              = "malformed-number";
        private static final String MISPLACED_IMPORT_OR_SCHEMA    = "misplaced-import-or-schema";
        private static final String NAME_MISSING                  = "name-missing";
        private static final String OBJECT_ENTRY_MALFORMED        = "object-entry-malformed";
        private static final String POLICY_PART_ORDER             = "policy-part-order";
        private static final String SCHEMA_STATEMENT_MALFORMED    = "schema-statement-malformed";
        private static final String SEPARATOR_MISSING             = "separator-missing";
        private static final String SUBSCRIPT_MALFORMED           = "subscript-malformed";
        private static final String TYPOGRAPHIC_QUOTATION_MARK    = "typographic-quotation-mark";
        private static final String UNCLOSED_BRACKET              = "unclosed-bracket";
        private static final String UNEXPECTED_TOKEN              = "unexpected-token";
        private static final String VALUE_MISSING                 = "value-missing";
        private static final String VARIABLE_DEFINITION_MALFORMED = "variable-definition-malformed";
    }

    /**
     * The short forms that three or more problems share.
     */
    @UtilityClass
    private static class ShortForm {
        private static final String INVISIBLE_CHARACTER     = "Invisible character.";
        private static final String MISSING_COMMA           = "Missing ','.";
        private static final String MISSING_FILTER_FUNCTION = "Missing filter function.";
        private static final String UNEXPECTED              = "Unexpected '%1$s'.";
        private static final String UNEXPECTED_END          = "Unexpected end of document.";
    }

    /**
     * The explanations that several problems share.
     */
    @UtilityClass
    private static class Explanation {
        private static final String EXPECTED_AFTER_VALUE     = "After a value an operator or a step such as '.name' is expected.";
        private static final String EXPECTED_END_OF_DOCUMENT = "Here the end of the document is expected.";
        private static final String EXPECTED_VALUE           = "A value is expected here.";
        private static final String KINDS_OF_SUBSCRIPT       = "Possible are an index [0], a key [\"name\"], all elements [*], a slice [0:2], an expression [(i)] and a condition [?(@ > 1)].";
    }

}
