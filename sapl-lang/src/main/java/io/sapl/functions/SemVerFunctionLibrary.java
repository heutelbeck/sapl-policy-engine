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

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Array;
import io.sapl.api.validation.Text;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.semver4j.Semver;
import org.semver4j.SemverException;

import java.util.ArrayList;
import java.util.List;

/**
 * Functions for semantic version comparison and validation in authorization
 * policies.
 * Supports version-based access control, API compatibility checks, and feature
 * gating.
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

    private static final String PARSED_VERSION_SCHEMA = """
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
    private static final String RETURNS_BOOLEAN       = """
            {
                "type": "boolean"
            }
            """;

    private static final String RETURNS_NUMBER = """
            {
                "type": "integer"
            }
            """;

    private static final String RETURNS_TEXT                  = """
            {
                "type": "string"
            }
            """;
    public static final String  INVALID_VERSION_IN_COMPARISON = "Invalid version in comparison: ";
    public static final String  INVALID_VERSION               = "Invalid version: ";

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
    public static Val parse(@Text Val versionString) {
        try {
            val semver = new Semver(versionString.getText());

            val result = Val.JSON.objectNode();
            result.put("version", semver.getVersion());
            result.put("major", semver.getMajor());
            result.put("minor", semver.getMinor());
            result.put("patch", semver.getPatch());
            result.put("isStable", semver.isStable());
            result.put("isPreRelease", !semver.isStable());

            val preReleaseList  = semver.getPreRelease();
            val preReleaseArray = result.putArray("preRelease");
            preReleaseList.forEach(preReleaseArray::add);

            val buildList          = semver.getBuild();
            val buildMetadataArray = result.putArray("buildMetadata");
            buildList.forEach(buildMetadataArray::add);

            return Val.of(result);
        } catch (SemverException e) {
            return Val.error("Invalid semantic version: " + e.getMessage());
        }
    }

    @Function(docs = """
            ```isValid(TEXT versionString)```: Validates semantic version format.

            Returns true if string conforms to Semantic Versioning 2.0.0. Accepts lowercase `v` prefix.

            ```sapl
            policy "require_valid_version"
            deny
            where
              !semver.isValid(resource.serviceVersion);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isValid(@Text Val versionString) {
        return Val.of(Semver.isValid(versionString.getText()));
    }

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
            """, schema = RETURNS_NUMBER)
    public static Val compare(@Text Val version1, @Text Val version2) {
        try {
            val v1 = new Semver(version1.getText());
            val v2 = new Semver(version2.getText());
            return Val.of(v1.compareTo(v2));
        } catch (SemverException e) {
            return Val.error(INVALID_VERSION_IN_COMPARISON + e.getMessage());
        }
    }

    @Function(docs = """
            ```equals(TEXT version1, TEXT version2)```: Checks version equality.

            Returns true if versions are semantically equal. Build metadata ignored.

            ```sapl
            policy "exact_version_required"
            permit
            where
              semver.equals(resource.apiVersion, "2.1.0");
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val equals(@Text Val version1, @Text Val version2) {
        try {
            val v1 = new Semver(version1.getText());
            val v2 = new Semver(version2.getText());
            return Val.of(v1.isEquivalentTo(v2));
        } catch (SemverException e) {
            return Val.error(INVALID_VERSION_IN_COMPARISON + e.getMessage());
        }
    }

    @Function(docs = """
            ```isLower(TEXT version1, TEXT version2)```: Tests if version1 < version2.

            ```sapl
            policy "block_outdated_clients"
            deny
            where
              semver.isLower(subject.clientVersion, "2.0.0");
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isLower(@Text Val version1, @Text Val version2) {
        try {
            val v1 = new Semver(version1.getText());
            val v2 = new Semver(version2.getText());
            return Val.of(v1.isLowerThan(v2));
        } catch (SemverException e) {
            return Val.error(INVALID_VERSION_IN_COMPARISON + e.getMessage());
        }
    }

    @Function(docs = """
            ```isHigher(TEXT version1, TEXT version2)```: Tests if version1 > version2.

            ```sapl
            policy "early_access_features"
            permit
            where
              semver.isHigher(subject.clientVersion, "3.0.0");
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isHigher(@Text Val version1, @Text Val version2) {
        try {
            val v1 = new Semver(version1.getText());
            val v2 = new Semver(version2.getText());
            return Val.of(v1.isGreaterThan(v2));
        } catch (SemverException e) {
            return Val.error(INVALID_VERSION_IN_COMPARISON + e.getMessage());
        }
    }

    @Function(docs = """
            ```isLowerOrEqual(TEXT version1, TEXT version2)```: Tests if version1 <= version2.

            ```sapl
            policy "version_ceiling"
            permit
            where
              semver.isLowerOrEqual(resource.apiVersion, "3.5.0");
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isLowerOrEqual(@Text Val version1, @Text Val version2) {
        try {
            val v1 = new Semver(version1.getText());
            val v2 = new Semver(version2.getText());
            return Val.of(v1.isLowerThan(v2) || v1.isEquivalentTo(v2));
        } catch (SemverException e) {
            return Val.error(INVALID_VERSION_IN_COMPARISON + e.getMessage());
        }
    }

    @Function(docs = """
            ```isHigherOrEqual(TEXT version1, TEXT version2)```: Tests if version1 >= version2.

            ```sapl
            policy "feature_gate"
            permit
            where
              action.name == "advancedFeature";
              semver.isHigherOrEqual(request.version, "2.5.0");
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isHigherOrEqual(@Text Val version1, @Text Val version2) {
        try {
            val v1 = new Semver(version1.getText());
            val v2 = new Semver(version2.getText());
            return Val.of(v1.isGreaterThan(v2) || v1.isEquivalentTo(v2));
        } catch (SemverException e) {
            return Val.error(INVALID_VERSION_IN_COMPARISON + e.getMessage());
        }
    }

    @Function(docs = """
            ```haveSameMajor(TEXT version1, TEXT version2)```: Tests for matching major version.

            ```sapl
            policy "api_v2_only"
            permit
            where
              semver.haveSameMajor(resource.apiVersion, "2.0.0");
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val haveSameMajor(@Text Val version1, @Text Val version2) {
        try {
            val v1 = new Semver(version1.getText());
            val v2 = new Semver(version2.getText());
            return Val.of(v1.getMajor() == v2.getMajor());
        } catch (SemverException e) {
            return Val.error(INVALID_VERSION_IN_COMPARISON + e.getMessage());
        }
    }

    @Function(docs = """
            ```haveSameMinor(TEXT version1, TEXT version2)```: Tests for matching major and minor version.

            ```sapl
            policy "minor_version_match"
            permit
            where
              semver.haveSameMinor(resource.version, "2.3.0");
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val haveSameMinor(@Text Val version1, @Text Val version2) {
        try {
            val v1 = new Semver(version1.getText());
            val v2 = new Semver(version2.getText());
            return Val.of(v1.getMajor() == v2.getMajor() && v1.getMinor() == v2.getMinor());
        } catch (SemverException e) {
            return Val.error(INVALID_VERSION_IN_COMPARISON + e.getMessage());
        }
    }

    @Function(docs = """
            ```haveSamePatch(TEXT version1, TEXT version2)```: Tests for matching major, minor, and patch version.

            ```sapl
            policy "exact_patch_match"
            permit
            where
              semver.haveSamePatch(resource.version, "2.3.5");
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val haveSamePatch(@Text Val version1, @Text Val version2) {
        try {
            val v1 = new Semver(version1.getText());
            val v2 = new Semver(version2.getText());
            return Val.of(
                    v1.getMajor() == v2.getMajor() && v1.getMinor() == v2.getMinor() && v1.getPatch() == v2.getPatch());
        } catch (SemverException e) {
            return Val.error(INVALID_VERSION_IN_COMPARISON + e.getMessage());
        }
    }

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
            """, schema = RETURNS_BOOLEAN)
    public static Val isCompatibleWith(@Text Val version1, @Text Val version2) {
        try {
            val v1 = new Semver(version1.getText());
            val v2 = new Semver(version2.getText());

            if (v2.getMajor() == 0) {
                return Val.of(v1.getMajor() == v2.getMajor() && v1.getMinor() == v2.getMinor());
            }

            return Val.of(v1.getMajor() == v2.getMajor());
        } catch (SemverException e) {
            return Val.error(INVALID_VERSION_IN_COMPARISON + e.getMessage());
        }
    }

    @Function(docs = """
            ```isAtLeast(TEXT version, TEXT minimum)```: Tests if version meets minimum.

            Alias for isHigherOrEqual. Enforces minimum version requirements.

            ```sapl
            policy "security_requirement"
            permit
            where
              semver.isAtLeast(request.version, resource.minimumSecureVersion);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isAtLeast(@Text Val version, @Text Val minimum) {
        return isHigherOrEqual(version, minimum);
    }

    @Function(docs = """
            ```isAtMost(TEXT version, TEXT maximum)```: Tests if version is at or below maximum.

            Alias for isLowerOrEqual. Enforces maximum version constraints.

            ```sapl
            policy "deprecated_after"
            deny
            where
              !semver.isAtMost(subject.apiVersion, "2.9.9");
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isAtMost(@Text Val version, @Text Val maximum) {
        return isLowerOrEqual(version, maximum);
    }

    @Function(docs = """
            ```isBetween(TEXT version, TEXT minimum, TEXT maximum)```: Tests if version is in range.

            Returns true if minimum <= version <= maximum. Both bounds inclusive.

            ```sapl
            policy "compatibility_window"
            permit
            where
              semver.isBetween(request.version, resource.minVersion, resource.maxVersion);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isBetween(@Text Val version, @Text Val minimum, @Text Val maximum) {
        try {
            val v   = new Semver(version.getText());
            val min = new Semver(minimum.getText());
            val max = new Semver(maximum.getText());

            return Val.of(
                    (v.isGreaterThan(min) || v.isEquivalentTo(min)) && (v.isLowerThan(max) || v.isEquivalentTo(max)));
        } catch (SemverException e) {
            return Val.error(INVALID_VERSION_IN_COMPARISON + e.getMessage());
        }
    }

    @Function(docs = """
            ```isPreRelease(TEXT version)```: Tests if version is pre-release.

            Returns true if version contains pre-release identifier (e.g., alpha, beta, rc).

            ```sapl
            policy "block_unstable"
            deny
            where
              semver.isPreRelease(resource.version);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isPreRelease(@Text Val version) {
        try {
            val v = new Semver(version.getText());
            return Val.of(!v.isStable());
        } catch (SemverException e) {
            return Val.error(INVALID_VERSION + e.getMessage());
        }
    }

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
            """, schema = RETURNS_BOOLEAN)
    public static Val isStable(@Text Val version) {
        try {
            val v = new Semver(version.getText());
            return Val.of(v.isStable());
        } catch (SemverException e) {
            return Val.error(INVALID_VERSION + e.getMessage());
        }
    }

    @Function(docs = """
            ```getMajor(TEXT version)```: Extracts major version number.

            ```sapl
            policy "api_v3_only"
            permit
            where
              semver.getMajor(subject.apiVersion) == 3;
            ```
            """, schema = RETURNS_NUMBER)
    public static Val getMajor(@Text Val version) {
        try {
            val v = new Semver(version.getText());
            return Val.of(v.getMajor());
        } catch (SemverException e) {
            return Val.error(INVALID_VERSION + e.getMessage());
        }
    }

    @Function(docs = """
            ```getMinor(TEXT version)```: Extracts minor version number.

            ```sapl
            policy "feature_availability"
            permit
            where
              semver.getMajor(subject.version) == 2;
              semver.getMinor(subject.version) >= 3;
            ```
            """, schema = RETURNS_NUMBER)
    public static Val getMinor(@Text Val version) {
        try {
            val v = new Semver(version.getText());
            return Val.of(v.getMinor());
        } catch (SemverException e) {
            return Val.error(INVALID_VERSION + e.getMessage());
        }
    }

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
            """, schema = RETURNS_NUMBER)
    public static Val getPatch(@Text Val version) {
        try {
            val v = new Semver(version.getText());
            return Val.of(v.getPatch());
        } catch (SemverException e) {
            return Val.error(INVALID_VERSION + e.getMessage());
        }
    }

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
            """, schema = RETURNS_BOOLEAN)
    public static Val satisfies(@Text Val version, @Text Val range) {
        try {
            val v = new Semver(version.getText());
            return Val.of(v.satisfies(range.getText()));
        } catch (SemverException e) {
            return Val.error("Invalid version or range: " + e.getMessage());
        }
    }

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
            """, schema = RETURNS_TEXT)
    public static Val maxSatisfying(@Array Val versions, @Text Val range) {
        try {
            val versionStrings  = extractVersionStrings(versions);
            val rangeExpression = range.getText();

            Semver maxVersion = null;
            for (val versionString : versionStrings) {
                val currentVersion = new Semver(versionString);
                if (currentVersion.satisfies(rangeExpression)
                        && (maxVersion == null || currentVersion.isGreaterThan(maxVersion))) {
                    maxVersion = currentVersion;
                }

            }

            return maxVersion != null ? Val.of(maxVersion.getVersion()) : Val.NULL;
        } catch (SemverException e) {
            return Val.error("Invalid versions or range: " + e.getMessage());
        }
    }

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
            """, schema = RETURNS_TEXT)
    public static Val minSatisfying(@Array Val versions, @Text Val range) {
        try {
            val versionStrings  = extractVersionStrings(versions);
            val rangeExpression = range.getText();

            Semver minVersion = null;
            for (val versionString : versionStrings) {
                val currentVersion = new Semver(versionString);
                if (currentVersion.satisfies(rangeExpression)
                        && (minVersion == null || currentVersion.isLowerThan(minVersion))) {
                    minVersion = currentVersion;
                }

            }

            return minVersion != null ? Val.of(minVersion.getVersion()) : Val.NULL;
        } catch (SemverException e) {
            return Val.error("Invalid versions or range: " + e.getMessage());
        }
    }

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
            """, schema = RETURNS_TEXT)
    public static Val coerce(@Text Val value) {
        try {
            val text = value.getText();
            if (text == null || text.isEmpty()) {
                return Val.error("Cannot coerce empty string");
            }

            val coerced = Semver.coerce(text);
            if (coerced == null) {
                return Val.error("Cannot coerce to valid semantic version");
            }

            return Val.of(coerced.getVersion());
        } catch (SemverException e) {
            return Val.error("Coercion failed: " + e.getMessage());
        }
    }

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
            """, schema = RETURNS_TEXT)
    public static Val diff(@Text Val version1, @Text Val version2) {
        try {
            val v1 = new Semver(version1.getText());
            val v2 = new Semver(version2.getText());

            if (v1.isEquivalentTo(v2)) {
                return Val.of("none");
            }

            if (v1.getMajor() != v2.getMajor()) {
                return Val.of("major");
            }

            if (v1.getMinor() != v2.getMinor()) {
                return Val.of("minor");
            }

            if (v1.getPatch() != v2.getPatch()) {
                return Val.of("patch");
            }

            val preRelease1 = v1.getPreRelease();
            val preRelease2 = v2.getPreRelease();
            if (preRelease1.isEmpty() != preRelease2.isEmpty()) {
                return Val.of("prerelease");
            }
            if (!preRelease1.equals(preRelease2)) {
                return Val.of("prerelease");
            }

            return Val.of("none");
        } catch (SemverException e) {
            return Val.error(INVALID_VERSION + e.getMessage());
        }
    }

    /**
     * Extracts version strings from JSON array.
     * Filters textual elements only.
     *
     * @param versions JSON array containing version strings
     * @return list of version strings
     */
    private static List<String> extractVersionStrings(Val versions) {
        val versionStrings = new ArrayList<String>();
        for (JsonNode element : versions.get()) {
            if (element.isTextual()) {
                versionStrings.add(element.asText());
            }
        }
        return versionStrings;
    }
}
