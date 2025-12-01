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
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.semver4j.Semver;
import org.semver4j.SemverException;

/**
 * Functions for semantic version comparison and validation in authorization
 * policies. Supports version-based access
 * control, API compatibility checks, and feature gating.
 */
@UtilityClass
@FunctionLibrary(name = SemVerFunctionLibrary.NAME, description = SemVerFunctionLibrary.DESCRIPTION, libraryDocumentation = SemVerFunctionLibrary.DOCUMENTATION)
public class SemVerFunctionLibrary {

    public static final String NAME        = "semver";
    public static final String DESCRIPTION = "Functions for semantic version comparison and validation in authorization policies.";

    public static final String DOCUMENTATION = """
            # Semantic Versioning in Authorization Policies

            This library provides functions for working with semantic versions in SAPL policies, following the Semantic Versioning 2.0.0 specification. Use these functions to implement version-based access control, check API compatibility between services, and gate features based on client versions.

            ## Understanding Version Format

            Semantic versions follow the format `MAJOR.MINOR.PATCH[-PRERELEASE][+BUILDMETADATA]`. Each component serves a specific purpose:

            - MAJOR: Incremented for incompatible API changes (e.g., `2.0.0`)
            - MINOR: Incremented when adding backwards-compatible functionality (e.g., `1.5.0`)
            - PATCH: Incremented for backwards-compatible bug fixes (e.g., `1.0.3`)
            - PRERELEASE: Optional pre-release identifier with dot-separated alphanumeric parts (e.g., `alpha`, `beta.1`, `rc.2`)
            - BUILDMETADATA: Optional build metadata with dot-separated alphanumeric parts (e.g., `build.123`, `sha.5114f85`)

            Valid examples include `1.0.0`, `2.3.5-alpha`, and `1.0.0-beta.2+build.456`.

            The library accepts a lowercase `v` prefix for compatibility with Git tags (e.g., `v1.0.0`). However, uppercase `V` is not supported. While the strict SemVer 2.0.0 specification excludes any prefix, lowercase `v` is widely adopted in version control systems.

            ## How Version Comparison Works

            Version precedence follows semantic versioning rules. Major, minor, and patch numbers are compared numerically. Pre-release versions always come before their corresponding normal versions (e.g., `1.0.0-alpha` is lower than `1.0.0`). When comparing pre-release identifiers, the comparison is alphanumeric. Build metadata never affects version precedence.

            ## Expressing Version Ranges

            The library supports NPM-style range syntax for flexible version matching:

            - **Operators**: `>=1.0.0`, `>1.0.0`, `<=2.0.0`, `<2.0.0`, `=1.0.0`
            - **Hyphen ranges**: `1.2.3 - 2.3.4` (both ends inclusive)
            - **X-ranges**: `1.2.x`, `1.x`, `*` (wildcards for matching any value)
            - **Tilde**: `~1.2.3` (allows patch-level changes: `>=1.2.3 <1.3.0`)
            - **Caret**: `^1.2.3` (allows minor-level changes: `>=1.2.3 <2.0.0`)
            - **OR**: `>=1.0.0 || >=2.0.0`
            - **AND**: `>=1.0.0 <2.0.0` (space-separated conditions)

            ## Common Authorization Scenarios

            Version-based access control is essential for managing API lifecycle and ensuring security. For instance, you might need to block clients using deprecated API versions to force security updates. Here's how to enforce a minimum client version:

            ```sapl
            policy "enforce_minimum_secure_version"
            deny
            where
              semver.isLower(subject.clientVersion, "2.5.0");
            ```

            Feature gating lets you enable advanced functionality only for clients meeting version requirements. This prevents older clients from attempting to use features they don't support:

            ```sapl
            policy "advanced_analytics_feature"
            permit action.name == "useAdvancedAnalytics"
            where
              semver.isAtLeast(subject.appVersion, "3.2.0");
            ```

            API compatibility checking ensures services can communicate properly. When services depend on each other, you need to verify they speak compatible API versions:

            ```sapl
            policy "service_compatibility"
            permit action.name == "invokeService"
            where
              semver.isCompatibleWith(subject.serviceVersion, resource.requiredApiVersion);
            ```

            Restricting pre-release versions to specific roles prevents unstable builds from reaching production environments. Development and staging teams might use pre-release versions, but production systems should only run stable releases:

            ```sapl
            policy "production_stable_only"
            deny action.name == "deployToProduction"
            where
              semver.isPreRelease(resource.version);
              subject.role != "release-manager";
            ```

            Compatibility windows help manage gradual rollouts. When migrating between major versions, you often need to support a range of client versions temporarily:

            ```sapl
            policy "migration_compatibility_window"
            permit
            where
              semver.isBetween(subject.clientVersion, "2.0.0", "3.5.0");
            ```
            """;

