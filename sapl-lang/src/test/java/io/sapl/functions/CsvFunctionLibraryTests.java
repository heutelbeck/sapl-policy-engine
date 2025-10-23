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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Tests for CsvFunctionLibrary verifying CSV parsing and generation.
 * <p/>
 * Test Coverage:
 * - csvToVal: Parsing various CSV formats (quoted fields, empty cells, line
 * endings, unicode)
 * - valToCsv: Generating CSV from SAPL arrays with comprehensive error handling
 * - Round-trip: Ensuring parsing and generation symmetry
 * - Error paths: Invalid input handling and validation
 * <p/>
 * Test data uses Lovecraftian themes to avoid confusion with real data while
 * testing international character support across writing systems.
 */
class CsvFunctionLibraryTests {

    /**
     * Parses CSV result into individual lines for assertion.
     *
     * @param csvResult Val containing CSV text
     * @return array of CSV lines
     */
    private static String[] parseCsvLines(Val csvResult) {
        return csvResult.getText().split("\n");
    }

    /**
     * Verifies a CSV row contains all expected values.
     *
     * @param row CSV row text
     * @param expectedValues values that should appear in the row
     */
    private static void assertCsvRowContains(String row, String... expectedValues) {
        for (String value : expectedValues) {
            assertThat(row).contains(value);
        }
    }

    @Nested
    class CsvToValTests {

        @Test
        void parsesSimpleCsv() {
            val csv    = """
                    cultist,city,sanity
                    Cthulhu,R'lyeh,0
                    Nyarlathotep,Egypt,13
                    """;
            val result = CsvFunctionLibrary.csvToVal(Val.of(csv));

            assertThat(result.get().isArray()).isTrue();
            assertThat(result.get()).hasSize(2);

            val firstRow = result.get().get(0);
            assertThat(firstRow.get("cultist").asText()).isEqualTo("Cthulhu");
            assertThat(firstRow.get("city").asText()).isEqualTo("R'lyeh");
            assertThat(firstRow.get("sanity").asText()).isEqualTo("0");

            val secondRow = result.get().get(1);
            assertThat(secondRow.get("cultist").asText()).isEqualTo("Nyarlathotep");
            assertThat(secondRow.get("city").asText()).isEqualTo("Egypt");
            assertThat(secondRow.get("sanity").asText()).isEqualTo("13");
        }

        @Test
        void handlesSingleRow() {
            val csv    = """
                    entity,power
                    Azathoth,999
                    """;
            val result = CsvFunctionLibrary.csvToVal(Val.of(csv));

            assertThat(result.get()).hasSize(1);
            assertThat(result.get().get(0).get("entity").asText()).isEqualTo("Azathoth");
            assertThat(result.get().get(0).get("power").asText()).isEqualTo("999");
        }

        @Test
        void handlesSingleColumn() {
            val csv    = """
                    location
                    Arkham
                    Innsmouth
                    Dunwich
                    """;
            val result = CsvFunctionLibrary.csvToVal(Val.of(csv));

            assertThat(result.get()).hasSize(3);
            assertThat(result.get().get(0).get("location").asText()).isEqualTo("Arkham");
            assertThat(result.get().get(1).get("location").asText()).isEqualTo("Innsmouth");
            assertThat(result.get().get(2).get("location").asText()).isEqualTo("Dunwich");
        }

        @Test
        void handlesEmptyCells() {
            val csv    = """
                    name,ritual,cost
                    Yog-Sothoth,Gate Opening,
                    Shub-Niggurath,,1000
                    ,,Madness
                    """;
            val result = CsvFunctionLibrary.csvToVal(Val.of(csv));

            assertThat(result.get()).hasSize(3);
            assertThat(result.get().get(0).get("cost").asText()).isEmpty();
            assertThat(result.get().get(1).get("ritual").asText()).isEmpty();
        }

