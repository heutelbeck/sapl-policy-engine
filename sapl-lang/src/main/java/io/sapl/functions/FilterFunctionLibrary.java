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
package io.sapl.functions;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Function library implementing content filtering functions for data transformation
 * in access control policies.
 * <p>
 * This library provides essential functions for redacting, replacing, and removing
 * sensitive data in authorization decisions. These transformations enable fine-grained
 * access control where users receive modified versions of resources based on their
 * authorization level, rather than simple permit/deny decisions.
 */
@UtilityClass
@FunctionLibrary(name = FilterFunctionLibrary.NAME, description = FilterFunctionLibrary.DESCRIPTION,
        libraryDocumentation = """
                # Filter Function Library
                
                The filter library provides essential functions for content transformation in access control policies.
                These functions enable data redaction, replacement, and removal to implement fine-grained authorization
                where different users see different versions of the same resource based on their privileges.
                
                ## Core Functions
                
                - **blacken**: Partially or fully redact text while optionally preserving visible portions
                - **replace**: Substitute values with alternatives
                - **remove**: Delete data elements entirely
                
                ## Access Control Applications
                
                ### Information Disclosure Control
                
                Access control often requires disclosing partial information rather than all-or-nothing access.
                The blacken function supports multiple strategies for controlled disclosure:
                
                **Full Redaction**: Hide all content
                
                Using filter operator:
                ```sapl
                policy "redact_ssn_full"
                permit action.id == "view_user_profile"
                where
                    subject.role == "employee";
                transform
                    resource.ssn |- filter.blacken
                // "123-45-6789" becomes "XXXXXXXXXXX"
                ```
                
                Using direct function call:
                ```sapl
                policy "redact_ssn_in_list"
                permit action == "list_employees"
                where
                    subject.role == "hr_assistant";
                transform
                    resource |- {
                        @..ssn : filter.blacken
                    }
                ```
                
                **Partial Disclosure - Prefix**: Show identifying prefix, hide remainder
                
                ```sapl
                policy "show_account_prefix"
                permit action.id == "view_transaction"
                where
                    subject.role == "auditor";
                transform
                    resource.accountNumber |- filter.blacken(4, 0, "X")
                // "9876543210" becomes "9876XXXXXX"
                // Useful for: account numbers, transaction IDs, reference codes
                ```
                
                Direct call with object template:
                ```sapl
                policy "audit_report_with_masked_accounts"
                permit action.id == "generate_audit_report"
                where
                    subject.department == "internal_audit";
                transform
                    { "accountNumber" : filter.blacken(resource.accountNumber, 4, 0) }
                ```
                
                **Partial Disclosure - Suffix**: Hide prefix, show identifying suffix
                
                ```sapl
                policy "show_ssn_last_four"
                permit action.id == "verify_identity"
                where
                    subject.role == "call_center_agent";
                transform
                    resource.ssn |- filter.blacken(0, 4, "X")
                // "123-45-6789" becomes "XXXXXXX6789"
                // Useful for: SSN last-four, card numbers, phone numbers
                ```
                
                **Partial Disclosure - Both Ends**: Reveal prefix and suffix, hide middle
                
                ```sapl
                policy "partial_email_disclosure"
                permit action.id == "view_contact_info"
                where
                    subject.role == "customer_service";
                transform
                    resource |- {
                        @.email : filter.blacken(3, 12, "*")
                    }
                // "john.doe@company.com" becomes "joh*****@company.com"
                // Useful for: email addresses, names, identifiers
                ```
                
                ### Privacy Protection Through Length Normalization
                
                A critical security concern in data redaction is information leakage through length.
                Without protection, attackers can infer sensitive information from the number of
                redaction characters:
                
                **Problem - Length Reveals Information**:
                ```sapl
                policy "bad_name_redaction"
                permit action.id == "list_users"
                where
                    subject.role == "guest";
                transform
                    resource |- {
                        @..name : filter.blacken
                    }
                // "John" becomes "XXXX" (4 characters)
                // "Elizabeth" becomes "XXXXXXXXX" (9 characters)
                // Attacker knows: second name is longer, can narrow guesses
                ```
                
                **Solution - Fixed-Length Redaction**:
                
                Using filter operator:
                ```sapl
                policy "good_name_redaction"
                permit action.id == "list_users"
                where
                    subject.role == "guest";
                transform
                    resource |- {
                        @..name : filter.blacken(0, 0, "X", 10)
                    }
                // "John" becomes "XXXXXXXXXX"
                // "Elizabeth" becomes "XXXXXXXXXX"
                // Attacker learns nothing from length
                ```
                
                Using direct function call:
                ```sapl
                policy "normalize_patient_names"
                permit action.id == "view_patient_list"
                where
                    subject.role == "nurse";
                transform
                    { "patientName" : filter.blacken(resource.patientName, 0, 0, "█", 15) }
                ```
                
                **Use Cases for Length Override**:
                
                User privacy - Hide name lengths in user lists:
                ```sapl
                policy "user_directory_privacy"
                permit action.id == "search_directory"
                where
                    subject.authenticated == true;
                transform
                    resource.users |- {
                        @..firstName : filter.blacken(2, 0, "*", 8),
                        @..lastName  : filter.blacken(2, 0, "*", 10)
                    }
                ```
               
                ### Data Substitution
                
                The replace function substitutes values while preserving structure:
                
                Using filter operator:
                ```sapl
                policy "sanitize_salary_data"
                permit action.id == "view_employee_details"
                where
                    subject.role != "hr_manager";
                transform
                    resource |- {
                        @.salary : filter.replace(null),
                        @.bonus  : filter.replace(0),
                        @.notes  : filter.replace("REDACTED")
                    }
                ```
              
                Replace with type-specific defaults:
                ```sapl
                policy "default_values_for_restricted_data"
                permit action.id == "api_access"
                where
                    subject.tier == "basic";
                transform
                    resource |- {
                        @.premiumFeatureEnabled : filter.replace(false),
                        @.apiCallLimit          : filter.replace(100),
                        @.supportLevel          : filter.replace("standard")
                    }
                ```
               
                ### Data Removal
                
                The remove function eliminates data elements entirely:
                
                Using filter operator:
                ```sapl
                policy "remove_payment_details"
                permit action.id == "view_order"
                where
                    subject.role == "warehouse_staff";
                transform
                    resource |- {
                        @.creditCardNumber : filter.remove,
                        @.cvv              : filter.remove,
                        @.billingAddress   : filter.remove
                    }
                ```
                
                **Applications**:
                - Delete fields users should not see
                - Remove sensitive array elements
                - Clean data before disclosure
                - Implement least-privilege data access
                
                ## Compliance and Regulatory Requirements
                
                These functions help satisfy regulatory requirements:
                
                - **GDPR**: Minimize data disclosure, implement data minimization
                - **HIPAA**: Protect PHI while allowing necessary information access
                - **PCI DSS**: Mask payment card numbers per requirements
                - **SOX**: Control access to financial data
                - **Classification-based access**: Redact classified portions of documents
                
                ## Best Practices
                
                ### Choosing Between Whitelisting and Blacklisting
                
                **Whitelisting (Object Templates)**: Explicitly construct responses from selected data elements.
                This is the more conservative approach as you define exactly what data is shared. If protected
                services evolve and add new confidential fields, whitelisting prevents accidental exposure since
                new fields are not included in templates by default.
                
                **Blacklisting (Filter Operator)**: Remove or redact specific confidential fields from the resource.
                This approach is more flexible for system evolution since adding new shareable fields does not
                require policy updates. However, it carries the risk of accidentally exposing new confidential data
                if the underlying data model changes.
                
                Choose whitelisting when security is paramount and data schemas are stable. Choose blacklisting
                when flexibility is needed and the risk of schema changes introducing confidential data is managed
                through other means.
                
                ### Redaction Guidelines
                
                1. **Use length normalization** when the length of redacted content could reveal sensitive information
                about the data itself
                2. **Prefer removal over replacement** when data should not be present in the response at all, as
                removal reduces the response size and eliminates any trace of the field
                3. **Use partial disclosure** when users need identifying information (like last four digits of SSN)
                but full content would be excessive
                4. **Consider information inference attacks**: Even partial data can reveal patterns or enable
                correlation attacks when combined with other information
                5. **Combine multiple transformations** when implementing defense-in-depth strategies for highly
                sensitive data
                
                ## Function Parameters
                
                ### blacken Function
                - `original` (required): Text to redact
                - `discloseLeft` (optional, default 0): Characters to keep at start
                - `discloseRight` (optional, default 0): Characters to keep at end
                - `replacement` (optional, default "X"): Character(s) to use for redaction
                - `length` (optional): Override redaction length for privacy protection
                
                ### replace Function
                - `originalValue` (required): Value to replace (ignored if not error)
                - `replacementValue` (required): New value to use
                
                ### remove Function
                - `value` (required): Value to remove (any type)
                
                ## Integration with SAPL Policies
                
                These functions integrate with SAPL's transformation operators and can be used in multiple ways.
                Generally there are two major approaches:
                
                - **Whitelisting with object templates**: Explicitly construct the resource from exactly the data
                to be shared.
                - **Blacklisting using the `|-` filter operator**: Remove confidential data from the resource by
                blackening/obfuscation, replacement, removal, or arbitrary transformation.

                Whitelisting can be considered the more conservative and secure approach, as the policy author knows
                a priori exactly which data will be shared. Even if the data schemas of services protected by SAPL
                change later and this change introduces new confidential data, using explicit whitelisting prevents
                accidental exposure of such new confidential information during overall system evolution.
                
                The filtering solution is more lenient. Here we explicitly specify which information to withhold.
                This makes system evolution easier, as policies do not need updates when more data must be shared.
                However, it implies the risk of accidental exposure of new confidential data.
                
                Selecting an approach is an important design decision.
                
                ### Using the Filter Operator (`|-`) for Blacklisting
                
                The filter operator applies transformations to matched paths:
                
                ```sapl
                policy "multi_field_redaction"
                permit action.id == "view_customer_record"
                where
                    subject.role == "sales_rep";
                transform
                    resource |- {
                        @.ssn           : filter.blacken(0, 4),
                        @.creditScore   : filter.replace(null),
                        @.internalNotes : filter.remove
                    }
                ```
                
                **Note**: In filter expressions, the first argument for the value to be transformed is omitted
                from the function call. This is syntactic sugar to make filters more readable and concise.
                
                ### Templating with Direct Calls to Filter Functions
                
                Functions can be called directly within object templates:
                
                ```sapl
                policy "prepare_external_report"
                permit action.id == "generate_external_report"
                where
                    subject.organization == "partner_company";
                transform
                    {
                        "contactEmail"  : filter.blacken(resource.contactEmail, 2, 10, "*"),
                        "phone"         : filter.blacken(resource.phone, 3, 0, "X", 7),
                        "actualRevenue" : filter.replace(resource.actualRevenue, "UNDISCLOSED")
                        // As this is whitelisting, only data to be shared is included in this template
                        // explicitly. For example, if resource contains profitMargin, it is simply not
                        // listed here, and other fields are explicitly whitelisted.
                    }
                ```
                
                ### Recursive Path Expressions
                
                Apply transformations to all matching elements at any depth:
                
                ```sapl
                policy "sanitize_entire_hierarchy"
                permit action.id == "export_org_structure"
                where
                    subject.role == "external_auditor";
                transform
                    resource |- {
                        @..ssn              : filter.blacken(0, 4),
                        @..salary           : filter.replace(null),
                        @..personalEmail    : filter.blacken(2, 8, "*"),
                        @..emergencyContact : filter.remove
                    }
                ```
                
                ### Array Filtering with Predicates
                
                Selectively remove or transform array elements:
                
                ```sapl
                policy "filter_transaction_array"
                permit action.id == "view_account_transactions"
                where
                    subject.accountOwner == false;
                transform
                    resource.transactions |- {
                        @[?(@.type == "internal")]               : filter.remove,
                        @[?(@.amount > 10000)].counterparty      : filter.blacken(0, 0, "X", 10),
                        @[?(@.category == "sensitive")].memo     : filter.replace("REDACTED")
                    }
                ```

                ### Integration Best Practices
                
                1. **Use the filter operator for blacklisting**: Apply when redacting specific fields from
                existing resource structures
                2. **Use object templates for whitelisting**: Apply when explicitly constructing responses
                from selected data elements
                3. **Combine with path expressions for precision**: Target exact fields or use recursive
                patterns for nested structures
                4. **Apply conditionally based on subject attributes**: Use different policies for different
                user roles to implement role-based data access
                5. **Use array predicates for selective filtering**: Filter or transform specific array
                elements based on their properties
                """
)
public class FilterFunctionLibrary {