    private static final String PARSED_VERSION_SCHEMA  = """
            {
                "type": "object",
                "properties": {
                    "version": {
                        "type": "string",
                        "description": "Complete version string without v-prefix"
                    },
                    "major": {
                        "type": "integer",
                        "description": "Major version number"
                    },
                    "minor": {
                        "type": "integer",
                        "description": "Minor version number"
                    },
                    "patch": {
                        "type": "integer",
                        "description": "Patch version number"
                    },
                    "isStable": {
                        "type": "boolean",
                        "description": "Indicates stable release without pre-release identifier"
                    },
                    "isPreRelease": {
                        "type": "boolean",
                        "description": "Indicates pre-release version"
                    },
                    "preRelease": {
                        "type": "array",
                        "items": {
                            "type": "string"
                        },
                        "description": "Pre-release identifiers"
                    },
                    "buildMetadata": {
                        "type": "array",
                        "items": {
                            "type": "string"
                        },
                        "description": "Build metadata identifiers"
                    }
                },
                "required": ["version", "major", "minor", "patch", "isStable", "isPreRelease", "preRelease", "buildMetadata"]
            }
            """;
    private static final String DIFF_RESULT_MAJOR      = "major";
    private static final String DIFF_RESULT_MINOR      = "minor";
    private static final String DIFF_RESULT_NONE       = "none";
    private static final String DIFF_RESULT_PATCH      = "patch";
    private static final String DIFF_RESULT_PRERELEASE = "prerelease";

    private static final String ERROR_CANNOT_COERCE_EMPTY           = "Cannot coerce empty string.";
    private static final String ERROR_CANNOT_COERCE_INVALID         = "Cannot coerce to valid semantic version.";
    private static final String ERROR_INVALID_SEMANTIC_VERSION      = "Invalid semantic version: %s.";
    private static final String ERROR_INVALID_VERSION               = "Invalid version: %s.";
    private static final String ERROR_INVALID_VERSION_IN_COMPARISON = "Invalid version in comparison: %s.";
    private static final String ERROR_INVALID_VERSION_OR_RANGE      = "Invalid version or range: %s.";
    private static final String ERROR_INVALID_VERSIONS_OR_RANGE     = "Invalid versions or range: %s.";

    private static final String FIELD_BUILD_METADATA = "buildMetadata";
    private static final String FIELD_IS_PRE_RELEASE = "isPreRelease";
    private static final String FIELD_IS_STABLE      = "isStable";
    private static final String FIELD_MAJOR          = "major";
    private static final String FIELD_MINOR          = "minor";
    private static final String FIELD_PATCH          = "patch";
    private static final String FIELD_PRE_RELEASE    = "preRelease";
    private static final String FIELD_VERSION        = "version";

    private static final String SCHEMA_RETURNS_BOOLEAN = """
            {
                "type": "boolean"
            }
            """;

    private static final String SCHEMA_RETURNS_NUMBER = """
            {
                "type": "integer"
            }
            """;

    private static final String SCHEMA_RETURNS_TEXT = """
            {
                "type": "string"
            }
            """;