        @Test
        void handlesQuotedFields() {
            val csv    = """
                    cultist,chant
                    "Wilbur, Whateley","Ph'nglui mglw'nafh"
                    "Lavinia, Whateley","That is not dead"
                    """;
            val result = CsvFunctionLibrary.csvToVal(Val.of(csv));

            assertThat(result.get().get(0).get("cultist").asText()).isEqualTo("Wilbur, Whateley");
            assertThat(result.get().get(0).get("chant").asText()).isEqualTo("Ph'nglui mglw'nafh");
        }

        @Test
        void handlesQuotesInQuotedFields() {
            val csv    = """
                    entity,quote
                    Cthulhu,"He spoke of ""dead Cthulhu waits dreaming\"""
                    """;
            val result = CsvFunctionLibrary.csvToVal(Val.of(csv));

            assertThat(result.get().get(0).get("quote").asText()).contains("dead Cthulhu");
        }

        @Test
        void handlesNewlinesInQuotedFields() {
            val csv    = """
                    location,description
                    R'lyeh,"Sunken city
                    Beneath the waves"
                    """;
            val result = CsvFunctionLibrary.csvToVal(Val.of(csv));

            assertThat(result.get().get(0).get("description").asText()).contains("Sunken city");
            assertThat(result.get().get(0).get("description").asText()).contains("Beneath the waves");
        }