    public static final String NAME        = "filter";
    public static final String DESCRIPTION = "Essential functions for content filtering.";

    private static final String ILLEGAL_PARAMETERS_COUNT         = "Illegal number of parameters provided.";
    private static final String ILLEGAL_PARAMETER_DISCLOSE_LEFT  = "Illegal parameter for DISCLOSE_LEFT. Expecting a positive integer.";
    private static final String ILLEGAL_PARAMETER_DISCLOSE_RIGHT = "Illegal parameter for DISCLOSE_RIGHT. Expecting a positive integer.";
    private static final String ILLEGAL_PARAMETER_REPLACEMENT    = "Illegal parameter for REPLACEMENT. Expecting a string.";
    private static final String ILLEGAL_PARAMETER_BLACKEN_LENGTH = "Illegal parameter for BLACKEN_LENGTH. Expecting a positive integer.";
    private static final String ILLEGAL_PARAMETER_STRING         = "Illegal parameter for STRING. Expecting a string.";

    private static final int    ORIGINAL_STRING_INDEX                    = 0;
    private static final int    DISCLOSE_LEFT_INDEX                      = 1;
    private static final int    DISCLOSE_RIGHT_INDEX                     = 2;
    private static final int    REPLACEMENT_INDEX                        = 3;
    private static final int    BLACKEN_LENGTH_INDEX                     = 4;
    private static final int    MAXIMAL_NUMBER_OF_PARAMETERS_FOR_BLACKEN = 5;
    private static final int    DEFAULT_NUMBER_OF_CHARACTERS_TO_SHOW_LEFT  = 0;
    private static final int    DEFAULT_NUMBER_OF_CHARACTERS_TO_SHOW_RIGHT = 0;
    private static final String DEFAULT_REPLACEMENT                        = "X";