    /**
     * Parses a semantic version string into components.
     *
     * @param versionString
     * the version string to parse
     *
     * @return ObjectValue with version parts, or ErrorValue if parsing fails
     */
    @Function(docs = """
            ```parse(TEXT versionString)```: Parses a semantic version string into components.

            Returns an object with version parts. Accepts versions with or without lowercase `v` prefix.
            Returns error if string is not valid.

            Result fields:
            - `version`: Complete version without v-prefix
            - `major`, `minor`, `patch`: Version numbers
            - `isStable`: Boolean indicating stable release (no pre-release)
            - `isPreRelease`: Boolean indicating pre-release
            - `preRelease`: Array of pre-release identifiers
            - `buildMetadata`: Array of build metadata identifiers

            ```sapl
            policy "require_stable_major_2"
            permit
            where
              var parsed = semver.parse(request.clientVersion);
              parsed.major >= 2;
              parsed.isStable;
            ```
            """, schema = PARSED_VERSION_SCHEMA)
    public static Value parse(TextValue versionString) {
        try {
            val semver = new Semver(versionString.value());

            val resultBuilder = ObjectValue.builder();
            resultBuilder.put(FIELD_VERSION, Value.of(semver.getVersion()));
            resultBuilder.put(FIELD_MAJOR, Value.of(semver.getMajor()));
            resultBuilder.put(FIELD_MINOR, Value.of(semver.getMinor()));
            resultBuilder.put(FIELD_PATCH, Value.of(semver.getPatch()));
            resultBuilder.put(FIELD_IS_STABLE, Value.of(semver.isStable()));
            resultBuilder.put(FIELD_IS_PRE_RELEASE, Value.of(!semver.isStable()));

            val preReleaseList    = semver.getPreRelease();
            val preReleaseBuilder = ArrayValue.builder();
            preReleaseList.forEach(identifier -> preReleaseBuilder.add(Value.of(identifier)));
            resultBuilder.put(FIELD_PRE_RELEASE, preReleaseBuilder.build());

            val buildList          = semver.getBuild();
            val buildMetadataArray = ArrayValue.builder();
            buildList.forEach(identifier -> buildMetadataArray.add(Value.of(identifier)));
            resultBuilder.put(FIELD_BUILD_METADATA, buildMetadataArray.build());

            return resultBuilder.build();
        } catch (SemverException exception) {
            return Value.error(ERROR_INVALID_SEMANTIC_VERSION, exception.getMessage());
        }
    }

    /**
     * Validates semantic version format.
     *
     * @param versionString
     * the version string to validate
     *
     * @return Value.TRUE if valid, Value.FALSE otherwise
     */
    @Function(docs = """
            ```isValid(TEXT versionString)```: Validates semantic version format.

            Returns true if string conforms to Semantic Versioning 2.0.0. Accepts lowercase `v` prefix.

            ```sapl
            policy "require_valid_version"
            deny
            where
              !semver.isValid(resource.serviceVersion);
            ```
            """, schema = SCHEMA_RETURNS_BOOLEAN)
    public static Value isValid(TextValue versionString) {
        return Value.of(Semver.isValid(versionString.value()));
    }

    /**
     * Compares two semantic versions.
     *
     * @param version1
     * the first version
     * @param version2
     * the second version
     *
     * @return NumberValue -1, 0, or 1, or ErrorValue if parsing fails
     */
    @Function(docs = """
            ```compare(TEXT version1, TEXT version2)```: Compares two semantic versions.

            Returns -1 if version1 < version2, 0 if equal, 1 if version1 > version2.
            Build metadata ignored per specification.

            ```sapl
            policy "minimum_version"
            permit
            where
              semver.compare(resource.version, "2.0.0") >= 0;
            ```
            """, schema = SCHEMA_RETURNS_NUMBER)
    public static Value compare(TextValue version1, TextValue version2) {
        try {
            val v1 = new Semver(version1.value());
            val v2 = new Semver(version2.value());
            return Value.of(v1.compareTo(v2));
        } catch (SemverException exception) {
            return Value.error(ERROR_INVALID_VERSION_IN_COMPARISON, exception.getMessage());
        }
    }

    /**
     * Checks version equality.
     *
     * @param version1
     * the first version
     * @param version2
     * the second version
     *
     * @return Value.TRUE if equal, or ErrorValue if parsing fails
     */
    @Function(docs = """
            ```equals(TEXT version1, TEXT version2)```: Checks version equality.

            Returns true if versions are semantically equal. Build metadata ignored.

            ```sapl
            policy "exact_version_required"
            permit
            where
              semver.equals(resource.apiVersion, "2.1.0");
            ```
            """, schema = SCHEMA_RETURNS_BOOLEAN)
    public static Value equals(TextValue version1, TextValue version2) {
        try {
            val v1 = new Semver(version1.value());
            val v2 = new Semver(version2.value());
            return Value.of(v1.isEquivalentTo(v2));
        } catch (SemverException exception) {
            return Value.error(ERROR_INVALID_VERSION_IN_COMPARISON, exception.getMessage());
        }
    }

