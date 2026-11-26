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
package io.sapl.functions.libraries;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.model.*;
import lombok.val;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.MappingIterator;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.dataformat.csv.CsvFactory;
import tools.jackson.dataformat.csv.CsvMapper;
import tools.jackson.dataformat.csv.CsvWriteFeature;
import tools.jackson.dataformat.csv.CsvSchema;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;

/**
 * CSV parsing and generation for authorization policies.
 * <p>
 * Note: CSV parsing loads the entire document into memory. For very large CSV
 * files (100,000+ rows), consider
 * processing in chunks or using external data sources.
 */
@FunctionLibrary(name = CsvFunctionLibrary.NAME, description = CsvFunctionLibrary.DESCRIPTION, libraryDocumentation = CsvFunctionLibrary.DOCUMENTATION)
public class CsvFunctionLibrary {

    public static final String NAME          = "csv";
    public static final String DESCRIPTION   = "CSV parsing and generation for authorization policies.";
    public static final String DOCUMENTATION = """
            # CSV Operations

            Parse CSV documents into SAPL data structures for authorization decisions based on
            tabular data. Convert SAPL arrays into CSV format for audit logs and reports.
            Process user lists, permission matrices, and configuration tables stored as CSV.

            ## Core Principles

            CSV parsing treats the first row as column headers. Each subsequent row becomes an
            object with properties named after the headers. All values are parsed as strings.
            Empty records are skipped, an initial byte order mark is removed, and duplicate headers
            are rejected. Field spaces are preserved. Output records end in CRLF.
            Type conversion must be done explicitly in policies. CSV generation takes an array
            of objects and uses the first object's keys as column headers. Empty arrays produce
            empty CSV output. Cells must be text, numbers, booleans or null. Arrays, objects,
            undefined values and errors are rejected. Distinct field names in one object must remain
            distinct after formula escaping; a collision rejects the entire export.

            ## Access Control Patterns

            Parse allowlists from CSV files to check if users or resources are permitted.
            Compare incoming requests against lists maintained in external systems.

            ```sapl
            policy "check_allowlist"
            permit action == "access_resource";
                var allowlistCsv = resource.config.allowedUsers;
                var allowlist = csv.csvToVal(allowlistCsv);
                allowlist[?(@.username == subject.username)] != [];
            ```

            Parse permission matrices that define which roles can perform which actions on
            which resource types. Match the current request against the matrix.

            ```sapl
            policy "permission_matrix"
            permit
                var matrixCsv = environment.permissionMatrix;
                var matrix = csv.csvToVal(matrixCsv);
                var matches = matrix[?(@.role == subject.role &&
                                      @.action == action.name &&
                                      @.resourceType == resource.type)];
                matches != [];
            ```

            Validate bulk operations by parsing uploaded CSV files and checking each row
            against authorization rules before processing.

            ```sapl
            policy "bulk_import"
            permit action == "import_users";
                var uploadedCsv = resource.fileContent;
                var users = csv.csvToVal(uploadedCsv);
                var invalidUsers = users[?(!(@.domain in subject.allowedDomains))];
                invalidUsers == [];
            ```

            Generate audit logs as CSV for compliance reporting. Convert policy evaluation
            results into tabular format for analysis.

            ```sapl
            policy "audit_access"
            permit
            obligation
                {
                    "type": "audit",
                    "format": "csv",
                    "data": csv.valToCsv([{
                        "user": subject.username,
                        "action": action.name,
                        "resource": resource.id,
                        "timestamp": |<time.now>
                    }])
                }
            ```

            Parse department hierarchies or organizational structures from CSV to determine
            authorization scope based on reporting relationships.

            ```sapl
            policy "hierarchical_access"
            permit action == "view_employee_data";
                var orgStructure = csv.csvToVal(environment.organizationCsv);
                orgStructure[?(@.managerId == subject.employeeId &&
                               @.employeeId == resource.employeeId)] != [];
            ```

            Parse configuration tables that map resource types to required permissions.
            Look up the required permissions for the requested resource type.

            ```sapl
            policy "resource_permissions"
            permit
                var configCsv = environment.resourcePermissionConfig;
                var config = csv.csvToVal(configCsv);
                var matches = config[?(@.resourceType == resource.type)];
                matches != [];
                matches[0].permissions in subject.permissions;
            ```

            ## Limits

            To bound memory and computation on untrusted input, the following limits apply:

            - The input is limited to 1 MB.
            - Serialization output is limited to 10000000 characters.

            These limits apply because this input may originate from the authorization subscription or from policy information points, which are not vetted to the same degree as the policies and variables shipped with the PDP configuration.
            """;

    private static final CsvMapper CSV_MAPPER = CsvMapper
            .builder(CsvFactory.builder().streamReadConstraints(TextParseLimits.STREAM_READ_CONSTRAINTS).build())
            .enable(CsvWriteFeature.STRICT_CHECK_FOR_QUOTING,
                    CsvWriteFeature.QUOTE_STRINGS_WITH_LEADING_TRAILING_WHITESPACE)
            .build();

