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

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * Functions for converting human-readable unit strings into numeric values.
 */
@UtilityClass
@FunctionLibrary(name = UnitsFunctionLibrary.NAME, description = UnitsFunctionLibrary.DESCRIPTION)
public class UnitsFunctionLibrary {

    public static final String NAME        = "units";
    public static final String DESCRIPTION = "Functions for converting human-readable unit strings into numeric values.";

    /**
     * Maximum length for entire input string to prevent DoS via extremely long
     * inputs. 110 characters allows 100 digits
     * plus sign, decimal, exponent notation, and short unit.
     */
    private static final int MAX_INPUT_LENGTH = 110;

    /**
     * Unified multiplier map for both general and byte units. Empty string entry
     * handles byte-only context (no unit =
     * bytes). Normalized keys: 'm' (lowercase milli), uppercase letters, 'I' suffix
     * for binary units.
     */
    private static final Map<String, BigDecimal> MULTIPLIERS = Map.ofEntries(Map.entry("", BigDecimal.ONE),
            Map.entry("m", new BigDecimal("0.001")), Map.entry("K", new BigDecimal("1000")),
            Map.entry("M", new BigDecimal("1000000")), Map.entry("G", new BigDecimal("1000000000")),
            Map.entry("T", new BigDecimal("1000000000000")), Map.entry("P", new BigDecimal("1000000000000000")),
            Map.entry("E", new BigDecimal("1000000000000000000")), Map.entry("KI", new BigDecimal("1024")),
            Map.entry("MI", new BigDecimal("1048576")), Map.entry("GI", new BigDecimal("1073741824")),
            Map.entry("TI", new BigDecimal("1099511627776")), Map.entry("PI", new BigDecimal("1125899906842624")),
            Map.entry("EI", new BigDecimal("1152921504606846976")));

    /**
     * Units not supported in byte parsing context (too large for practical byte
     * sizes).
     */
    private static final Set<String> UNSUPPORTED_FOR_BYTES = Set.of("P", "E", "PI", "EI");

    private static final String ERROR_CANNOT_PARSE_NUMERIC = "Cannot parse numeric value: '%s'.";
    private static final String ERROR_INPUT_TOO_LONG       = "Input too long (max %d characters): '%s...'.";
    private static final String ERROR_INVALID_FORMAT       = "Invalid %sformat: '%s'. Expected format: number followed by optional unit (e.g., '10K', '5.5M', '1e3G').";
    private static final String ERROR_UNKNOWN_UNIT         = "Unknown %sunit: '%s'. Supported units: %s.";

    private static final String FORMAT_TYPE_BYTE = "byte ";
    private static final String FORMAT_TYPE_UNIT = "unit ";

    private static final String SUPPORTED_UNITS_BYTES = "B, KB/KiB, MB/MiB, GB/GiB, TB/TiB (byte suffix optional, case-insensitive)";
    private static final String SUPPORTED_UNITS_SI    = "m, K/Ki, M/Mi, G/Gi, T/Ti, P/Pi, E/Ei";

    private static final String UNIT_MILLI = "m";