    /**
     * Tests if version1 is lower than version2.
     *
     * @param version1
     * the first version
     * @param version2
     * the second version
     *
     * @return Value.TRUE if version1 is lower, Value.FALSE otherwise, or ErrorValue
     * if parsing fails
     */
    @Function(docs = """
            ```isLower(TEXT version1, TEXT version2)```: Tests if version1 < version2.

            ```sapl
            policy "block_outdated_clients"
            deny
            where
              semver.isLower(subject.clientVersion, "2.0.0");
            ```
            """, schema = SCHEMA_RETURNS_BOOLEAN)
    public static Value isLower(TextValue version1, TextValue version2) {
        try {
            val v1 = new Semver(version1.value());
            val v2 = new Semver(version2.value());
            return Value.of(v1.isLowerThan(v2));
        } catch (SemverException exception) {
            return Value.error(ERROR_INVALID_VERSION_IN_COMPARISON, exception.getMessage());
        }
    }

    /**
     * Tests if version1 is higher than version2.
     *
     * @param version1
     * the first version
     * @param version2
     * the second version
     *
     * @return Value.TRUE if version1 is higher, Value.FALSE otherwise, or
     * ErrorValue if parsing fails
     */
    @Function(docs = """
            ```isHigher(TEXT version1, TEXT version2)```: Tests if version1 > version2.

            ```sapl
            policy "early_access_features"
            permit
            where
              semver.isHigher(subject.clientVersion, "3.0.0");
            ```
            """, schema = SCHEMA_RETURNS_BOOLEAN)
    public static Value isHigher(TextValue version1, TextValue version2) {
        try {
            val v1 = new Semver(version1.value());
            val v2 = new Semver(version2.value());
            return Value.of(v1.isGreaterThan(v2));
        } catch (SemverException exception) {
            return Value.error(ERROR_INVALID_VERSION_IN_COMPARISON, exception.getMessage());
        }
    }

    /**
     * Tests if version1 is lower than or equal to version2.
     *
     * @param version1
     * the first version
     * @param version2
     * the second version
     *
     * @return Value.TRUE if version1 is lower or equal, Value.FALSE otherwise, or
     * ErrorValue if parsing fails
     */
    @Function(docs = """
            ```isLowerOrEqual(TEXT version1, TEXT version2)```: Tests if version1 <= version2.

            ```sapl
            policy "version_ceiling"
            permit
            where
              semver.isLowerOrEqual(resource.apiVersion, "3.5.0");
            ```
            """, schema = SCHEMA_RETURNS_BOOLEAN)
    public static Value isLowerOrEqual(TextValue version1, TextValue version2) {
        try {
            val v1 = new Semver(version1.value());
            val v2 = new Semver(version2.value());
            return Value.of(v1.isLowerThan(v2) || v1.isEquivalentTo(v2));
        } catch (SemverException exception) {
            return Value.error(ERROR_INVALID_VERSION_IN_COMPARISON, exception.getMessage());
        }
    }

    /**
     * Tests if version1 is higher than or equal to version2.
     *
     * @param version1
     * the first version
     * @param version2
     * the second version
     *
     * @return Value.TRUE if version1 is higher or equal, Value.FALSE otherwise, or
     * ErrorValue if parsing fails
     */
    @Function(docs = """
            ```isHigherOrEqual(TEXT version1, TEXT version2)```: Tests if version1 >= version2.

            ```sapl
            policy "feature_gate"
            permit
            where
              action.name == "advancedFeature";
              semver.isHigherOrEqual(request.version, "2.5.0");
            ```
            """, schema = SCHEMA_RETURNS_BOOLEAN)
    public static Value isHigherOrEqual(TextValue version1, TextValue version2) {
        try {
            val v1 = new Semver(version1.value());
            val v2 = new Semver(version2.value());
            return Value.of(v1.isGreaterThan(v2) || v1.isEquivalentTo(v2));
        } catch (SemverException exception) {
            return Value.error(ERROR_INVALID_VERSION_IN_COMPARISON, exception.getMessage());
        }
    }

    /**
     * Tests for matching major version.
     *
     * @param version1
     * the first version
     * @param version2
     * the second version
     *
     * @return Value.TRUE if major versions match, Value.FALSE otherwise, or
     * ErrorValue if parsing fails
     */
    @Function(docs = """
            ```haveSameMajor(TEXT version1, TEXT version2)```: Tests for matching major version.

            ```sapl
            policy "api_v2_only"
            permit
            where
              semver.haveSameMajor(resource.apiVersion, "2.0.0");
            ```
            """, schema = SCHEMA_RETURNS_BOOLEAN)
    public static Value haveSameMajor(TextValue version1, TextValue version2) {
        try {
            val v1 = new Semver(version1.value());
            val v2 = new Semver(version2.value());
            return Value.of(v1.getMajor() == v2.getMajor());
        } catch (SemverException exception) {
            return Value.error(ERROR_INVALID_VERSION_IN_COMPARISON, exception.getMessage());
        }
    }