    /**
     * Replaces a section of a text with a fixed character.
     *
     * @param parameters STRING (original textual Val), DISCLOSE_LEFT leave this
     * number of characters original on the left side of the string, DISCLOSE_RIGHT
     * leave this number of characters original on the right side of the string,
     * REPLACEMENT the replacement characters, defaulting to X, BLACKEN_LENGTH the
     * number of replacement characters to use, overriding the calculated length.
     * @return the original Text value with the indicated characters replaced with
     * the replacement characters.
     */
    @Function(docs = """
            ```blacken(TEXT original[, INTEGER>=0 discloseLeft][, INTEGER>=0 discloseRight][, TEXT replacement][, INTEGER>=0 length])```:
            This function can be used to partially blacken text in data.
            The function requires that ```discloseLeft```, ```discloseRight```, and ```length``` are integers >= 0.
            Also, ```original``` and ```replacement``` must be text strings.
            The function replaces each character in ```original``` with ```replacement```, while leaving ```discloseLeft```
            characters from the beginning and ```discloseRight``` characters from the end unchanged.

            If ```length``` is provided, the number of characters replaced is set to ```length```, for example, to
            ensure that string length does not leak any information.
            If ```length``` is not provided, it will replace all characters that are not disclosed.

            When ```discloseLeft``` + ```discloseRight``` >= length of ```original```, the original string is returned unchanged.

            Except for ```original```, all parameters are optional.

            **Defaults**:

            ```discloseLeft``` defaults to ```0```, ```discloseRight``` defaults to ```0```
            and ```replacement``` defaults to ```"X"```.
            The function returns the modified ```original```.

            **Example:**

            Given a subscription:
            ```json
            {
              "resource" : {
                             "array" : [ null, true ],
                             "key1"  : "abcde"
                           }
            }
            ```

            And the policy:
            ```sapl
            policy "test"
            permit
            transform resource |- {
                                    @.key1 : filter.blacken(1)
                                  }
            ```

            The decision will contain a ```resource``` as follows:
            ```json
            {
              "array" : [ null, true ],
              "key1"  : "aXXXX"
            }
            ```
            """)
    public static Val blacken(Val... parameters) {
        validateParameterCount(parameters);
        val originalString = extractOriginalText(parameters);
        val replacement    = extractReplacement(parameters);
        val discloseRight  = extractDiscloseRight(parameters);
        val discloseLeft   = extractDiscloseLeft(parameters);
        val blackenLength  = extractBlackenLength(parameters);
        return blacken(originalString, replacement, discloseRight, discloseLeft, blackenLength);
    }

