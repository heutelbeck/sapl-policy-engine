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
package io.sapl.functions.libraries;

import io.sapl.api.model.*;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class CsvFunctionLibraryTests {

    @Test
    void csvToVal_whenSimpleCsv_thenParsesCorrectly() {
        val csv = """
                cultist,city,sanity
                Cthulhu,R'lyeh,0
                Nyarlathotep,Egypt,13
                """;

        val result = CsvFunctionLibrary.csvToVal(Value.of(csv));

        assertThat(result).isInstanceOf(ArrayValue.class);
        val array = (ArrayValue) result;
        assertThat(array).hasSize(2);

        val firstRow = (ObjectValue) array.getFirst();
        assertThat(firstRow).containsEntry("cultist", Value.of("Cthulhu")).containsEntry("city", Value.of("R'lyeh"))
                .containsEntry("sanity", Value.of("0"));

        val secondRow = (ObjectValue) array.get(1);
        assertThat(secondRow).containsEntry("cultist", Value.of("Nyarlathotep")).containsEntry("city",
                Value.of("Egypt"));
    }

    @Test
    void csvToVal_whenSingleRow_thenParsesCorrectly() {
        val csv = """
                entity,power
                Azathoth,999
                """;

        val result = CsvFunctionLibrary.csvToVal(Value.of(csv));

        assertThat(result).isInstanceOf(ArrayValue.class);
        val array = (ArrayValue) result;
        assertThat(array).hasSize(1);
        assertThat((ObjectValue) array.getFirst()).containsEntry("entity", Value.of("Azathoth")).containsEntry("power",
                Value.of("999"));
    }

    @Test
    void csvToVal_whenSingleColumn_thenParsesCorrectly() {
        val csv = """
                location
                Arkham
                Innsmouth
                Dunwich
                """;

        val result = CsvFunctionLibrary.csvToVal(Value.of(csv));

        assertThat(result).isInstanceOf(ArrayValue.class);
        val array = (ArrayValue) result;
        assertThat(array).hasSize(3);
        assertThat((ObjectValue) array.get(0)).containsEntry("location", Value.of("Arkham"));
        assertThat((ObjectValue) array.get(1)).containsEntry("location", Value.of("Innsmouth"));
        assertThat((ObjectValue) array.get(2)).containsEntry("location", Value.of("Dunwich"));
    }

    @Test
    void csvToVal_whenEmptyCells_thenHandlesCorrectly() {
        val csv = """
                name,ritual,cost
                Yog-Sothoth,Gate Opening,
                Shub-Niggurath,,1000
                """;

        val result = CsvFunctionLibrary.csvToVal(Value.of(csv));

        assertThat(result).isInstanceOf(ArrayValue.class);
        val array = (ArrayValue) result;
        assertThat(array).hasSize(2);
        assertThat((ObjectValue) array.get(0)).containsEntry("cost", Value.of(""));
        assertThat((ObjectValue) array.get(1)).containsEntry("ritual", Value.of(""));
    }

    @Test
    void csvToVal_whenQuotedFields_thenHandlesCorrectly() {
        val csv = """
                cultist,chant
                "Wilbur, Whateley","Ph'nglui mglw'nafh"
                "Lavinia, Whateley","That is not dead"
                """;

        val result = CsvFunctionLibrary.csvToVal(Value.of(csv));

        assertThat(result).isInstanceOf(ArrayValue.class);
        val array = (ArrayValue) result;
        assertThat((ObjectValue) array.get(0)).containsEntry("cultist", Value.of("Wilbur, Whateley"))
                .containsEntry("chant", Value.of("Ph'nglui mglw'nafh"));
    }

    @Test
    void csvToVal_whenHeadersOnly_thenReturnsEmptyArray() {
        val csv = "entity,power,location";

        val result = CsvFunctionLibrary.csvToVal(Value.of(csv));

        assertThat(result).isInstanceOf(ArrayValue.class);
        assertThat((ArrayValue) result).isEmpty();
    }

    @Test
    void csvToVal_whenManyRows_thenParsesAll() {
        val csvBuilder = new StringBuilder("cultistId,ritual\n");
        for (int i = 0; i < 100; i++) {
            csvBuilder.append(i).append(",Ritual").append(i).append('\n');
        }

        val result = CsvFunctionLibrary.csvToVal(Value.of(csvBuilder.toString()));

        assertThat(result).isInstanceOf(ArrayValue.class);
        val array = (ArrayValue) result;
        assertThat(array).hasSize(100);
        assertThat((ObjectValue) array.get(0)).containsEntry("cultistId", Value.of("0"));
        assertThat((ObjectValue) array.get(99)).containsEntry("cultistId", Value.of("99"));
    }

    @ParameterizedTest(name = "Line endings: {0}")
    @MethodSource("lineEndingTestCases")
    void csvToVal_whenVariousLineEndings_thenParsesCorrectly(String description, String csv) {
        val result = CsvFunctionLibrary.csvToVal(Value.of(csv));

        assertThat(result).isInstanceOf(ArrayValue.class);
        val array = (ArrayValue) result;
        assertThat(array).hasSize(2);
        assertThat((ObjectValue) array.get(0)).containsEntry("entity", Value.of("Cthulhu"));
        assertThat((ObjectValue) array.get(1)).containsEntry("entity", Value.of("Dagon"));
    }

    private static Stream<Arguments> lineEndingTestCases() {
        return Stream.of(arguments("Windows (CRLF)", "entity,sanity\r\nCthulhu,0\r\nDagon,5\r\n"),
                arguments("Unix (LF)", "entity,sanity\nCthulhu,0\nDagon,5\n"),
                arguments("Mac (CR)", "entity,sanity\rCthulhu,0\rDagon,5\r"),
                arguments("Mixed", "entity,sanity\r\nCthulhu,0\nDagon,5\r\n"));
    }

    @Test
    void csvToVal_whenEmptyString_thenReturnsError() {
        val result = CsvFunctionLibrary.csvToVal(Value.of(""));

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Failed to parse CSV");
    }

    @Test
    void csvToVal_whenMalformedCsv_thenReturnsError() {
        val csv = "entity,chant\n\"Cthulhu,Ph'nglui mglw'nafh";

        val result = CsvFunctionLibrary.csvToVal(Value.of(csv));

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("Failed to parse CSV");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unicodeTestCases")
    void csvToVal_whenInternationalCharacters_thenParsesCorrectly(String description, String csv, String fieldName,
            String expectedValue) {
        val result = CsvFunctionLibrary.csvToVal(Value.of(csv));

        assertThat(result).isInstanceOf(ArrayValue.class);
        val array = (ArrayValue) result;
        assertThat((ObjectValue) array.get(0)).containsEntry(fieldName, Value.of(expectedValue));
    }

    private static Stream<Arguments> unicodeTestCases() {
        return Stream.of(arguments("Chinese characters", "name,message\nCthulhu,克苏鲁在梦中等待", "message", "克苏鲁在梦中等待"),
                arguments("Japanese katakana", "entity,name\nCthulhu,クトゥルフ", "name", "クトゥルフ"),
                arguments("Korean", "entity,name\nCthulhu,크툴루", "name", "크툴루"),
                arguments("Cyrillic", "name,location\nCthulhu,Р'льех", "location", "Р'льех"),
                arguments("Mixed emojis and text", "cultist,mood\nWilbur,Madness 🌙🐙👁️", "mood", "Madness 🌙🐙👁️"));
    }

    @Test
    void valToCsv_whenArrayOfObjects_thenConvertsCorrectly() {
        val array = ArrayValue.builder()
                .add(ObjectValue.builder().put("cultist", Value.of("Cthulhu")).put("city", Value.of("R'lyeh"))
                        .put("sanity", Value.of("0")).build())
                .add(ObjectValue.builder().put("cultist", Value.of("Nyarlathotep")).put("city", Value.of("Egypt"))
                        .put("sanity", Value.of("13")).build())
                .build();

        val result = CsvFunctionLibrary.valToCsv(array);

        assertThat(result).isInstanceOf(TextValue.class);
        val csvText = ((TextValue) result).value();
        assertThat(csvText).contains("cultist").contains("city").contains("sanity");
        assertThat(csvText).contains("Cthulhu").contains("R'lyeh");
    }

    @Test
    void valToCsv_whenSingleObject_thenConvertsCorrectly() {
        val array = ArrayValue.builder()
                .add(ObjectValue.builder().put("entity", Value.of("Azathoth")).put("power", Value.of("999")).build())
                .build();

        val result = CsvFunctionLibrary.valToCsv(array);

        assertThat(result).isInstanceOf(TextValue.class);
        val csvText = ((TextValue) result).value();
        assertThat(csvText).contains("entity").contains("power").contains("Azathoth").contains("999");
    }

    @Test
    void valToCsv_whenEmptyArray_thenReturnsEmptyString() {
        val result = CsvFunctionLibrary.valToCsv(Value.EMPTY_ARRAY);

        assertThat(result).isEqualTo(Value.EMPTY_TEXT);
    }

    @Test
    void valToCsv_whenNonObjectFirstElement_thenReturnsError() {
        val array = ArrayValue.builder().add(Value.of("not an object")).build();

        val result = CsvFunctionLibrary.valToCsv(array);

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("must contain objects");
    }

    @Test
    void valToCsv_whenNonObjectAtLaterIndex_thenReturnsError() {
        val array = ArrayValue.builder().add(ObjectValue.builder().put("entity", Value.of("Cthulhu")).build())
                .add(Value.of(42)).build();

        val result = CsvFunctionLibrary.valToCsv(array);

        assertThat(result).isInstanceOf(ErrorValue.class);
        assertThat(((ErrorValue) result).message()).contains("index 1").contains("not an object");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unicodeGenerationTestCases")
    void valToCsv_whenInternationalCharacters_thenGeneratesCorrectly(String description, ArrayValue array,
            String expectedInCsv) {
        val result = CsvFunctionLibrary.valToCsv(array);

        assertThat(result).isInstanceOf(TextValue.class);
        assertThat(((TextValue) result).value()).contains(expectedInCsv);
    }

    private static Stream<Arguments> unicodeGenerationTestCases() {
        return Stream.of(arguments("Chinese",
                ArrayValue.builder().add(ObjectValue.builder().put("entity", Value.of("克苏鲁")).build()).build(), "克苏鲁"),
                arguments("Japanese",
                        ArrayValue.builder().add(ObjectValue.builder().put("name", Value.of("クトゥルフ")).build()).build(),
                        "クトゥルフ"),
                arguments(
                        "Emoji", ArrayValue.builder()
                                .add(ObjectValue.builder().put("symbol", Value.of("🐙👁️🌙")).build()).build(),
                        "🐙👁️🌙"));
    }

    @Test
    void roundTrip_preservesDataCorrectly() {
        val original = ArrayValue.builder()
                .add(ObjectValue.builder().put("cultist", Value.of("Cthulhu")).put("city", Value.of("R'lyeh"))
                        .put("sanity", Value.of("0")).build())
                .add(ObjectValue.builder().put("cultist", Value.of("Nyarlathotep")).put("city", Value.of("Egypt"))
                        .put("sanity", Value.of("13")).build())
                .build();

        val csvString = CsvFunctionLibrary.valToCsv(original);
        val restored  = CsvFunctionLibrary.csvToVal((TextValue) csvString);

        assertThat(restored).isInstanceOf(ArrayValue.class);
        val array = (ArrayValue) restored;
        assertThat(array).hasSize(2);
        assertThat((ObjectValue) array.get(0)).containsEntry("cultist", Value.of("Cthulhu")).containsEntry("city",
                Value.of("R'lyeh"));
        assertThat((ObjectValue) array.get(1)).containsEntry("cultist", Value.of("Nyarlathotep"));
    }

    @Test
    void roundTrip_preservesUnicodeDataCorrectly() {
        val original = ArrayValue.builder()
                .add(ObjectValue.builder().put("entity", Value.of("克苏鲁")).put("symbol", Value.of("🐙")).build())
                .add(ObjectValue.builder().put("entity", Value.of("ナイアーラトテップ")).put("symbol", Value.of("👁️")).build())
                .build();

        val csvString = CsvFunctionLibrary.valToCsv(original);
        val restored  = CsvFunctionLibrary.csvToVal((TextValue) csvString);

        assertThat(restored).isInstanceOf(ArrayValue.class);
        val array = (ArrayValue) restored;
        assertThat(array).hasSize(2);
        assertThat((ObjectValue) array.get(0)).containsEntry("entity", Value.of("克苏鲁")).containsEntry("symbol",
                Value.of("🐙"));
        assertThat((ObjectValue) array.get(1)).containsEntry("entity", Value.of("ナイアーラトテップ"));
    }

    @Test
    void roundTrip_preservesEmptyFieldsCorrectly() {
        val original = ArrayValue.builder()
                .add(ObjectValue.builder().put("entity", Value.of("Hastur")).put("ritual", Value.of(""))
                        .put("power", Value.of("666")).build())
                .add(ObjectValue.builder().put("entity", Value.of("")).put("ritual", Value.of("Summoning"))
                        .put("power", Value.of("")).build())
                .build();

        val csvString = CsvFunctionLibrary.valToCsv(original);
        val restored  = CsvFunctionLibrary.csvToVal((TextValue) csvString);

        assertThat(restored).isInstanceOf(ArrayValue.class);
        val array = (ArrayValue) restored;
        assertThat(array).hasSize(2);
        assertThat((ObjectValue) array.get(0)).containsEntry("ritual", Value.of(""));
        assertThat((ObjectValue) array.get(1)).containsEntry("entity", Value.of(""));
    }
}