    /**
     * Tests for matching major and minor version.
     *
     * @param version1
     * the first version
     * @param version2
     * the second version
     *
     * @return Value.TRUE if major and minor versions match, Value.FALSE otherwise,
     * or ErrorValue if parsing fails
     */
    @Function(docs = """
            ```haveSameMinor(TEXT version1, TEXT version2)```: Tests for matching major and minor version.

            ```sapl
            policy "minor_version_match"
            permit
            where
              semver.haveSameMinor(resource.version, "2.3.0");
            ```
            """, schema = SCHEMA_RETURNS_BOOLEAN)
    public static Value haveSameMinor(TextValue version1, TextValue version2) {
        try {
            val v1 = new Semver(version1.value());
            val v2 = new Semver(version2.value());
            return Value.of(v1.getMajor() == v2.getMajor() && v1.getMinor() == v2.getMinor());
        } catch (SemverException exception) {
            return Value.error(ERROR_INVALID_VERSION_IN_COMPARISON, exception.getMessage());
        }
    }

    /**
     * Tests for matching major, minor, and patch version.
     *
     * @param version1
     * the first version
     * @param version2
     * the second version
     *
     * @return Value.TRUE if all version components match, Value.FALSE otherwise, or
     * ErrorValue if parsing fails
     */
    @Function(docs = """
            ```haveSamePatch(TEXT version1, TEXT version2)```: Tests for matching major, minor, and patch version.

            ```sapl
            policy "exact_patch_match"
            permit
            where
              semver.haveSamePatch(resource.version, "2.3.5");
            ```
            """, schema = SCHEMA_RETURNS_BOOLEAN)
    public static Value haveSamePatch(TextValue version1, TextValue version2) {
        try {
            val v1 = new Semver(version1.value());
            val v2 = new Semver(version2.value());
            return Value.of(
                    v1.getMajor() == v2.getMajor() && v1.getMinor() == v2.getMinor() && v1.getPatch() == v2.getPatch());
        } catch (SemverException exception) {
            return Value.error(ERROR_INVALID_VERSION_IN_COMPARISON, exception.getMessage());
        }
    }

    /**
     * Tests API compatibility.
     *
     * @param version1
     * the first version
     * @param version2
     * the second version
     *
     * @return Value.TRUE if compatible, Value.FALSE otherwise, or ErrorValue if
     * parsing fails
     */
    @Function(docs = """
            ```isCompatibleWith(TEXT version1, TEXT version2)```: Tests API compatibility.

            Returns true if versions are API-compatible per semantic versioning:
            - Major 0 (0.y.z): Only exact major.minor match is compatible
            - Major 1+: Same major version indicates compatibility

            ```sapl
            policy "api_compatibility"
            permit
            where
              semver.isCompatibleWith(subject.clientVersion, resource.apiVersion);
            ```
            """, schema = SCHEMA_RETURNS_BOOLEAN)
    public static Value isCompatibleWith(TextValue version1, TextValue version2) {
        try {
            val v1 = new Semver(version1.value());
            val v2 = new Semver(version2.value());

            if (v2.getMajor() == 0) {
                return Value.of(v1.getMajor() == v2.getMajor() && v1.getMinor() == v2.getMinor());
            }

            return Value.of(v1.getMajor() == v2.getMajor());
        } catch (SemverException exception) {
            return Value.error(ERROR_INVALID_VERSION_IN_COMPARISON, exception.getMessage());
        }
    }

    /**
     * Tests if version meets minimum.
     *
     * @param version
     * the version to check
     * @param minimum
     * the minimum version
     *
     * @return Value.TRUE if version meets minimum, Value.FALSE otherwise, or
     * ErrorValue if parsing fails
     */
    @Function(docs = """
            ```isAtLeast(TEXT version, TEXT minimum)```: Tests if version meets minimum.

            Alias for isHigherOrEqual. Enforces minimum version requirements.

            ```sapl
            policy "security_requirement"
            permit
            where
              semver.isAtLeast(request.version, resource.minimumSecureVersion);
            ```
            """, schema = SCHEMA_RETURNS_BOOLEAN)
    public static Value isAtLeast(TextValue version, TextValue minimum) {
        return isHigherOrEqual(version, minimum);
    }