        @Test
        void handlesHeadersWithSpaces() {
            val csv    = """
                    Elder Sign,Ritual Name,Power Level
                    Yellow,Gate Closing,7
                    """;
            val result = CsvFunctionLibrary.csvToVal(Val.of(csv));

            assertThat(result.get().get(0).has("Elder Sign")).isTrue();
            assertThat(result.get().get(0).has("Ritual Name")).isTrue();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("unicodeTestCases")
        void handlesInternationalCharacters(String description, String csv, String fieldName, String expectedValue) {
            val result = CsvFunctionLibrary.csvToVal(Val.of(csv));

            assertThat(result.get().get(0).get(fieldName).asText()).isEqualTo(expectedValue);
        }

        static Stream<Arguments> unicodeTestCases() {
            return Stream.of(
                    arguments("Latin with accents", "cultist,location\nJoséph Curwen,Pawtúxet", "location", "Pawtúxet"),
                    arguments("French accents", "nom,ville\nYog-Sothoth,Carcassonne", "ville", "Carcassonne"),
                    arguments("German umlauts", "name,ort\nNyarlathotep,Königsberg", "ort", "Königsberg"),
                    arguments("Chinese characters", "name,message\nCthulhu,克苏鲁在梦中等待", "message", "克苏鲁在梦中等待"), // Cthulhu
                                                                                                              // waits
                                                                                                              // dreaming
                    arguments("Chinese entity names", "entity,name\nCthulhu,克苏鲁", "name", "克苏鲁"), // Cthulhu
                    arguments("Chinese with hyphen", "entity,name\nYog-Sothoth,犹格-索托斯", "name", "犹格-索托斯"), // Yog-Sothoth
                                                                                                           // (note
                                                                                                           // hyphen)
                    arguments("Japanese katakana", "entity,name\nCthulhu,クトゥルフ", "name", "クトゥルフ"), // Cthulhu
                    arguments("Japanese full-width", "being,name\nYog-Sothoth,ヨグ＝ソトース", "name", "ヨグ＝ソトース"), // Yog-Sothoth
                                                                                                            // (note
                                                                                                            // full-width
                                                                                                            // ＝)
                    arguments("Korean", "entity,name\nCthulhu,크툴루", "name", "크툴루"), // Cthulhu
                    arguments("Korean entity", "entity,name\nNyarlathotep,니알라토텝", "name", "니알라토텝"), // Nyarlathotep
                    arguments("Hebrew", "name,text\nDagon,דגון הכהן", "text", "דגון הכהן"), // Dagon the priest (kohen)
                    arguments("Arabic", "entity,description\nYog-Sothoth,يوج-سوثوث", "description", "يوج-سوثوث"), // Yog-Sothoth
                                                                                                                  // (note
                                                                                                                  // hyphen)
                    arguments("Cyrillic", "name,location\nCthulhu,Р'льех", "location", "Р'льех"), // R'lyeh
                    arguments("Greek", "entity,title\nNyarlathotep,Νυαρλαθοτέπ", "title", "Νυαρλαθοτέπ"), // Nyarlathotep
                    arguments("Mixed emojis and text", "cultist,mood\nWilbur,Madness 🌙🐙👁️", "mood",
                            "Madness 🌙🐙👁️"),
                    arguments("Only emojis", "symbol,meaning\n🌟,Elder Sign ⭐", "meaning", "Elder Sign ⭐"),
                    arguments("Mixed scripts", "name,title\nCthulhu,克苏鲁 Κθούλου 🐙", "title", "克苏鲁 Κθούλου 🐙")); // Cthulhu
                                                                                                                  // (Chinese)
                                                                                                                  // Cthulhu
                                                                                                                  // (Greek)
                                                                                                                  // (emoji)
        }

        @Test
        void handlesOnlyHeaders() {
            val csv    = "entity,power,location";
            val result = CsvFunctionLibrary.csvToVal(Val.of(csv));

            assertThat(result.get().isArray()).isTrue();
            assertThat(result.get()).isEmpty();
        }

        @Test
        void handlesManyRows() {
            val csvBuilder = new StringBuilder("cultistId,ritual\n");
            for (int i = 0; i < 100; i++) {
                csvBuilder.append(i).append(",Ritual").append(i).append('\n');
            }

            val result = CsvFunctionLibrary.csvToVal(Val.of(csvBuilder.toString()));

            assertThat(result.get()).hasSize(100);
            assertThat(result.get().get(0).get("cultistId").asText()).isEqualTo("0");
            assertThat(result.get().get(99).get("cultistId").asText()).isEqualTo("99");
        }

        @Test
        void handlesManyColumns() {
            val headers = new StringBuilder();
            for (int i = 0; i < 50; i++) {
                if (i > 0)
                    headers.append(',');
                headers.append("artifact").append(i);
            }
            headers.append('\n');
            for (int i = 0; i < 50; i++) {
                if (i > 0)
                    headers.append(',');
                headers.append("relic").append(i);
            }

            val result = CsvFunctionLibrary.csvToVal(Val.of(headers.toString()));

            assertThat(result.get()).hasSize(1);
            assertThat(result.get().get(0).get("artifact0").asText()).isEqualTo("relic0");
            assertThat(result.get().get(0).get("artifact49").asText()).isEqualTo("relic49");
        }

        @ParameterizedTest(name = "Line endings: {0}")
        @MethodSource("lineEndingTestCases")
        void handlesVariousLineEndings(String description, String csv) {
            val result = CsvFunctionLibrary.csvToVal(Val.of(csv));

            assertThat(result.get()).hasSize(2);
            assertThat(result.get().get(0).get("entity").asText()).isEqualTo("Cthulhu");
            assertThat(result.get().get(1).get("entity").asText()).isEqualTo("Dagon");
        }

        static Stream<Arguments> lineEndingTestCases() {
            return Stream.of(arguments("Windows (CRLF)", "entity,sanity\r\nCthulhu,0\r\nDagon,5\r\n"),
                    arguments("Unix (LF)", "entity,sanity\nCthulhu,0\nDagon,5\n"),
                    arguments("Mac (CR)", "entity,sanity\rCthulhu,0\rDagon,5\r"),
                    arguments("Mixed", "entity,sanity\r\nCthulhu,0\nDagon,5\r\n"));
        }

        @Test
        void handlesTrailingComma() {
            val csv    = """
                    entity,power,
                    Hastur,666,
                    """;
            val result = CsvFunctionLibrary.csvToVal(Val.of(csv));

            assertThat(result.get()).hasSize(1);
        }

        @Test
        void returnsErrorForEmptyString() {
            val result = CsvFunctionLibrary.csvToVal(Val.of(""));

            assertThat(result.isError()).isTrue();
            assertThat(result.getMessage()).contains("Failed to parse CSV");
        }

        @Test
        void returnsErrorForMalformedCsv() {
            val csv    = "entity,chant\n\"Cthulhu,Ph'nglui mglw'nafh";
            val result = CsvFunctionLibrary.csvToVal(Val.of(csv));

            assertThat(result.isError()).isTrue();
            assertThat(result.getMessage()).contains("Failed to parse CSV");
        }

        @Test
        void returnsErrorForUnclosedQuote() {
            val csv    = "cultist,location\n\"Wilbur Whateley,Dunwich";
            val result = CsvFunctionLibrary.csvToVal(Val.of(csv));

            assertThat(result.isError()).isTrue();
            assertThat(result.getMessage()).contains("Failed to parse CSV");
        }
    }

