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

import com.fasterxml.jackson.core.JsonProcessingException;
import io.sapl.api.interpreter.Val;
import lombok.val;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CsvFunctionLibraryTests {

    @Test
    void csvToValParsesSimpleCsv() {
        val csv    = """
                name,color,petals
                Poppy,RED,9
                Rose,PINK,5
                """;
        val result = CsvFunctionLibrary.csvToVal(Val.of(csv));

        assertThat(result.get().isArray()).isTrue();
        assertThat(result.get().size()).isEqualTo(2);

        val firstRow = result.get().get(0);
        assertThat(firstRow.get("name").asText()).isEqualTo("Poppy");
        assertThat(firstRow.get("color").asText()).isEqualTo("RED");
        assertThat(firstRow.get("petals").asText()).isEqualTo("9");

        val secondRow = result.get().get(1);
        assertThat(secondRow.get("name").asText()).isEqualTo("Rose");
        assertThat(secondRow.get("color").asText()).isEqualTo("PINK");
        assertThat(secondRow.get("petals").asText()).isEqualTo("5");
    }

    @Test
    void csvToValHandlesSingleRow() {
        val csv    = """
                name,age
                Alice,30
                """;
        val result = CsvFunctionLibrary.csvToVal(Val.of(csv));

        assertThat(result.get().size()).isEqualTo(1);
        assertThat(result.get().get(0).get("name").asText()).isEqualTo("Alice");
        assertThat(result.get().get(0).get("age").asText()).isEqualTo("30");
    }

    @Test
    void csvToValHandlesSingleColumn() {
        val csv    = """
                name
                Alice
                Bob
                Charlie
                """;
        val result = CsvFunctionLibrary.csvToVal(Val.of(csv));

        assertThat(result.get().size()).isEqualTo(3);
        assertThat(result.get().get(0).get("name").asText()).isEqualTo("Alice");
        assertThat(result.get().get(1).get("name").asText()).isEqualTo("Bob");
        assertThat(result.get().get(2).get("name").asText()).isEqualTo("Charlie");
    }

    @Test
    void csvToValHandlesEmptyCells() {
        val csv    = """
                name,age,city
                Alice,30,
                Bob,,London
                ,,Paris
                """;
        val result = CsvFunctionLibrary.csvToVal(Val.of(csv));

        assertThat(result.get().size()).isEqualTo(3);
        assertThat(result.get().get(0).get("city").asText()).isEmpty();
        assertThat(result.get().get(1).get("age").asText()).isEmpty();
    }

    @Test
    void csvToValHandlesQuotedFields() {
        val csv    = """
                name,description
                "Smith, John","A person named John"
                "Doe, Jane","Another person"
                """;
        val result = CsvFunctionLibrary.csvToVal(Val.of(csv));

        assertThat(result.get().get(0).get("name").asText()).isEqualTo("Smith, John");
        assertThat(result.get().get(0).get("description").asText()).isEqualTo("A person named John");
    }

    @Test
    void csvToValHandlesQuotesInQuotedFields() {
        val csv    = """
                name,quote
                Alice,"She said ""Hello\"""
                """;
        val result = CsvFunctionLibrary.csvToVal(Val.of(csv));

        assertThat(result.get().get(0).get("quote").asText()).contains("Hello");
    }

    @Test
    void csvToValHandlesNewlinesInQuotedFields() {
        val csv    = """
                name,address
                Alice,"123 Main St
                Apt 4B"
                """;
        val result = CsvFunctionLibrary.csvToVal(Val.of(csv));

        assertThat(result.get().get(0).get("address").asText()).contains("Main St");
        assertThat(result.get().get(0).get("address").asText()).contains("Apt 4B");
    }

    @Test
    void csvToValHandlesHeadersWithSpaces() {
        val csv    = """
                First Name,Last Name,Age
                Alice,Smith,30
                """;
        val result = CsvFunctionLibrary.csvToVal(Val.of(csv));

        assertThat(result.get().get(0).has("First Name")).isTrue();
        assertThat(result.get().get(0).has("Last Name")).isTrue();
    }

    @Test
    void csvToValHandlesUnicodeCharacters() {
        val csv    = """
                name,message
                Alice,Hello 世界 🌸
                """;
        val result = CsvFunctionLibrary.csvToVal(Val.of(csv));

        assertThat(result.get().get(0).get("message").asText()).isEqualTo("Hello 世界 🌸");
    }

    @Test
    void csvToValHandlesOnlyHeaders() {
        val csv    = "name,age,city";
        val result = CsvFunctionLibrary.csvToVal(Val.of(csv));

        assertThat(result.get().isArray()).isTrue();
        assertThat(result.get().size()).isZero();
    }

    @Test
    void csvToValHandlesManyRows() {
        val csvBuilder = new StringBuilder("id,value\n");
        for (int i = 0; i < 100; i++) {
            csvBuilder.append(i).append(",value").append(i).append('\n');
        }

        val result = CsvFunctionLibrary.csvToVal(Val.of(csvBuilder.toString()));

        assertThat(result.get().size()).isEqualTo(100);
        assertThat(result.get().get(0).get("id").asText()).isEqualTo("0");
        assertThat(result.get().get(99).get("id").asText()).isEqualTo("99");
    }

    @Test
    void valToCsvConvertsArrayOfObjectsToCsv() throws JsonProcessingException {
        val array = Val.ofJson("""
                [
                  {"name":"Poppy","color":"RED","petals":9},
                  {"name":"Rose","color":"PINK","petals":5}
                ]
                """);

        val result = CsvFunctionLibrary.valToCsv(array);
        val lines  = result.getText().split("\n");

        assertThat(lines[0]).contains("name");
        assertThat(lines[0]).contains("color");
        assertThat(lines[0]).contains("petals");
        assertThat(lines[1]).contains("Poppy");
        assertThat(lines[1]).contains("RED");
        assertThat(lines[1]).contains("9");
    }

    @Test
    void valToCsvHandlesSingleObject() throws JsonProcessingException {
        val array = Val.ofJson("[{\"name\":\"Alice\",\"age\":30}]");

        val result = CsvFunctionLibrary.valToCsv(array);

        assertThat(result.getText()).contains("name");
        assertThat(result.getText()).contains("age");
        assertThat(result.getText()).contains("Alice");
        assertThat(result.getText()).contains("30");
    }

    @Test
    void valToCsvHandlesEmptyArray() {
        val result = CsvFunctionLibrary.valToCsv(Val.ofEmptyArray());
        assertThat(result.getText()).isEmpty();
    }

    @Test
    void valToCsvHandlesConsistentSchemaAcrossRows() throws JsonProcessingException {
        val array = Val.ofJson("""
                [
                  {"name":"Alice","age":30},
                  {"name":"Bob","age":25}
                ]
                """);

        val result = CsvFunctionLibrary.valToCsv(array);
        val lines  = result.getText().split("\n");

        assertThat(lines[0]).contains("name");
        assertThat(lines[0]).contains("age");
        assertThat(lines[1]).contains("Alice");
        assertThat(lines[2]).contains("Bob");
    }

    @Test
    void valToCsvHandlesSpecialCharacters() throws JsonProcessingException {
        val array = Val.ofJson("[{\"name\":\"Smith, John\",\"note\":\"He said \\\"Hi\\\"\"}]");

        val result = CsvFunctionLibrary.valToCsv(array);

        assertThat(result.getText()).contains("Smith");
    }

    @Test
    void valToCsvReturnsErrorForErrorValue() {
        val error  = Val.error("Test error");
        val result = CsvFunctionLibrary.valToCsv(error);
        assertThat(result.isError()).isTrue();
    }

    @Test
    void valToCsvReturnsUndefinedForUndefinedValue() {
        val undefined = Val.UNDEFINED;
        val result    = CsvFunctionLibrary.valToCsv(undefined);
        assertThat(result.isUndefined()).isTrue();
    }

    @Test
    void roundTripConversionPreservesData() throws JsonProcessingException {
        val original = Val.ofJson("""
                [
                  {"name":"Charlie","age":35,"city":"Paris"},
                  {"name":"Diana","age":28,"city":"Tokyo"}
                ]
                """);

        val csvString = CsvFunctionLibrary.valToCsv(original);
        val restored  = CsvFunctionLibrary.csvToVal(csvString);

        assertThat(restored.get().size()).isEqualTo(2);
        assertThat(restored.get().get(0).get("name").asText()).isEqualTo("Charlie");
        assertThat(restored.get().get(0).get("city").asText()).isEqualTo("Paris");
        assertThat(restored.get().get(1).get("name").asText()).isEqualTo("Diana");
    }

    @Test
    void csvToValHandlesWindowsLineEndings() {
        val csv    = "name,age\r\nAlice,30\r\nBob,25\r\n";
        val result = CsvFunctionLibrary.csvToVal(Val.of(csv));

        assertThat(result.get().size()).isEqualTo(2);
        assertThat(result.get().get(0).get("name").asText()).isEqualTo("Alice");
    }

    @Test
    void csvToValHandlesUnixLineEndings() {
        val csv    = "name,age\nAlice,30\nBob,25\n";
        val result = CsvFunctionLibrary.csvToVal(Val.of(csv));

        assertThat(result.get().size()).isEqualTo(2);
        assertThat(result.get().get(0).get("name").asText()).isEqualTo("Alice");
    }

    @Test
    void csvToValHandlesTrailingComma() {
        val csv    = """
                name,age,
                Alice,30,
                """;
        val result = CsvFunctionLibrary.csvToVal(Val.of(csv));

        assertThat(result.get().size()).isEqualTo(1);
    }

}