    /**
     * Transforms a human-readable unit string into its numeric equivalent. Supports
     * standard metric prefixes including
     * both decimal SI units (m, K, M, G, T, P, E) and binary SI units (Ki, Mi, Gi,
     * Ti, Pi, Ei).
     *
     * @param unitString
     * the human-readable unit expression to transform
     *
     * @return a Value containing the computed numeric value, or an ErrorValue if
     * parsing fails
     */
    @Function(docs = """
            ```units.parse(TEXT unitString)```: Transforms human-readable unit notation into numeric values.
            This function processes strings containing a number followed by an optional unit designator,
            supporting both decimal and binary SI prefixes. The result is a numeric value representing
            the calculation of the input number multiplied by the appropriate unit multiplier.

            **Supported Units:**
            - **Decimal SI units**: m (milli, 0.001), K (kilo, 1000), M (mega, 1,000,000),
              G (giga, 1 billion), T (tera, 1 trillion), P (peta), E (exa)
            - **Binary SI units**: Ki (kibi, 1024), Mi (mebi, 1,048,576), Gi (gibi, 1,073,741,824),
              Ti (tebi), Pi (pebi), Ei (exbi)

            **Important Notes:**
            - The 'm' and 'M' prefixes are case-sensitive to differentiate between milli and mega
            - All other unit prefixes are case-insensitive
            - Scientific notation is supported (e.g., "1.5e3K", "2e-2M")
            - Decimal values are permitted (e.g., "3.14M", "0.5G")
            - If no unit is specified, the input number is returned unchanged

            **Examples:**
            ```sapl
            policy "demonstrate_unit_parsing"
            permit
            where
              // Basic decimal units
              var fiveThousand = units.parse("5K");           // Returns 5000
              var fourMillion = units.parse("4M");            // Returns 4000000
              var tenGigavalue = units.parse("10G");          // Returns 10000000000

              // Milli prefix (case-sensitive)
              var milliValue = units.parse("1500m");          // Returns 1.5
              var megaValue = units.parse("1500M");           // Returns 1500000000

              // Binary units
              var oneKibi = units.parse("1Ki");               // Returns 1024
              var twoMebi = units.parse("2Mi");               // Returns 2097152

              // Scientific notation support
              var scientificKilo = units.parse("1e-3K");      // Returns 1
              var largeMega = units.parse("2.5e6M");          // Returns 2500000000000000

              // Decimal values
              var fractional = units.parse("3.14M");          // Returns 3140000
              var smallValue = units.parse("0.75K");          // Returns 750

              // No unit specified
              var plainNumber = units.parse("42");            // Returns 42

              // Invalid formats return errors
              var error1 = units.parse("invalid");            // Returns error
              var error2 = units.parse("10ZZ");               // Returns error (unknown unit)
            ```
            """)
    public static Value parse(TextValue unitString) {
        return parseWithContext(unitString.value(), false);
    }

    /**
     * Converts human-readable byte size strings into their numeric byte count
     * equivalents. Handles both decimal byte
     * units (KB, MB, GB, TB) and binary byte units (KiB, MiB, GiB, TiB).
     *
     * @param byteString
     * the byte size string to convert
     *
     * @return a Value containing the byte count as a numeric value, or an
     * ErrorValue if parsing fails
     */
    @Function(docs = """
            ```units.parseBytes(TEXT byteString)```: Converts byte size notation into precise byte counts.
            This function interprets strings expressing data sizes in human-readable format and transforms
            them into exact byte values. It distinguishes between decimal units (based on powers of 1000)
            and binary units (based on powers of 1024), following standard computing conventions.

            **Supported Units:**
            - **Decimal byte units**: KB/kb (1000 bytes), MB/mb (1,000,000 bytes),
              GB/gb (1 billion bytes), TB/tb (1 trillion bytes)
            - **Binary byte units**: KiB/Ki (1024 bytes), MiB/Mi (1,048,576 bytes),
              GiB/Gi (1,073,741,824 bytes), TiB/Ti (1,099,511,627,776 bytes)
            - **Base unit**: B/b (1 byte)

            **Important Notes:**
            - The byte suffix ('B' or 'b') is optional and can be omitted (e.g., "Mi" equals "MiB")
            - Decimal units (KB, MB, GB) use base-1000 multipliers
            - Binary units (KiB, MiB, GiB) use base-1024 multipliers
            - Unit prefixes are case-insensitive (e.g., "KB", "kb", "Kb", "kB" are all valid)
            - Scientific notation is fully supported (e.g., "1.5e3MB", "2e6GiB")
            - Decimal values are allowed (e.g., "2.5GB", "10.75MiB")
            - If no unit is specified, the value is interpreted as bytes

            **Examples:**
            ```sapl
            policy "demonstrate_byte_parsing"
            permit
            where
              // Basic decimal byte units
              var tenKilobytes = units.parseBytes("10KB");        // Returns 10000
              var fiveKilo = units.parseBytes("5K");              // Returns 5000 (B is optional)
              var fourMegabytes = units.parseBytes("4mb");        // Returns 4000000 (case-insensitive)
              var oneGigabyte = units.parseBytes("1GB");          // Returns 1000000000

              // Binary byte units (powers of 1024)
              var oneKibibyte = units.parseBytes("1KiB");         // Returns 1024
              var twoKibi = units.parseBytes("2Ki");              // Returns 2048 (B is optional)
              var threeMebibytes = units.parseBytes("3MiB");      // Returns 3145728
              var halfGibibyte = units.parseBytes("0.5GiB");      // Returns 536870912

              // Scientific notation examples
              var largeMegabytes = units.parseBytes("1.5e3MB");   // Returns 1500000000 (1500 MB)
              var massiveGibibytes = units.parseBytes("2e6GiB");  // Returns 2147483648000000 (2 million GiB)
              var smallKilobytes = units.parseBytes("1e-2KB");    // Returns 10 (0.01 KB)

              // Decimal precision
              var preciseSize = units.parseBytes("2.5GB");        // Returns 2500000000
              var fractionalMiB = units.parseBytes("10.75MiB");   // Returns 11272192

              // Just bytes
              var plainBytes = units.parseBytes("1024");          // Returns 1024
              var explicitBytes = units.parseBytes("1024B");      // Returns 1024

              // Case variations (all valid)
              var lowercase = units.parseBytes("100mb");          // Returns 100000000
              var mixedCase = units.parseBytes("100Mb");          // Returns 100000000
              var uppercase = units.parseBytes("100MB");          // Returns 100000000

              // Comparison: decimal vs binary
              var decimalMB = units.parseBytes("1MB");            // Returns 1000000
              var binaryMiB = units.parseBytes("1MiB");           // Returns 1048576

              // Invalid formats return errors
              var error1 = units.parseBytes("not-a-size");        // Returns error
              var error2 = units.parseBytes("10PB");              // Returns error (unsupported unit)
            ```
            """)
    public static Value parseBytes(TextValue byteString) {
        return parseWithContext(byteString.value(), true);
    }