    @Nested
    class ValToCsvTests {

        @Test
        void convertsArrayOfObjectsToCsv() throws JsonProcessingException {
            val array = Val.ofJson("""
                    [
                      {"cultist":"Cthulhu","city":"R'lyeh","sanity":0},
                      {"cultist":"Nyarlathotep","city":"Egypt","sanity":13}
                    ]
                    """);

            val result = CsvFunctionLibrary.valToCsv(array);
            val lines  = parseCsvLines(result);

            assertCsvRowContains(lines[0], "cultist", "city", "sanity");
            assertCsvRowContains(lines[1], "Cthulhu", "R'lyeh", "0");
        }

        @Test
        void handlesSingleObject() throws JsonProcessingException {
            val array = Val.ofJson("[{\"entity\":\"Azathoth\",\"power\":999}]");

            val result = CsvFunctionLibrary.valToCsv(array);

            assertThat(result.getText()).contains("entity").contains("power").contains("Azathoth").contains("999");
        }

        @Test
        void handlesEmptyArray() {
            val result = CsvFunctionLibrary.valToCsv(Val.ofEmptyArray());
            assertThat(result.getText()).isEmpty();
        }

        @Test
        void handlesConsistentSchemaAcrossRows() throws JsonProcessingException {
            val array = Val.ofJson("""
                    [
                      {"name":"Yog-Sothoth","dimension":"outer"},
                      {"name":"Shub-Niggurath","dimension":"forest"}
                    ]
                    """);

            val result = CsvFunctionLibrary.valToCsv(array);
            val lines  = parseCsvLines(result);

            assertCsvRowContains(lines[0], "name", "dimension");
            assertCsvRowContains(lines[1], "Yog-Sothoth");
            assertCsvRowContains(lines[2], "Shub-Niggurath");
        }