    /**
     * Tests if version is at or below maximum.
     *
     * @param version
     * the version to check
     * @param maximum
     * the maximum version
     *
     * @return Value.TRUE if version is at or below maximum, Value.FALSE otherwise,
     * or ErrorValue if parsing fails
     */
    @Function(docs = """
            ```isAtMost(TEXT version, TEXT maximum)```: Tests if version is at or below maximum.

            Alias for isLowerOrEqual. Enforces maximum version constraints.

            ```sapl
            policy "deprecated_after"
            deny
            where
              !semver.isAtMost(subject.apiVersion, "2.9.9");
            ```
            """, schema = SCHEMA_RETURNS_BOOLEAN)
    public static Value isAtMost(TextValue version, TextValue maximum) {
        return isLowerOrEqual(version, maximum);
    }

    /**
     * Tests if version is in range.
     *
     * @param version
     * the version to check
     * @param minimum
     * the minimum version (inclusive)
     * @param maximum
     * the maximum version (inclusive)
     *
     * @return Value.TRUE if version is in range, Value.FALSE otherwise, or
     * ErrorValue if parsing fails
     */
    @Function(docs = """
            ```isBetween(TEXT version, TEXT minimum, TEXT maximum)```: Tests if version is in range.

            Returns true if minimum <= version <= maximum. Both bounds inclusive.

            ```sapl
            policy "compatibility_window"
            permit
            where
              semver.isBetween(request.version, resource.minVersion, resource.maxVersion);
            ```
            """, schema = SCHEMA_RETURNS_BOOLEAN)
    public static Value isBetween(TextValue version, TextValue minimum, TextValue maximum) {
        try {
            val v   = new Semver(version.value());
            val min = new Semver(minimum.value());
            val max = new Semver(maximum.value());

            return Value.of(
                    (v.isGreaterThan(min) || v.isEquivalentTo(min)) && (v.isLowerThan(max) || v.isEquivalentTo(max)));
        } catch (SemverException exception) {
            return Value.error(ERROR_INVALID_VERSION_IN_COMPARISON, exception.getMessage());
        }
    }

    /**
     * Tests if version is pre-release.
     *
     * @param version
     * the version to check
     *
     * @return Value.TRUE if pre-release, Value.FALSE if stable, or ErrorValue if
     * parsing fails
     */
    @Function(docs = """
            ```isPreRelease(TEXT version)```: Tests if version is pre-release.

            Returns true if version contains pre-release identifier (e.g., alpha, beta, rc).

            ```sapl
            policy "block_unstable"
            deny
            where
              semver.isPreRelease(resource.version);
            ```
            """, schema = SCHEMA_RETURNS_BOOLEAN)
    public static Value isPreRelease(TextValue version) {
        try {
            val v = new Semver(version.value());
            return Value.of(!v.isStable());
        } catch (SemverException exception) {
            return Value.error(ERROR_INVALID_VERSION, exception.getMessage());
        }
    }

    /**
     * Tests if version is stable release.
     *
     * @param version
     * the version to check
     *
     * @return Value.TRUE if stable, Value.FALSE if pre-release, or ErrorValue if
     * parsing fails
     */
    @Function(docs = """
            ```isStable(TEXT version)```: Tests if version is stable release.

            Returns true if version does not contain pre-release identifier.

            ```sapl
            policy "production_ready"
            permit
            where
              semver.isStable(resource.version);
              action.name == "production";
            ```
            """, schema = SCHEMA_RETURNS_BOOLEAN)
    public static Value isStable(TextValue version) {
        try {
            val v = new Semver(version.value());
            return Value.of(v.isStable());
        } catch (SemverException exception) {
            return Value.error(ERROR_INVALID_VERSION, exception.getMessage());
        }
    }

    /**
     * Extracts major version number.
     *
     * @param version
     * the version to parse
     *
     * @return NumberValue, or ErrorValue if parsing fails
     */
    @Function(docs = """
            ```getMajor(TEXT version)```: Extracts major version number.

            ```sapl
            policy "api_v3_only"
            permit
            where
              semver.getMajor(subject.apiVersion) == 3;
            ```
            """, schema = SCHEMA_RETURNS_NUMBER)
    public static Value getMajor(TextValue version) {
        try {
            val v = new Semver(version.value());
            return Value.of(v.getMajor());
        } catch (SemverException exception) {
            return Value.error(ERROR_INVALID_VERSION, exception.getMessage());
        }
    }