    /**
     * Core parsing logic shared by both parse and parseBytes functions. Uses manual
     * string parsing instead of regex to
     * avoid ReDoS vulnerabilities.
     */
    private static Value parseWithContext(String input, boolean isByteContext) {
        val text = input.trim();

        if (text.isEmpty()) {
            return createFormatError("", isByteContext);
        }

        if (text.length() > MAX_INPUT_LENGTH) {
            return Value.error(ERROR_INPUT_TOO_LONG, MAX_INPUT_LENGTH, text.substring(0, 50));
        }

        val parsed = parseNumberAndUnit(text);
        if (parsed == null) {
            return createFormatError(text, isByteContext);
        }

        val numberPart = parsed.numberPart();
        val unitPart   = parsed.unitPart();

        BigDecimal numericValue;
        try {
            numericValue = new BigDecimal(numberPart);
        } catch (NumberFormatException e) {
            return Value.error(ERROR_CANNOT_PARSE_NUMERIC, numberPart);
        }

        val normalizedUnit = isByteContext ? normalizeByteUnit(unitPart) : normalizeUnit(unitPart);

        if (normalizedUnit.isEmpty() && !isByteContext) {
            return Value.of(numericValue.doubleValue());
        }

        val multiplier = MULTIPLIERS.get(normalizedUnit);
        if (multiplier == null || (isByteContext && UNSUPPORTED_FOR_BYTES.contains(normalizedUnit))) {
            val unitType       = isByteContext ? FORMAT_TYPE_BYTE : "";
            val supportedUnits = isByteContext ? SUPPORTED_UNITS_BYTES : SUPPORTED_UNITS_SI;
            return Value.error(ERROR_UNKNOWN_UNIT, unitType, unitPart, supportedUnits);
        }

        val result = numericValue.multiply(multiplier);
        return Value.of(result.doubleValue());
    }

    /**
     * Parses a string into numeric and unit parts using explicit character
     * scanning.
     */
    private static ParsedParts parseNumberAndUnit(String text) {
        val parser = new UnitParser(text);

        if (!parser.parseNumber()) {
            return null;
        }

        parser.skipWhitespace();

        if (!parser.parseUnit()) {
            return null;
        }

        return parser.getParsedParts();
    }