    private static final String ERROR_ARRAY_ELEMENT_NOT_OBJECT   = "CSV array element at index %d is not an object.";
    private static final String ERROR_ARRAY_MUST_CONTAIN_OBJECTS = "CSV array must contain objects.";
    private static final String ERROR_CELL_MUST_BE_SCALAR        = "CSV fields must be texts, numbers, booleans or null.";
    private static final String ERROR_DUPLICATE_HEADER           = "Duplicate CSV header.";
    private static final String ERROR_FAILED_TO_GENERATE_CSV     = "Failed to generate CSV: %s";
    private static final String ERROR_FAILED_TO_PARSE_CSV        = "Failed to parse CSV: %s";
    private static final String ERROR_NO_COLUMNS                 = "CSV output requires at least one column.";

    /**
     * Parses a CSV document with headers into a SAPL array of objects.
     *
     * @param csv
     * CSV text with headers in first row
     *
     * @return Array of objects, one per data row, or an ErrorValue if parsing fails
     */
    @Function(docs = """
            ```csv.csvToVal(TEXT csv)```

            Parses a CSV document with headers into a SAPL array of objects. The first row is
            treated as column headers, and each subsequent row becomes an object with properties
            named after those headers. All values are parsed as strings. Empty records are skipped,
            an initial byte order mark is removed, and duplicate headers are rejected.

            Input longer than 1 MB (1048576 characters) is rejected with an error, so a hostile
            attribute value cannot exhaust the evaluation thread.

            Parameters:
            - csv: CSV text with headers in first row

            Returns: Array of objects, one per data row

            Example: parse user allowlist
            ```sapl
            policy "example"
            permit
                var csvText = "username,department\\nalice,engineering\\nbob,sales";
                var users = csv.csvToVal(csvText);
                users[?(@.username == subject.username)] != [];
            ```
            """, schema = """
            {
              "type": "array"
            }""")
    public static Value csvToVal(TextValue csv) {
        if (TextParseLimits.exceedsMaxInput(csv.value())) {
            return Value.error(TextParseLimits.ERROR_INPUT_TOO_LARGE, TextParseLimits.MAX_INPUT_CHARS);
        }
        val input = csv.value();
        int start = input.startsWith("\uFEFF") ? 1 : 0;
        while (start < input.length() && (input.charAt(start) == '\r' || input.charAt(start) == '\n')) {
            start++;
        }
        val text   = input.substring(start);
        val schema = CsvSchema.emptySchema().withHeader();

        try (MappingIterator<Map<String, String>> iterator = CSV_MAPPER.readerFor(Map.class).with(schema)
                .readValues(text)) {
            val names = new HashSet<String>();
            for (val column : (CsvSchema) iterator.parserSchema()) {
                if (!names.add(column.getName())) {
                    return Value.error(ERROR_FAILED_TO_PARSE_CSV, ERROR_DUPLICATE_HEADER);
                }
            }
            val resultBuilder = ArrayValue.builder();
            while (iterator.hasNextValue()) {
                // Jackson reports the last consumed character.
                val recordStart = iterator.currentLocation().getCharOffset() + 1;
                val row         = iterator.nextValue();
                val recordEnd   = iterator.currentLocation().getCharOffset() + 1;
                if (isEmptyRecord(text, recordStart, recordEnd)) {
                    continue;
                }
                val objectBuilder = ObjectValue.builder();
                for (val entry : row.entrySet()) {
                    objectBuilder.put(entry.getKey(), Value.of(entry.getValue()));
                }
                resultBuilder.add(objectBuilder.build());
            }
            return resultBuilder.build();
        } catch (JacksonException exception) {
            return Value.error(ERROR_FAILED_TO_PARSE_CSV, sentence(exception.getOriginalMessage()));
        }
    }