    private static Val blacken(String originalString, String replacement, int discloseRight, int discloseLeft,
                               Integer blackenLength) {
        return Val.of(blackenUtil(originalString, replacement, discloseRight, discloseLeft, blackenLength));
    }

    /**
     * Utility method to blacken a string with optional length override.
     *
     * @param originalString the original string to blacken
     * @param replacement the replacement character(s)
     * @param discloseRight number of characters to keep on the right
     * @param discloseLeft number of characters to keep on the left
     * @param blackenLength override length for replacement characters, or null to use calculated length
     * @return the blackened string
     */
    public static String blackenUtil(String originalString, String replacement, int discloseRight, int discloseLeft,
                                     Integer blackenLength) {
        if (discloseLeft + discloseRight >= originalString.length())
            return originalString;

        val result        = new StringBuilder();
        val replacedChars = originalString.length() - discloseLeft - discloseRight;

        if (discloseLeft > 0) {
            result.append(originalString, 0, discloseLeft);
        }

        val blackenFinalLength = blackenLength != null ? blackenLength : replacedChars;
        result.append(String.valueOf(replacement).repeat(blackenFinalLength));

        if (discloseRight > 0) {
            result.append(originalString.substring(discloseLeft + replacedChars));
        }

        return result.toString();
    }

    /**
     * Extracts a positive integer parameter from the parameters array.
     *
     * @param parameters the parameters array
     * @param index the index of the parameter to extract
     * @param defaultValue the default value if parameter is not present
     * @param errorMessage the error message if parameter is invalid
     * @return the extracted integer or the default value
     * @throws IllegalArgumentException if parameter is present but invalid
     */
    private static int extractPositiveIntParameter(Val[] parameters, int index, int defaultValue, String errorMessage) {
        if (hasNoParameterAtIndex(parameters.length, index)) {
            return defaultValue;
        }

        val parameter = parameters[index];
        if (!parameter.isNumber() || parameter.get().asInt() < 0) {
            throw new IllegalArgumentException(errorMessage);
        }
        return parameter.get().asInt();
    }