    /**
     * Creates a standardized format error message.
     */
    private static Value createFormatError(String text, boolean isByteContext) {
        val formatType = isByteContext ? FORMAT_TYPE_BYTE : FORMAT_TYPE_UNIT;
        return Value.error(ERROR_INVALID_FORMAT, formatType, text);
    }

    /**
     * Normalizes a general unit string to canonical form for map lookup.
     */
    private static String normalizeUnit(String unit) {
        if (unit.isEmpty() || UNIT_MILLI.equals(unit)) {
            return unit;
        }

        val lower = unit.toLowerCase();
        if (lower.endsWith("i")) {
            return unit.substring(0, unit.length() - 1).toUpperCase() + "I";
        }

        return unit.toUpperCase();
    }

    /**
     * Normalizes a byte unit string by stripping optional 'B'/'b' suffix.
     */
    private static String normalizeByteUnit(String unit) {
        if (unit.isEmpty()) {
            return unit;
        }

        var normalizedUnit = unit;
        if (normalizedUnit.endsWith("B") || normalizedUnit.endsWith("b")) {
            normalizedUnit = normalizedUnit.substring(0, normalizedUnit.length() - 1);
        }

        if (normalizedUnit.isEmpty()) {
            return normalizedUnit;
        }

        val lower = normalizedUnit.toLowerCase();
        if (lower.endsWith("i")) {
            return normalizedUnit.substring(0, normalizedUnit.length() - 1).toUpperCase() + "I";
        }

        return normalizedUnit.toUpperCase();
    }

    /**
     * Holds the parsed number and unit components of an input string.
     */
    private record ParsedParts(String numberPart, String unitPart) {}

    /**
     * Helper class for parsing unit strings with maintained position state.
     */
    private static class UnitParser {
        private final String text;
        private final int    length;
        private int          position;
        private int          numberEnd;

        UnitParser(String text) {
            this.text      = text;
            this.length    = text.length();
            this.position  = 0;
            this.numberEnd = 0;
        }

        boolean parseNumber() {
            parseOptionalSign();

            if (!parseRequiredDigits()) {
                return false;
            }

            parseOptionalDecimal();
            parseOptionalScientificNotation();

            return true;
        }

        void parseOptionalSign() {
            if (position < length && (text.charAt(position) == '+' || text.charAt(position) == '-')) {
                position++;
            }
        }

        boolean parseRequiredDigits() {
            int digitStart = position;
            while (position < length && Character.isDigit(text.charAt(position))) {
                position++;
            }
            return position > digitStart;
        }

        void parseOptionalDecimal() {
            if (position < length && text.charAt(position) == '.') {
                position++;
                while (position < length && Character.isDigit(text.charAt(position))) {
                    position++;
                }
            }
        }

        void parseOptionalScientificNotation() {
            if (position < length && (text.charAt(position) == 'e' || text.charAt(position) == 'E')) {
                int savedPosition = position;
                position++;

                if (position < length && (text.charAt(position) == '+' || text.charAt(position) == '-')) {
                    position++;
                }

                int exponentStart = position;
                while (position < length && Character.isDigit(text.charAt(position))) {
                    position++;
                }

                if (position == exponentStart) {
                    position = savedPosition;
                }
            }
        }

        void skipWhitespace() {
            while (position < length && Character.isWhitespace(text.charAt(position))) {
                position++;
            }

            numberEnd = position;
            while (numberEnd > 0 && Character.isWhitespace(text.charAt(numberEnd - 1))) {
                numberEnd--;
            }
        }

        boolean parseUnit() {
            while (position < length && Character.isLetter(text.charAt(position))) {
                position++;
            }

            return position == length;
        }

        ParsedParts getParsedParts() {
            val numberPart = text.substring(0, numberEnd);
            val unitPart   = text.substring(numberEnd).trim();
            return new ParsedParts(numberPart, unitPart);
        }
    }
}
