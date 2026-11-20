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

import static java.lang.Integer.signum;
import static java.lang.Long.compare;
import static java.lang.Long.parseLong;

import java.util.List;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import lombok.val;

/**
 * An immutable semantic version with SemVer 2.0.0 precedence. A lowercase v
 * prefix is accepted. Whitespace is not trimmed. Numeric components and numeric
 * identifiers are limited to 9007199254740991. Build metadata has no precedence.
 */
public final class Version {
    private static final String  ERROR_INVALID_VERSION = "Invalid semantic version: '%s'.";
    private static final long    MAXIMUM_NUMBER        = 9007199254740991L;
    private static final Pattern FORMAT                = Pattern
            .compile("v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?(?:\\+([0-9A-Za-z.-]+))?");
    private static final Pattern COERCION              = Pattern
            .compile("(?:^|\\D)(\\d{1,16})(?:\\.(\\d{1,16}))?(?:\\.(\\d{1,16}))?(?:$|\\D)");

    private final long         major;
    private final long         minor;
    private final long         patch;
    private final List<String> preRelease;
    private final List<String> build;

    private Version(long major, long minor, long patch, List<String> preRelease, List<String> build) {
        this.major      = major;
        this.minor      = minor;
        this.patch      = patch;
        this.preRelease = List.copyOf(preRelease);
        this.build      = List.copyOf(build);
    }

    /**
     * Parses a complete version with an optional lowercase v prefix.
     *
     * @param text the complete version
     * @return the parsed version
     * @throws IllegalArgumentException if the version is invalid or out of bounds
     */
    public static Version parse(String text) {
        val match = FORMAT.matcher(text);
        if (!match.matches()) {
            throw invalid(text);
        }
        try {
            return new Version(number(match.group(1)), number(match.group(2)), number(match.group(3)),
                    identifiers(match.group(4), false), identifiers(match.group(5), true));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(ERROR_INVALID_VERSION.formatted(text), exception);
        }
    }

    /**
     * Parses a version when it is valid.
     *
     * @param text the candidate version, or null
     * @return the parsed version, or null if absent or invalid
     */
    public static @Nullable Version tryParse(@Nullable String text) {
        if (text == null) {
            return null;
        }
        try {
            return parse(text);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * Extracts the first version shaped sequence of at most sixteen digits per
     * component, fills missing components with zero, and drops all suffixes.
     *
     * @param text the text to coerce
     * @return the coerced version, or null if its first match is invalid
     */
    public static @Nullable Version coerce(String text) {
        val match = COERCION.matcher(text);
        if (!match.find()) {
            return null;
        }
        return tryParse(match.group(1) + "." + (match.group(2) == null ? "0" : match.group(2)) + "."
                + (match.group(3) == null ? "0" : match.group(3)));
    }

    /** @return the major component */
    public long major() {
        return major;
    }

    /** @return the minor component */
    public long minor() {
        return minor;
    }

    /** @return the patch component */
    public long patch() {
        return patch;
    }

    /** @return immutable prerelease identifiers */
    public List<String> preRelease() {
        return preRelease;
    }

    /** @return immutable build identifiers */
    public List<String> build() {
        return build;
    }

    /** @return whether the major is positive and no prerelease identifiers exist */
    public boolean isStable() {
        return major > 0 && preRelease.isEmpty();
    }

    /** @return the three numeric components separated by periods */
    public String core() {
        return major + "." + minor + "." + patch;
    }

    /** @return the complete version without a v prefix */
    public String text() {
        return core() + (preRelease.isEmpty() ? "" : "-" + String.join(".", preRelease))
                + (build.isEmpty() ? "" : "+" + String.join(".", build));
    }

    /**
     * Compares precedence according to section 11 of SemVer 2.0.0.
     *
     * @param other the other version
     * @return negative one, zero, or one
     */
    public int comparePrecedenceTo(Version other) {
        int order = compare(major, other.major);
        if (order == 0)
            order = compare(minor, other.minor);
        if (order == 0)
            order = compare(patch, other.patch);
        if (order != 0)
            return order;
        if (preRelease.isEmpty() || other.preRelease.isEmpty()) {
            return Boolean.compare(preRelease.isEmpty(), other.preRelease.isEmpty());
        }
        for (int index = 0; index < Math.min(preRelease.size(), other.preRelease.size()); index++) {
            order = compareIdentifier(preRelease.get(index), other.preRelease.get(index));
            if (order != 0)
                return order;
        }
        return signum(Integer.compare(preRelease.size(), other.preRelease.size()));
    }

    static Version boundary(long major, long minor, long patch, boolean beforePrereleases) {
        // Exclusive upper bounds precede every prerelease of their numeric core.
        return new Version(major, minor, patch, beforePrereleases ? List.of("0") : List.of(), List.of());
    }

    static long number(String text) {
        if (text.isEmpty() || (text.length() > 1 && text.charAt(0) == '0') || !numeric(text)) {
            throw invalid(text);
        }
        val value = parseLong(text);
        if (value > MAXIMUM_NUMBER)
            throw invalid(text);
        return value;
    }

    private static List<String> identifiers(@Nullable String text, boolean metadata) {
        if (text == null)
            return List.of();
        val identifiers = List.of(text.split("\\.", -1));
        for (val identifier : identifiers) {
            if (identifier.isEmpty())
                throw invalid(text);
            if (numeric(identifier)) {
                // Build identifiers may contain leading zeros under SemVer 2.0.0.
                val digits = metadata ? identifier.replaceFirst("^0+(?!$)", "") : identifier;
                number(digits);
            }
        }
        return identifiers;
    }

    private static boolean numeric(String text) {
        return text.chars().allMatch(character -> character >= '0' && character <= '9');
    }

    private static int compareIdentifier(String first, String second) {
        val firstNumeric  = numeric(first);
        val secondNumeric = numeric(second);
        if (firstNumeric && secondNumeric)
            return compare(parseLong(first), parseLong(second));
        if (firstNumeric != secondNumeric)
            return firstNumeric ? -1 : 1;
        return signum(first.compareTo(second));
    }

    private static IllegalArgumentException invalid(String text) {
        return new IllegalArgumentException(ERROR_INVALID_VERSION.formatted(text));
    }
}