    private static int extractDiscloseLeft(Val... parameters) {
        return extractPositiveIntParameter(parameters, DISCLOSE_LEFT_INDEX,
                DEFAULT_NUMBER_OF_CHARACTERS_TO_SHOW_LEFT,
                ILLEGAL_PARAMETER_DISCLOSE_LEFT);
    }

    private static int extractDiscloseRight(Val... parameters) {
        return extractPositiveIntParameter(parameters, DISCLOSE_RIGHT_INDEX,
                DEFAULT_NUMBER_OF_CHARACTERS_TO_SHOW_RIGHT,
                ILLEGAL_PARAMETER_DISCLOSE_RIGHT);
    }

    private static String extractReplacement(Val... parameters) {
        if (hasNoParameterAtIndex(parameters.length, REPLACEMENT_INDEX)) {
            return DEFAULT_REPLACEMENT;
        }

        if (!parameters[REPLACEMENT_INDEX].isTextual()) {
            throw new IllegalArgumentException(ILLEGAL_PARAMETER_REPLACEMENT);
        }
        return parameters[REPLACEMENT_INDEX].get().asText();
    }

    private static String extractOriginalText(Val... parameters) {
        if (hasNoParameterAtIndex(parameters.length, ORIGINAL_STRING_INDEX)
                || !parameters[ORIGINAL_STRING_INDEX].isTextual()) {
            throw new IllegalArgumentException(ILLEGAL_PARAMETER_STRING);
        }

        return parameters[ORIGINAL_STRING_INDEX].get().asText();
    }