        @Test
        void handlesSpecialCharactersInFields() throws JsonProcessingException {
            val array = Val.ofJson("[{\"cultist\":\"Wilbur, Whateley\",\"chant\":\"He said \\\"Ph'nglui\\\"\"}]");

            val result = CsvFunctionLibrary.valToCsv(array);

            assertThat(result.getText()).contains("Wilbur");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("unicodeGenerationTestCases")
        void generatesInternationalCharacters(String description, String jsonArray, String expectedInCsv)
                throws JsonProcessingException {
            val array = Val.ofJson(jsonArray);

            val result = CsvFunctionLibrary.valToCsv(array);

            assertThat(result.getText()).contains(expectedInCsv);
        }

        static Stream<Arguments> unicodeGenerationTestCases() {
            return Stream.of(arguments("Chinese", "[{\"entity\":\"克苏鲁\",\"location\":\"R'lyeh\"}]", "克苏鲁"), // Cthulhu
                    arguments("Chinese with hyphen", "[{\"entity\":\"犹格-索托斯\"}]", "犹格-索托斯"), // Yog-Sothoth
                    arguments("Japanese", "[{\"name\":\"クトゥルフ\",\"power\":\"999\"}]", "クトゥルフ"), // Cthulhu
                    arguments("Japanese full-width", "[{\"entity\":\"ヨグ＝ソトース\"}]", "ヨグ＝ソトース"), // Yog-Sothoth
                    arguments("Korean", "[{\"name\":\"크툴루\"}]", "크툴루"), // Cthulhu
                    arguments("Korean entity", "[{\"entity\":\"니알라토텝\"}]", "니알라토텝"), // Nyarlathotep
                    arguments("Hebrew", "[{\"text\":\"דגון הכהן\"}]", "דגון הכהן"), // Dagon the priest (kohen)
                    arguments("Arabic", "[{\"desc\":\"يوج-سوثوث\"}]", "يوج-سوثوث"), // Yog-Sothoth (note hyphen)
                    arguments("Emoji", "[{\"symbol\":\"🐙👁️🌙\"}]", "🐙👁️🌙"),
                    arguments("Mixed", "[{\"title\":\"Cthulhu 克苏鲁 🐙\"}]", "Cthulhu 克苏鲁 🐙")); // Cthulhu (Chinese)
        }

        @Test
        void handlesNullValues() throws JsonProcessingException {
            val array = Val.ofJson("[{\"entity\":\"Hastur\",\"location\":null}]");

            val result = CsvFunctionLibrary.valToCsv(array);

            assertThat(result.isTextual()).isTrue();
        }

        @Test
        void returnsErrorForInconsistentKeys() throws JsonProcessingException {
            val array = Val.ofJson("""
                    [
                      {"entity":"Cthulhu","power":999},
                      {"entity":"Dagon","location":"Ocean"}
                    ]
                    """);

            val result = CsvFunctionLibrary.valToCsv(array);

            assertThat(result.isError()).isTrue();
        }

        @ParameterizedTest(name = "{1}")
        @MethodSource("nonObjectFirstElementTestCases")
        void returnsErrorForNonObjectFirstElement(String jsonArray, String description) throws JsonProcessingException {
            val array = Val.ofJson(jsonArray);

            val result = CsvFunctionLibrary.valToCsv(array);

            assertThat(result.isError()).isTrue();
            assertThat(result.getMessage()).contains("must contain objects");
        }

        static Stream<Arguments> nonObjectFirstElementTestCases() {
            return Stream.of(arguments("[null, {\"entity\":\"Cthulhu\"}]", "null as first element"),
                    arguments("[\"not an object\"]", "string as first element"),
                    arguments("[42]", "number as first element"), arguments("[true]", "boolean as first element"),
                    arguments("[[]]", "array as first element"));
        }

        @ParameterizedTest(name = "{2}")
        @MethodSource("nonObjectAtIndexTestCases")
        void returnsErrorForNonObjectAtIndex(String jsonArray, int expectedIndex, String description)
                throws JsonProcessingException {
            val array = Val.ofJson(jsonArray);

            val result = CsvFunctionLibrary.valToCsv(array);

            assertThat(result.isError()).isTrue();
            assertThat(result.getMessage()).contains("index " + expectedIndex).contains("not an object");
        }

        static Stream<Arguments> nonObjectAtIndexTestCases() {
            return Stream.of(
                    arguments("[{\"entity\":\"Cthulhu\"}, 42, {\"entity\":\"Dagon\"}]", 1, "number at index 1"),
                    arguments("[{\"entity\":\"Cthulhu\"}, \"invalid\", {\"entity\":\"Dagon\"}]", 1,
                            "string at index 1"),
                    arguments("[{\"entity\":\"Cthulhu\"}, [], {\"entity\":\"Dagon\"}]", 1, "array at index 1"),
                    arguments("[{\"entity\":\"Cthulhu\"}, {\"entity\":\"Dagon\"}, null]", 2, "null at index 2"),
                    arguments("[{\"entity\":\"Cthulhu\"}, {\"entity\":\"Dagon\"}, true]", 2, "boolean at index 2"));
        }

        @Test
        void returnsErrorForErrorValue() {
            val error  = Val.error("Ritual failed in Arkham.");
            val result = CsvFunctionLibrary.valToCsv(error);

            assertThat(result.isError()).isTrue();
        }

        @Test
        void returnsUndefinedForUndefinedValue() {
            val undefined = Val.UNDEFINED;
            val result    = CsvFunctionLibrary.valToCsv(undefined);

            assertThat(result.isUndefined()).isTrue();
        }
    }

