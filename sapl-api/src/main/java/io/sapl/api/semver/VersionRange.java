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
package io.sapl.api.semver;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import lombok.val;

/**
 * A node style semantic version range. Comparators in one alternative all must
 * hold. Alternatives are separated by ||. Empty alternatives accept every
 * release. Prereleases require a comparator naming the same numeric core.
 */
public final class VersionRange {
    private static final String          ERROR_INVALID_RANGE = "Invalid range: '%s'.";
    private static final Pattern         PARTIAL             = Pattern.compile(
            "v?(\\d+|[xX*])(?:\\.(\\d+|[xX*])(?:\\.(\\d+|[xX*])((?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?))?)?");
    private static final Pattern         SIMPLE              = Pattern.compile("(<=|>=|<|>|=|~>|~|\\^)?(.*)");
    private static final Pattern         WHITESPACE          = Pattern.compile("[ \\t\\r\\n\\x0B\\f]+");
    private final List<List<Comparator>> alternatives;

    private VersionRange(List<List<Comparator>> alternatives) {
        this.alternatives = List.copyOf(alternatives);
    }

    /**
     * Parses a range, validating every token before it can be matched.
     *
     * @param expression the range expression
     * @return the parsed range
     * @throws IllegalArgumentException if any part is invalid or out of bounds
     */
    public static VersionRange parse(String expression) {
        try {
            val alternatives = new ArrayList<List<Comparator>>();
            for (val section : expression.split("\\|\\|", -1)) {
                alternatives.add(parseSet(section));
            }
            return new VersionRange(alternatives);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(ERROR_INVALID_RANGE.formatted(expression), exception);
        }
    }

    /**
     * Tests a version using the default node prerelease exclusion rule.
     *
     * @param version the version to test
     * @return whether any complete comparator set accepts it
     */
    public boolean contains(Version version) {
        return alternatives.stream().anyMatch(comparators -> comparators.stream()
                .allMatch(comparator -> comparator.matches(version))
                && (version.preRelease().isEmpty()
                        || comparators.stream().anyMatch(comparator -> !comparator.version().preRelease().isEmpty()
                                && comparator.version().core().equals(version.core()))));
    }

    private static List<Comparator> parseSet(String section) {
        val tokens = WHITESPACE.splitAsStream(section).filter(token -> !token.isEmpty()).toArray(String[]::new);
        val result = new ArrayList<Comparator>();
        int index  = 0;
        while (index < tokens.length) {
            if (index + 2 < tokens.length && "-".equals(tokens[index + 1])) {
                hyphen(result, partial(tokens[index]), partial(tokens[index + 2]));
                index += 3;
            } else {
                val match = SIMPLE.matcher(tokens[index]);
                if (!match.matches())
                    throw invalid();
                val    operator = match.group(1) == null ? "=" : match.group(1);
                String operand  = match.group(2);
                index++;
                if (operand.isEmpty() && index < tokens.length)
                    operand = tokens[index++];
                expand(result, operator, partial(operand));
            }
        }
        return List.copyOf(result);
    }

    private static Partial partial(String text) {
        val match = PARTIAL.matcher(text);
        if (!match.matches())
            throw invalid();
        val       major = component(match.group(1));
        val       minor = component(match.group(2));
        val       patch = component(match.group(3));
        final int precision;
        if (major.wildcard())
            precision = 0;
        else if (minor.wildcard())
            precision = 1;
        else if (patch.wildcard())
            precision = 2;
        else
            precision = 3;
        val suffix = match.group(4) == null ? "" : match.group(4);
        // Validate even identifiers following a wildcard before desugaring it.
        val complete = Version.parse(major.number() + "." + minor.number() + "." + patch.number() + suffix);
        if (precision == 3) {
            return new Partial(complete, precision);
        }
        val minorNumber = precision > 1 ? minor.number() : 0;
        val floor       = Version.boundary(major.number(), minorNumber, 0, false);
        return new Partial(floor, precision);
    }

    private static void expand(List<Comparator> result, String operator, Partial partial) {
        val floor     = partial.floor();
        val precision = partial.precision();
        if (precision == 0) {
            if ("<".equals(operator) || ">".equals(operator))
                result.add(new Comparator("<", Version.boundary(0, 0, 0, true)));
            return;
        }
        if ("~".equals(operator) || "~>".equals(operator) || "^".equals(operator)) {
            result.add(new Comparator(">=", floor));
            result.add(new Comparator("<", upper(floor, upperComponent(operator, partial))));
        } else if (precision == 3) {
            result.add(new Comparator(operator, floor));
        } else {
            switch (operator) {
            case "="  -> {
                result.add(new Comparator(">=", floor));
                result.add(new Comparator("<", upper(floor, precision - 1)));
            }
            case ">"  -> result.add(new Comparator(">=", releaseUpper(floor, precision - 1)));
            case "<=" -> result.add(new Comparator("<", upper(floor, precision - 1)));
            case "<"  -> result.add(new Comparator("<", Version.boundary(floor.major(), floor.minor(), 0, true)));
            case ">=" -> result.add(new Comparator(">=", floor));
            default   -> throw invalid();
            }
        }
    }

    private static int upperComponent(String operator, Partial partial) {
        val precision = partial.precision();
        if (!"^".equals(operator)) {
            return precision == 1 ? 0 : 1;
        }
        val floor = partial.floor();
        if (floor.major() > 0 || precision == 1) {
            return 0;
        }
        if (floor.minor() > 0 || precision == 2) {
            return 1;
        }
        return 2;
    }

    private static void hyphen(List<Comparator> result, Partial lower, Partial upper) {
        if (lower.precision() > 0)
            result.add(new Comparator(">=", lower.floor()));
        if (upper.precision() == 3)
            result.add(new Comparator("<=", upper.floor()));
        else if (upper.precision() > 0)
            result.add(new Comparator("<", upper(upper.floor(), upper.precision() - 1)));
    }

    private static Version upper(Version floor, int component) {
        val release = releaseUpper(floor, component);
        return Version.boundary(release.major(), release.minor(), release.patch(), true);
    }

    private static Version releaseUpper(Version floor, int component) {
        return switch (component) {
        case 0  -> Version.boundary(floor.major() + 1, 0, 0, false);
        case 1  -> Version.boundary(floor.major(), floor.minor() + 1, 0, false);
        default -> Version.boundary(floor.major(), floor.minor(), floor.patch() + 1, false);
        };
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException(ERROR_INVALID_RANGE.formatted(""));
    }

    private static Component component(@Nullable String text) {
        if (text == null || "x".equalsIgnoreCase(text) || "*".equals(text))
            return new Component(0, true);
        return new Component(Version.number(text), false);
    }

    private record Component(long number, boolean wildcard) {}

    private record Partial(Version floor, int precision) {}

    private record Comparator(String operator, Version version) {
        boolean matches(Version candidate) {
            val order = candidate.comparePrecedenceTo(version);
            return switch (operator) {
            case "="  -> order == 0;
            case ">"  -> order > 0;
            case ">=" -> order >= 0;
            case "<"  -> order < 0;
            case "<=" -> order <= 0;
            default   -> false;
            };
        }
    }
}