    /**
     * Extracts the blacken length from parameters.
     *
     * @param parameters the function parameters
     * @return the blacken length if provided, otherwise null
     * @throws IllegalArgumentException if the length parameter is not a positive integer
     */
    private static Integer extractBlackenLength(Val... parameters) {
        if (hasNoParameterAtIndex(parameters.length, BLACKEN_LENGTH_INDEX)) {
            return null;
        }

        val parameter = parameters[BLACKEN_LENGTH_INDEX];
        if (!parameter.isNumber() || parameter.get().asInt() < 0) {
            throw new IllegalArgumentException(ILLEGAL_PARAMETER_BLACKEN_LENGTH);
        }

        return parameter.get().asInt();
    }

    private static boolean hasNoParameterAtIndex(int parameterCount, int parameterIndex) {
        return parameterCount < parameterIndex + 1;
    }

    private static void validateParameterCount(Val... parameters) {
        if (parameters.length > MAXIMAL_NUMBER_OF_PARAMETERS_FOR_BLACKEN) {
            throw new IllegalArgumentException(ILLEGAL_PARAMETERS_COUNT);
        }
    }

    /**
     * Replaces the original with another value.
     *
     * @param original the original value, which is ignored.
     * @param replacement a replacement value.
     * @return the replacement value.
     */
    @Function(docs = """
            ```replace(originalValue, replacementValue)```:
            The function will map the ```originalValue``` to the replacement value.
            If the original value is an error, it will not be replaced and it bubbles up the evaluation chain.
            If the original value is ```undefined``` it will be replaced with the ```replacementValue```.

            **Example:**

            Given a subscription:
            ```json
            {
              "resource" : {
                             "array" : [ null, true ],
                             "key1"  : "abcde"
                           }
            }
            ```

            And the policy:
            ```sapl
            policy "test"
            permit
            transform resource |- {
                                    @.array[1] : filter.replace("***"),
                                    @.key1     : filter.replace(null)
                                  }
            ```

            The decision will contain a ```resource``` as follows:
            ```json
            {
              "array" : [ null, "***" ],
              "key1"  : null
            }
            ```
            """)
    public static Val replace(Val original, Val replacement) {
        if (original.isError()) {
            return original;
        }
        return replacement;
    }

    /**
     * Replaces any value with UNDEFINED.
     *
     * @param original some value
     * @return Val.UNDEFINED
     */
    @Function(docs = """
            ```remove(value)```: This function maps any ```value``` to ```undefined```.
            In filters, ```undefined``` elements of arrays and objects will be silently removed.

            **Example:**

            The expression ```[ 0, 1, 2, 3, 4, 5 ] |- { @[-2:] : filter.remove }``` results in ```[0, 1, 2, 3]```.

            Given a subscription:
            ```json
            {
              "resource" : {
                             "array" : [ null, true ],
                             "key1"  : "abcde"
                           }
            }
            ```

            And the policy:
            ```sapl
            policy "test"
            permit
            transform resource |- {
                                    @.key1 : filter.remove
                                  }
            ```

            The decision will contain a ```resource``` as follows:
            ```json
            {
              "array" : [ null, true ]
            }
            ```
            """)
    public static Val remove(Val original) {
        return Val.UNDEFINED;
    }
}