    /**
     * Extracts minor version number.
     *
     * @param version
     * the version to parse
     *
     * @return NumberValue, or ErrorValue if parsing fails
     */
    @Function(docs = """
            ```getMinor(TEXT version)```: Extracts minor version number.

            ```sapl
            policy "feature_availability"
            permit
            where
              semver.getMajor(subject.version) == 2;
              semver.getMinor(subject.version) >= 3;
            ```
            """, schema = SCHEMA_RETURNS_NUMBER)
    public static Value getMinor(TextValue version) {
        try {
            val v = new Semver(version.value());
            return Value.of(v.getMinor());
        } catch (SemverException exception) {
            return Value.error(ERROR_INVALID_VERSION, exception.getMessage());
        }
    }

    /**
     * Extracts patch version number.
     *
     * @param version
     * the version to parse
     *
     * @return NumberValue, or ErrorValue if parsing fails
     */
    @Function(docs = """
            ```getPatch(TEXT version)```: Extracts patch version number.

            ```sapl
            policy "specific_patch"
            permit
            where
              semver.getMajor(subject.version) == 1;
              semver.getMinor(subject.version) == 0;
              semver.getPatch(subject.version) >= 5;
            ```
            """, schema = SCHEMA_RETURNS_NUMBER)
    public static Value getPatch(TextValue version) {
        try {
            val v = new Semver(version.value());
            return Value.of(v.getPatch());
        } catch (SemverException exception) {
            return Value.error(ERROR_INVALID_VERSION, exception.getMessage());
        }
    }

    /**
     * Tests if version satisfies range expression.
     *
     * @param version
     * the version to test
     * @param range
     * the range expression
     *
     * @return Value.TRUE if satisfies, Value.FALSE otherwise, or ErrorValue if
     * version is invalid
     */
    @Function(docs = """
            ```satisfies(TEXT version, TEXT range)```: Tests if version satisfies range expression.

            Returns true if version satisfies NPM-style range. Supports operators (>=, >, <=, <, =),
            hyphen ranges (1.2.3 - 2.3.4), X-ranges (1.2.x, 1.x, *), tilde (~1.2.3 allows patch),
            caret (^1.2.3 allows minor), and logical operators (||, space-separated AND).

            ```sapl
            policy "version_range"
            permit
            where
              semver.satisfies(subject.clientVersion, ">=2.0.0 <3.0.0");
            ```
            """, schema = SCHEMA_RETURNS_BOOLEAN)
    public static Value satisfies(TextValue version, TextValue range) {
        try {
            val v = new Semver(version.value());
            return Value.of(v.satisfies(range.value()));
        } catch (SemverException exception) {
            return Value.error(ERROR_INVALID_VERSION_OR_RANGE, exception.getMessage());
        }
    }

    /**
     * Finds highest version matching range.
     *
     * @param versions
     * array of version strings
     * @param range
     * the range expression
     *
     * @return TextValue with highest matching version, NullValue if none match, or
     * ErrorValue on parse failure
     */
    @Function(docs = """
            ```maxSatisfying(ARRAY versions, TEXT range)```: Finds highest version matching range.

            Returns highest version from array satisfying range, or null if none match.

            ```sapl
            policy "use_latest_compatible"
            permit
            where
              var compatible = semver.maxSatisfying(resource.availableVersions, "^2.0.0");
              compatible != null;
            ```
            """, schema = SCHEMA_RETURNS_TEXT)
    public static Value maxSatisfying(ArrayValue versions, TextValue range) {
        try {
            val rangeExpression = range.value();

            Semver maxVersion = null;
            for (val element : versions) {
                if (element instanceof TextValue textValue) {
                    val currentVersion = new Semver(textValue.value());
                    if (currentVersion.satisfies(rangeExpression)
                            && (maxVersion == null || currentVersion.isGreaterThan(maxVersion))) {
                        maxVersion = currentVersion;
                    }
                }
            }

            return maxVersion != null ? Value.of(maxVersion.getVersion()) : Value.NULL;
        } catch (SemverException exception) {
            return Value.error(ERROR_INVALID_VERSIONS_OR_RANGE, exception.getMessage());
        }
    }