    /**
     * Converts a SAPL array of objects into a CSV string with headers.
     *
     * @param array
     * Array of objects to convert
     *
     * @return CSV string with headers, or an ErrorValue if conversion fails
     */
    @Function(docs = """
            ```csv.valToCsv(ARRAY array)```

            Converts a SAPL array of objects into a CSV string with headers. The keys of the
            first object determine the column headers. Subsequent objects should have the same
            keys for consistent output. Returns an empty string for empty arrays. Cells must be text,
            numbers, booleans or null. If two distinct field names in any object become the same
            header after formula escaping, the entire export is rejected. Records end in CRLF.
            Fields are quoted when they contain commas, double quotes or line breaks, or start or end
            with an ASCII space or C0 control character. Double quotes inside quoted fields are doubled.
            Whole numbers in the signed 64-bit range are written as plain integers, for example `100.00` as `100`.

            To prevent CSV formula injection, a cell whose text begins with =, +, -, @, a tab,
            or a carriage return is prefixed with a single quote, so a spreadsheet that opens
            the output treats it as text rather than a formula.

            Parameters:
            - array: Array of objects to convert

            Returns: CSV string with headers

            Example: generate audit log
            ```sapl
            policy "example"
            permit
            obligation
                {
                    "type": "audit",
                    "log": csv.valToCsv([{
                        "user": subject.username,
                        "action": action.name,
                        "resource": resource.id
                    }])
                }
            ```
            """, schema = """
            {
              "type": "string"
            }""")
    public static Value valToCsv(ArrayValue array) {
        if (array.isEmpty()) {
            return Value.EMPTY_TEXT;
        }

        val firstElement = array.getFirst();
        if (!(firstElement instanceof ObjectValue)) {
            return Value.error(ERROR_ARRAY_MUST_CONTAIN_OBJECTS);
        }

        for (int i = 0; i < array.size(); i++) {
            if (!(array.get(i) instanceof ObjectValue)) {
                return Value.error(ERROR_ARRAY_ELEMENT_NOT_OBJECT, i);
            }
        }

        try {
            val sanitized     = sanitizeAgainstFormulaInjection(array);
            val schemaBuilder = CsvSchema.builder();
            for (val key : ((ObjectValue) sanitized.getFirst()).keySet()) {
                schemaBuilder.addColumn(key);
            }
            val schema = schemaBuilder.build().withHeader().withLineSeparator("\r\n");
            if (schema.size() == 0) {
                return Value.error(ERROR_FAILED_TO_GENERATE_CSV, ERROR_NO_COLUMNS);
            }
            val arrayNode = convertToJacksonArrayNode(sanitized);
            val csvText   = TextOutputLimits.write(writer -> CSV_MAPPER.writer(schema).writeValue(writer, arrayNode));
            return Value.of(csvText);
        } catch (TextOutputLimits.OutputLimitExceededException exception) {
            return Value.error(exception.getMessage());
        } catch (JacksonException exception) {
            return Value.error(ERROR_FAILED_TO_GENERATE_CSV, sentence(exception.getOriginalMessage()));
        } catch (IllegalArgumentException | IOException exception) {
            return Value.error(ERROR_FAILED_TO_GENERATE_CSV, sentence(exception.getMessage()));
        }
    }

    private static String sentence(String reason) {
        return reason.endsWith(".") ? reason : reason + ".";
    }

    private static boolean isEmptyRecord(String text, long start, long end) {
        val length = end - start;
        if (start < 0 || start >= text.length()) {
            return false;
        }
        val first = text.charAt((int) start);
        return (length == 1 && (first == '\r' || first == '\n'))
                || (length == 2 && first == '\r' && text.charAt((int) start + 1) == '\n');
    }

    private static ArrayNode convertToJacksonArrayNode(ArrayValue array) {
        return (ArrayNode) ValueJsonMarshaller.toJsonNode(array);
    }

    /**
     * Neutralizes CSV formula injection. A cell whose text starts with a
     * formula trigger is prefixed with a single quote so a spreadsheet that
     * later opens the generated CSV treats it as text, not an expression. The
     * same neutralization is applied to object keys, since they become the
     * header-row cells of the generated CSV.
     */
    private static ArrayValue sanitizeAgainstFormulaInjection(ArrayValue array) {
        val builder = ArrayValue.builder();
        for (int i = 0; i < array.size(); i++) {
            if (array.get(i) instanceof ObjectValue object) {
                val objectBuilder = ObjectValue.builder();
                for (val entry : object.entrySet()) {
                    objectBuilder.put(escapeFormula(entry.getKey()), escapeFormulaCell(entry.getValue()));
                }
                val sanitized = objectBuilder.build();
                if (sanitized.size() != object.size()) {
                    throw new IllegalArgumentException(ERROR_DUPLICATE_HEADER);
                }
                builder.add(sanitized);
            } else {
                builder.add(array.get(i));
            }
        }
        return builder.build();
    }

    private static Value escapeFormulaCell(Value cell) {
        if (cell instanceof TextValue(String text)) {
            return Value.of(escapeFormula(text));
        }
        if (cell instanceof NumberValue number) {
            return Value.of(NumberValueLimits.textFormatNumber(number.value()));
        }
        if (cell instanceof NullValue || cell instanceof BooleanValue) {
            return cell;
        }
        throw new IllegalArgumentException(ERROR_CELL_MUST_BE_SCALAR);
    }

    private static String escapeFormula(String text) {
        if (startsWithFormulaCharacter(text)) {
            return "'" + text;
        }
        return text;
    }

    private static boolean startsWithFormulaCharacter(String text) {
        if (text.isEmpty()) {
            return false;
        }
        val first = text.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r';
    }
}
