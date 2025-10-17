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

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Array;
import io.sapl.api.validation.Text;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.Map;

/**
 * Function library providing CSV parsing and generation operations.
 */
@UtilityClass
@FunctionLibrary(name = CsvFunctionLibrary.NAME, description = CsvFunctionLibrary.DESCRIPTION)
public class CsvFunctionLibrary {

    public static final String NAME        = "csv";
    public static final String DESCRIPTION = "Function library for CSV parsing and generation operations.";

    private static final CsvMapper CSV_MAPPER = new CsvMapper();

    /**
     * Parses a CSV document with headers into a SAPL array of objects.
     *
     * @param csv the CSV text to parse, first row must contain headers
     * @return a Val containing an array of objects, one per CSV row
     */
    @SneakyThrows
    @Function(docs = """
            ```csvToVal(TEXT csv)```: Parses a CSV document ```csv``` with headers into a SAPL
            array of objects. The first row is treated as column headers.

            **Example:**
            ```
            import csv.*
            policy "example"
            permit
            where
               var csvText = "name,color,petals\\nPoppy,RED,9\\nRose,PINK,5";
               csvToVal(csvText) == [
                 {"name":"Poppy","color":"RED","petals":"9"},
                 {"name":"Rose","color":"PINK","petals":"5"}
               ];
            ```
            """, schema = """
            {
              "type": "array"
            }""")
    public static Val csvToVal(@Text Val csv) {
        val                                  schema   = CsvSchema.emptySchema().withHeader();
        MappingIterator<Map<String, String>> iterator = CSV_MAPPER.readerFor(Map.class).with(schema)
                .readValues(csv.getText());
        val                                  result   = iterator.readAll();
        return Val.of(CSV_MAPPER.valueToTree(result));
    }

    /**
     * Converts a SAPL array of objects into a CSV string with headers.
     *
     * @param array the array of objects to convert, first object defines headers
     * @return a Val containing the CSV string representation
     */
    @SneakyThrows
    @Function(docs = """
            ```valToCsv(ARRAY array)```: Converts a SAPL ```array``` of objects into a CSV string
            with headers. The keys of the first object determine the column headers.

            **Example:**
            ```
            import csv.*
            policy "example"
            permit
            where
               var data = [
                 {"name":"Poppy","color":"RED","petals":9},
                 {"name":"Rose","color":"PINK","petals":5}
               ];
               var expected = "name,color,petals\\nPoppy,RED,9\\nRose,PINK,5\\n";
               valToCsv(data) == expected;
            ```
            """, schema = """
            {
              "type": "string"
            }""")
    public static Val valToCsv(@Array Val array) {
        if (array.isError() || array.isUndefined()) {
            return array;
        }

        val arrayNode = array.get();
        if (arrayNode.isEmpty()) {
            return Val.of("");
        }

        val firstElement = arrayNode.get(0);
        if (!firstElement.isObject()) {
            return Val.error("CSV array must contain objects");
        }

        val schemaBuilder = CsvSchema.builder();
        firstElement.fieldNames().forEachRemaining(schemaBuilder::addColumn);
        val schema = schemaBuilder.build().withHeader();

        return Val.of(CSV_MAPPER.writer(schema).writeValueAsString(arrayNode));
    }

}