    @Nested
    class RoundTripTests {

        @Test
        void preservesDataThroughRoundTrip() throws JsonProcessingException {
            val original = Val.ofJson("""
                    [
                      {"cultist":"Cthulhu","city":"R'lyeh","sanity":"0"},
                      {"cultist":"Nyarlathotep","city":"Egypt","sanity":"13"}
                    ]
                    """);

            val csvString = CsvFunctionLibrary.valToCsv(original);
            val restored  = CsvFunctionLibrary.csvToVal(csvString);

            assertThat(restored.get()).hasSize(2);
            assertThat(restored.get().get(0).get("cultist").asText()).isEqualTo("Cthulhu");
            assertThat(restored.get().get(0).get("city").asText()).isEqualTo("R'lyeh");
            assertThat(restored.get().get(1).get("cultist").asText()).isEqualTo("Nyarlathotep");
        }

        @Test
        void preservesUnicodeDataThroughRoundTrip() throws JsonProcessingException {
            val original = Val.ofJson("""
                    [
                      {"entity":"克苏鲁","location":"र'ल्येह","symbol":"🐙"},
                      {"entity":"ナイアーラトテップ","location":"مصر","symbol":"👁️"}
                    ]
                    """);
            // Cthulhu (Chinese Simplified), R'lyeh (Devanagari), octopus emoji
            // Nyarlathotep (Japanese katakana), Egypt (Arabic), eye emoji

            val csvString = CsvFunctionLibrary.valToCsv(original);
            val restored  = CsvFunctionLibrary.csvToVal(csvString);

            assertThat(restored.get()).hasSize(2);
            assertThat(restored.get().get(0).get("entity").asText()).isEqualTo("克苏鲁");
            assertThat(restored.get().get(0).get("symbol").asText()).isEqualTo("🐙");
            assertThat(restored.get().get(1).get("entity").asText()).isEqualTo("ナイアーラトテップ");
            assertThat(restored.get().get(1).get("location").asText()).isEqualTo("مصر");
        }

        @Test
        void preservesSpecialCharactersThroughRoundTrip() throws JsonProcessingException {
            val original = Val.ofJson("""
                    [
                      {"name":"Wilbur, Whateley","chant":"Ph'nglui mglw'nafh"},
                      {"name":"Lavinia, Whateley","chant":"That is \\"not\\" dead"}
                    ]
                    """);

            val csvString = CsvFunctionLibrary.valToCsv(original);
            val restored  = CsvFunctionLibrary.csvToVal(csvString);

            assertThat(restored.get()).hasSize(2);
            assertThat(restored.get().get(0).get("name").asText()).isEqualTo("Wilbur, Whateley");
        }

        @Test
        void preservesEmptyFieldsThroughRoundTrip() throws JsonProcessingException {
            val original = Val.ofJson("""
                    [
                      {"entity":"Hastur","ritual":"","power":"666"},
                      {"entity":"","ritual":"Summoning","power":""}
                    ]
                    """);

            val csvString = CsvFunctionLibrary.valToCsv(original);
            val restored  = CsvFunctionLibrary.csvToVal(csvString);

            assertThat(restored.get()).hasSize(2);
            assertThat(restored.get().get(0).get("ritual").asText()).isEmpty();
            assertThat(restored.get().get(1).get("entity").asText()).isEmpty();
        }
    }
}