    /**
     * Finds lowest version matching range.
     *
     * @param versions
     * array of version strings
     * @param range
     * the range expression
     *
     * @return TextValue with lowest matching version, NullValue if none match, or
     * ErrorValue on parse failure
     */
    @Function(docs = """
            ```minSatisfying(ARRAY versions, TEXT range)```: Finds lowest version matching range.

            Returns lowest version from array satisfying range, or null if none match.

            ```sapl
            policy "require_minimum"
            permit
            where
              var minimum = semver.minSatisfying(resource.supportedVersions, ">=1.0.0");
              semver.isAtLeast(subject.clientVersion, minimum);
            ```
            """, schema = SCHEMA_RETURNS_TEXT)
    public static Value minSatisfying(ArrayValue versions, TextValue range) {
        try {
            val rangeExpression = range.value();

            Semver minVersion = null;
            for (val element : versions) {
                if (element instanceof TextValue textValue) {
                    val currentVersion = new Semver(textValue.value());
                    if (currentVersion.satisfies(rangeExpression)
                            && (minVersion == null || currentVersion.isLowerThan(minVersion))) {
                        minVersion = currentVersion;
                    }
                }
            }

            return minVersion != null ? Value.of(minVersion.getVersion()) : Value.NULL;
        } catch (SemverException exception) {
            return Value.error(ERROR_INVALID_VERSIONS_OR_RANGE, exception.getMessage());
        }
    }

    /**
     * Coerces string to valid semantic version.
     *
     * @param value
     * the string to coerce
     *
     * @return TextValue with normalized version, or ErrorValue if coercion fails
     */
    @Function(docs = """
            ```coerce(TEXT value)```: Coerces string to valid semantic version.

            Normalizes common formats including partial versions (e.g., "1.2" becomes "1.2.0"),
            lowercase v-prefix removal, and other standard version formats.

            ```sapl
            policy "normalize_version"
            permit
            where
              var normalized = semver.coerce(request.version);
              semver.isAtLeast(normalized, "2.0.0");
            ```
            """, schema = SCHEMA_RETURNS_TEXT)
    public static Value coerce(TextValue value) {
        val text = value.value();
        if (text.isEmpty()) {
            return Value.error(ERROR_CANNOT_COERCE_EMPTY);
        }

        val coerced = Semver.coerce(text);
        if (coerced == null) {
            return Value.error(ERROR_CANNOT_COERCE_INVALID);
        }

        return Value.of(coerced.getVersion());
    }

    /**
     * Determines version change type.
     *
     * @param version1
     * the first version
     * @param version2
     * the second version
     *
     * @return TextValue with change type, or ErrorValue if parsing fails
     */
    @Function(docs = """
            ```diff(TEXT version1, TEXT version2)```: Determines version change type.

            Returns "major", "minor", "patch", "prerelease", or "none" for equal versions.
            Determines if version change requires approval or special handling.

            ```sapl
            policy "breaking_change_approval"
            permit action.name == "deploy"
            where
              var changeType = semver.diff(resource.currentVersion, resource.newVersion);
              changeType == "major" implies subject.role == "architect";
            ```
            """, schema = SCHEMA_RETURNS_TEXT)
    public static Value diff(TextValue version1, TextValue version2) {
        try {
            val v1 = new Semver(version1.value());
            val v2 = new Semver(version2.value());

            if (v1.isEquivalentTo(v2)) {
                return Value.of(DIFF_RESULT_NONE);
            }

            if (v1.getMajor() != v2.getMajor()) {
                return Value.of(DIFF_RESULT_MAJOR);
            }

            if (v1.getMinor() != v2.getMinor()) {
                return Value.of(DIFF_RESULT_MINOR);
            }

            if (v1.getPatch() != v2.getPatch()) {
                return Value.of(DIFF_RESULT_PATCH);
            }

            val preRelease1 = v1.getPreRelease();
            val preRelease2 = v2.getPreRelease();
            if (preRelease1.isEmpty() != preRelease2.isEmpty()) {
                return Value.of(DIFF_RESULT_PRERELEASE);
            }
            if (!preRelease1.equals(preRelease2)) {
                return Value.of(DIFF_RESULT_PRERELEASE);
            }

            return Value.of(DIFF_RESULT_NONE);
        } catch (SemverException exception) {
            return Value.error(ERROR_INVALID_VERSION, exception.getMessage());
        }
    }
}
