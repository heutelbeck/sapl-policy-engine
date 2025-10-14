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
import io.sapl.api.validation.Text;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.math.BigDecimal;
import java.util.Map;
import java.util.regex.Pattern;

@UtilityClass
@FunctionLibrary(name = UnitsFunctionLibrary.NAME, description = UnitsFunctionLibrary.DESCRIPTION)
public class UnitsFunctionLibrary {
    public static final String NAME        = "units";
    public static final String DESCRIPTION = "Functions for converting human-readable unit strings into numeric values.";

    private static final Pattern UNIT_PATTERN = Pattern
            .compile("^([+-]?\\d+\\.?\\d*(?:[eE][+-]?\\d+)?)\\s*([a-zA-Z]*)$");

    /**
     * Unified multiplier map for both general and byte units.
     * Empty string entry handles byte-only context (no unit = bytes).
     * Normalized keys: 'm' (lowercase milli), uppercase letters, 'I' suffix for
     * binary units.
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
    private static final java.util.Set<String> UNSUPPORTED_FOR_BYTES = java.util.Set.of("P", "E", "PI", "EI");

    /**
     * Transforms a human-readable unit string into its numeric equivalent.
     * Supports standard metric prefixes including both decimal SI units (m, K, M,
     * G, T, P, E)
     * and binary SI units (Ki, Mi, Gi, Ti, Pi, Ei). The prefix 'm' represents milli
     * (0.001)
     * while 'M' represents mega (1,000,000), making case sensitivity important for
     * distinguishing
     * these values. Other unit prefixes are case-insensitive. Scientific notation
     * is fully supported
     * in the numeric portion, enabling expressions such as "1e-3K" (evaluating to
     * 1) or "2.5e6M"
     * (evaluating to 2.5 trillion).
     *
     * @param unitString the human-readable unit expression to transform
     * @return a Val containing the computed numeric value, or an error if parsing
     * fails
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
    public static Val parse(@Text Val unitString) {
        return parseWithContext(unitString, false);
    }

    /**
     * Converts human-readable byte size strings into their numeric byte count
     * equivalents.
     * Handles both decimal byte units (KB, MB, GB, TB) and binary byte units (KiB,
     * MiB, GiB, TiB).
     * The byte indicator ('b' or 'B') in the unit suffix is optional; omitting it
     * yields identical
     * results (e.g., "Mi" and "MiB" are treated equivalently). Decimal units use
     * powers of 1000,
     * while binary units use powers of 1024. Scientific notation is supported for
     * the numeric
     * component, allowing expressions such as "1.5e3MB" (1,500 megabytes) or
     * "2e6GiB"
     * (2 million gibibytes).
     *
     * @param byteString the byte size string to convert
     * @return a Val containing the byte count as a numeric value, or an error if
     * parsing fails
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
    public static Val parseBytes(@Text Val byteString) {
        return parseWithContext(byteString, true);
    }

    /**
     * Core parsing logic shared by both parse and parseBytes functions.
     * Extracts numeric value and unit suffix, normalizes the unit based on context,
     * and applies the appropriate multiplier.
     *
     * @param input the input string containing number and optional unit
     * @param isByteContext true if parsing bytes (allows empty unit), false for
     * general units
     * @return a Val containing the computed value or an error
     */
    private static Val parseWithContext(Val input, boolean isByteContext) {
        val text    = input.getText().trim();
        val matcher = UNIT_PATTERN.matcher(text);

        if (!matcher.matches()) {
            return Val.error("Invalid " + (isByteContext ? "byte " : "unit ") + "format: '" + text
                    + "'. Expected format: number followed by optional unit (e.g., '10K', '5.5M', '1e3G')");
        }

        val numberPart = matcher.group(1);
        val unitPart   = matcher.group(2);

        BigDecimal numericValue;
        try {
            numericValue = new BigDecimal(numberPart);
        } catch (NumberFormatException e) {
            return Val.error("Cannot parse numeric value: '" + numberPart + "'");
        }

        val normalizedUnit = isByteContext ? normalizeByteUnit(unitPart) : normalizeUnit(unitPart);

        if (normalizedUnit.isEmpty() && !isByteContext) {
            return Val.of(numericValue.doubleValue());
        }

        val multiplier = MULTIPLIERS.get(normalizedUnit);
        if (multiplier == null || (isByteContext && UNSUPPORTED_FOR_BYTES.contains(normalizedUnit))) {
            return Val.error("Unknown " + (isByteContext ? "byte " : "") + "unit: '" + unitPart + "'. Supported units: "
                    + (isByteContext ? "B, KB/KiB, MB/MiB, GB/GiB, TB/TiB (byte suffix optional, case-insensitive)"
                            : "m, K/Ki, M/Mi, G/Gi, T/Ti, P/Pi, E/Ei"));
        }

        val result = numericValue.multiply(multiplier);
        return Val.of(result.doubleValue());
    }

    /**
     * Normalizes a general unit string to canonical form for map lookup.
     * Preserves lowercase 'm' for milli vs uppercase 'M' for mega distinction.
     * Binary units (ending in 'i') are normalized to uppercase base + 'I'.
     * Other units are normalized to uppercase.
     *
     * @param unit the unit string to normalize
     * @return normalized unit string matching a key in MULTIPLIERS map
     */
    private static String normalizeUnit(String unit) {
        if (unit.isEmpty() || "m".equals(unit)) {
            return unit;
        }

        val lower = unit.toLowerCase();
        if (lower.endsWith("i")) {
            return unit.substring(0, unit.length() - 1).toUpperCase() + "I";
        }

        return unit.toUpperCase();
    }

    /**
     * Normalizes a byte unit string by stripping optional 'B'/'b' suffix
     * then applying byte-specific normalization. For bytes, 'm' is always
     * treated as 'M' (mega) since milli-bytes don't make sense.
     *
     * @param unit the byte unit string to normalize
     * @return normalized unit string matching a key in MULTIPLIERS map
     */
    private static String normalizeByteUnit(String unit) {
        if (unit.isEmpty()) {
            return unit;
        }

        if (unit.endsWith("B") || unit.endsWith("b")) {
            unit = unit.substring(0, unit.length() - 1);
        }

        if (unit.isEmpty()) {
            return unit;
        }

        val lower = unit.toLowerCase();
        if (lower.endsWith("i")) {
            return unit.substring(0, unit.length() - 1).toUpperCase() + "I";
        }

        return unit.toUpperCase();
    }
}
