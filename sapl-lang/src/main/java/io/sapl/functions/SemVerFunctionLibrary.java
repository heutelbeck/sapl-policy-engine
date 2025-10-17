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

import de.skuzzle.semantic.Version;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Text;
import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Functions for semantic version comparison and validation in authorization
 * policies.
 * Enables version-based access control decisions, API version compatibility
 * checks, and feature gate evaluation based on client or service versions.
 */
@UtilityClass
@FunctionLibrary(name = SemVerFunctionLibrary.NAME, description = SemVerFunctionLibrary.DESCRIPTION, libraryDocumentation = SemVerFunctionLibrary.DOCUMENTATION)
public class SemVerFunctionLibrary {

    public static final String NAME        = "semver";
    public static final String DESCRIPTION = "Functions for semantic version comparison and validation in authorization policies.";

    public static final String DOCUMENTATION = """
            # Semantic Versioning in Authorization Policies

            This library provides functions for working with semantic version strings following the Semantic
            Versioning 2.0.0 specification. Use these functions for version-based access control, API
            compatibility checks, and feature gating based on client or service versions.

            ## Version Format

            Semantic versions follow the format: `MAJOR.MINOR.PATCH[-PRERELEASE][+BUILDMETADATA]`

            - **MAJOR**: Incompatible API changes (e.g., `2.0.0`)
            - **MINOR**: Backwards-compatible functionality additions (e.g., `1.5.0`)
            - **PATCH**: Backwards-compatible bug fixes (e.g., `1.0.3`)
            - **PRERELEASE**: Optional pre-release identifier with dot-separated alphanumeric identifiers
              (e.g., `alpha`, `beta.1`, `rc.2`)
            - **BUILDMETADATA**: Optional build metadata with dot-separated alphanumeric identifiers
              (e.g., `build.123`, `sha.5114f85`)

            Examples: `1.0.0`, `2.3.5-alpha`, `1.0.0-beta.2+build.456`, `v3.0.0` (v-prefix supported)

            ## Version Comparison

            Versions are compared following semantic versioning precedence rules:
            1. Major, minor, and patch numbers are compared numerically
            2. Pre-release versions have lower precedence than normal versions
            3. Pre-release identifiers are compared lexically
            4. Build metadata is ignored in comparisons

            ## Access Control Use Cases

            - **API Versioning**: Block access to deprecated API versions
            - **Feature Gates**: Enable features for versions meeting minimum requirements
            - **Client Requirements**: Enforce minimum client version for security or compatibility
            - **Compatibility Windows**: Allow only compatible version ranges
            - **Pre-release Access**: Restrict beta/alpha versions to specific user groups
            - **Deprecation Policies**: Warn or block outdated versions
            """;

    private static final String RETURNS_OBJECT = """
            {
                "type": "object"
            }
            """;

    private static final String RETURNS_BOOLEAN = """
            {
                "type": "boolean"
            }
            """;

    private static final String RETURNS_NUMBER = """
            {
                "type": "integer"
            }
            """;

    @Function(docs = """
            ```parse(TEXT versionString)```: Parses a semantic version string into its components.

            Returns an object containing all parts of the version. Accepts versions with or without
            'v' prefix. Returns error if the string is not a valid semantic version.

            **Result Object**:
            - `version`: Complete version string without v-prefix
            - `major`: Major version number
            - `minor`: Minor version number
            - `patch`: Patch version number
            - `preRelease`: Pre-release identifier (empty string if none)
            - `buildMetadata`: Build metadata (empty string if none)

            **Examples:**
            ```sapl
            policy "parse_client_version"
            permit
            where
              var parsed = semver.parse(request.clientVersion);
              parsed.major >= 2;
            ```

            ```sapl
            policy "check_prerelease"
            permit
            where
              var version = semver.parse(resource.apiVersion);
              version.preRelease == "";
            ```
            """, schema = RETURNS_OBJECT)
    public static Val parse(@Text Val versionString) {
        try {
            val text    = stripVPrefix(versionString.getText());
            val version = Version.parseVersion(text);

            val result = Val.JSON.objectNode();
            result.put("version", version.toString());
            result.put("major", version.getMajor());
            result.put("minor", version.getMinor());
            result.put("patch", version.getPatch());
            result.put("preRelease", version.getPreRelease());
            result.put("buildMetadata", version.getBuildMetaData());

            return Val.of(result);
        } catch (Exception e) {
            return Val.error("Invalid semantic version: " + e.getMessage());
        }
    }

    @Function(docs = """
            ```isValid(TEXT versionString)```: Checks if a string is a valid semantic version.

            Returns true if the string conforms to Semantic Versioning 2.0.0 specification, false
            otherwise. Accepts versions with or without 'v' prefix.

            **Examples:**
            ```sapl
            policy "validate_version"
            permit
            where
              semver.isValid(request.version);
            ```

            ```sapl
            policy "require_valid_version"
            deny
            where
              !semver.isValid(resource.serviceVersion);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isValid(@Text Val versionString) {
        try {
            Version.parseVersion(stripVPrefix(versionString.getText()));
            return Val.TRUE;
        } catch (Exception e) {
            return Val.FALSE;
        }
    }

    @Function(docs = """
            ```compare(TEXT version1, TEXT version2)```: Compares two semantic versions.

            Returns -1 if version1 < version2, 0 if equal, 1 if version1 > version2. Build metadata
            is ignored in comparison per semantic versioning specification.

            **Examples:**
            ```sapl
            policy "version_comparison"
            permit
            where
              semver.compare(resource.version, "2.0.0") >= 0;
            ```

            ```sapl
            policy "version_order"
            permit
            where
              var result = semver.compare(subject.clientVersion, resource.minVersion);
              result >= 0;
            ```
            """, schema = RETURNS_NUMBER)
    public static Val compare(@Text Val version1, @Text Val version2) {
        try {
            val v1 = parseVersion(version1);
            val v2 = parseVersion(version2);
            return Val.of(v1.compareTo(v2));
        } catch (Exception e) {
            return Val.error("Invalid version in comparison: " + e.getMessage());
        }
    }

    @Function(docs = """
            ```equals(TEXT version1, TEXT version2)```: Checks if two versions are equal.

            Returns true if both versions are semantically equal. Build metadata is ignored in
            comparison.

            **Examples:**
            ```sapl
            policy "exact_version"
            permit
            where
              semver.equals(resource.apiVersion, "2.1.0");
            ```

            ```sapl
            policy "version_match"
            permit
            where
              semver.equals(subject.requiredVersion, resource.providedVersion);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val equals(@Text Val version1, @Text Val version2) {
        try {
            val v1 = parseVersion(version1);
            val v2 = parseVersion(version2);
            return Val.of(v1.equals(v2));
        } catch (Exception e) {
            return Val.error("Invalid version in comparison: " + e.getMessage());
        }
    }

    @Function(docs = """
            ```isLower(TEXT version1, TEXT version2)```: Checks if version1 is lower than version2.

            Returns true if version1 < version2 according to semantic versioning precedence rules.

            **Examples:**
            ```sapl
            policy "require_upgrade"
            deny
            where
              semver.isLower(subject.clientVersion, "2.0.0");
            ```

            ```sapl
            policy "old_version_warning"
            permit
            where
              semver.isLower(request.version, resource.recommendedVersion);
            obligation
              "warning" : "Please upgrade to the recommended version";
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isLower(@Text Val version1, @Text Val version2) {
        try {
            val v1 = parseVersion(version1);
            val v2 = parseVersion(version2);
            return Val.of(v1.compareTo(v2) < 0);
        } catch (Exception e) {
            return Val.error("Invalid version in comparison: " + e.getMessage());
        }
    }

    @Function(docs = """
            ```isHigher(TEXT version1, TEXT version2)```: Checks if version1 is higher than version2.

            Returns true if version1 > version2 according to semantic versioning precedence rules.

            **Examples:**
            ```sapl
            policy "new_version_access"
            permit
            where
              semver.isHigher(subject.clientVersion, "3.0.0");
            ```

            ```sapl
            policy "preview_features"
            permit
            where
              semver.isHigher(request.version, resource.stableVersion);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isHigher(@Text Val version1, @Text Val version2) {
        try {
            val v1 = parseVersion(version1);
            val v2 = parseVersion(version2);
            return Val.of(v1.compareTo(v2) > 0);
        } catch (Exception e) {
            return Val.error("Invalid version in comparison: " + e.getMessage());
        }
    }

    @Function(docs = """
            ```isLowerOrEqual(TEXT version1, TEXT version2)```: Checks if version1 is lower than or equal to version2.

            Returns true if version1 <= version2 according to semantic versioning precedence rules.

            **Examples:**
            ```sapl
            policy "max_version"
            permit
            where
              semver.isLowerOrEqual(resource.apiVersion, "3.5.0");
            ```

            ```sapl
            policy "version_cap"
            permit
            where
              semver.isLowerOrEqual(subject.version, resource.maximumVersion);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isLowerOrEqual(@Text Val version1, @Text Val version2) {
        try {
            val v1 = parseVersion(version1);
            val v2 = parseVersion(version2);
            return Val.of(v1.compareTo(v2) <= 0);
        } catch (Exception e) {
            return Val.error("Invalid version in comparison: " + e.getMessage());
        }
    }

    @Function(docs = """
            ```isHigherOrEqual(TEXT version1, TEXT version2)```: Checks if version1 is higher than or equal to version2.

            Returns true if version1 >= version2 according to semantic versioning precedence rules.

            **Examples:**
            ```sapl
            policy "minimum_version"
            permit
            where
              semver.isHigherOrEqual(subject.clientVersion, "2.0.0");
            ```

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
            val v1 = parseVersion(version1);
            val v2 = parseVersion(version2);
            return Val.of(v1.compareTo(v2) >= 0);
        } catch (Exception e) {
            return Val.error("Invalid version in comparison: " + e.getMessage());
        }
    }

    @Function(docs = """
            ```haveSameMajor(TEXT version1, TEXT version2)```: Checks if two versions have the same major version.

            Returns true if both versions share the same major version number, regardless of minor,
            patch, or pre-release identifiers.

            **Examples:**
            ```sapl
            policy "major_version_compatible"
            permit
            where
              semver.haveSameMajor(resource.apiVersion, "2.0.0");
            ```

            ```sapl
            policy "api_v2"
            permit
            where
              semver.haveSameMajor(request.apiVersion, subject.supportedApiVersion);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val haveSameMajor(@Text Val version1, @Text Val version2) {
        try {
            val v1 = parseVersion(version1);
            val v2 = parseVersion(version2);
            return Val.of(v1.getMajor() == v2.getMajor());
        } catch (Exception e) {
            return Val.error("Invalid version in comparison: " + e.getMessage());
        }
    }

    @Function(docs = """
            ```haveSameMinor(TEXT version1, TEXT version2)```: Checks if two versions have the same major and minor version.

            Returns true if both versions share the same major and minor version numbers, regardless
            of patch or pre-release identifiers.

            **Examples:**
            ```sapl
            policy "minor_version_match"
            permit
            where
              semver.haveSameMinor(resource.version, "2.3.0");
            ```

            ```sapl
            policy "compatible_minor"
            permit
            where
              semver.haveSameMinor(subject.clientVersion, resource.serverVersion);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val haveSameMinor(@Text Val version1, @Text Val version2) {
        try {
            val v1 = parseVersion(version1);
            val v2 = parseVersion(version2);
            return Val.of(v1.getMajor() == v2.getMajor() && v1.getMinor() == v2.getMinor());
        } catch (Exception e) {
            return Val.error("Invalid version in comparison: " + e.getMessage());
        }
    }

    @Function(docs = """
            ```haveSamePatch(TEXT version1, TEXT version2)```: Checks if two versions have the same major, minor, and patch version.

            Returns true if both versions share the same major, minor, and patch numbers, regardless
            of pre-release or build metadata.

            **Examples:**
            ```sapl
            policy "exact_patch_match"
            permit
            where
              semver.haveSamePatch(resource.version, "2.3.5");
            ```

            ```sapl
            policy "same_release"
            permit
            where
              semver.haveSamePatch(subject.version, "1.0.0");
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val haveSamePatch(@Text Val version1, @Text Val version2) {
        try {
            val v1 = parseVersion(version1);
            val v2 = parseVersion(version2);
            return Val.of(
                    v1.getMajor() == v2.getMajor() && v1.getMinor() == v2.getMinor() && v1.getPatch() == v2.getPatch());
        } catch (Exception e) {
            return Val.error("Invalid version in comparison: " + e.getMessage());
        }
    }

    @Function(docs = """
            ```isCompatibleWith(TEXT version1, TEXT version2)```: Checks if version1 is compatible with version2.

            Returns true if versions are API-compatible according to semantic versioning principles:
            - For major version 0 (0.y.z): Only exact major.minor match is compatible
            - For major version 1+: Same major version indicates compatibility

            **Examples:**
            ```sapl
            policy "api_compatibility"
            permit
            where
              semver.isCompatibleWith(subject.clientVersion, resource.apiVersion);
            ```

            ```sapl
            policy "compatible_versions"
            permit
            where
              semver.isCompatibleWith("2.5.0", "2.0.0");
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isCompatibleWith(@Text Val version1, @Text Val version2) {
        try {
            val v1 = parseVersion(version1);
            val v2 = parseVersion(version2);

            if (v2.getMajor() == 0) {
                return Val.of(v1.getMajor() == v2.getMajor() && v1.getMinor() == v2.getMinor());
            }

            return Val.of(v1.getMajor() == v2.getMajor());
        } catch (Exception e) {
            return Val.error("Invalid version in comparison: " + e.getMessage());
        }
    }

    @Function(docs = """
            ```isAtLeast(TEXT version, TEXT minimum)```: Checks if version meets or exceeds minimum version.

            Returns true if version >= minimum. Useful for enforcing minimum version requirements.

            **Examples:**
            ```sapl
            policy "minimum_client"
            permit
            where
              semver.isAtLeast(subject.clientVersion, "2.0.0");
            ```

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
            ```isAtMost(TEXT version, TEXT maximum)```: Checks if version is at or below maximum version.

            Returns true if version <= maximum. Useful for enforcing maximum version constraints.

            **Examples:**
            ```sapl
            policy "maximum_version"
            permit
            where
              semver.isAtMost(resource.version, "3.0.0");
            ```

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
            ```isBetween(TEXT version, TEXT minimum, TEXT maximum)```: Checks if version is within range.

            Returns true if minimum <= version <= maximum. Both bounds are inclusive.

            **Examples:**
            ```sapl
            policy "supported_versions"
            permit
            where
              semver.isBetween(subject.clientVersion, "2.0.0", "3.0.0");
            ```

            ```sapl
            policy "compatibility_window"
            permit
            where
              semver.isBetween(request.version, resource.minVersion, resource.maxVersion);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isBetween(@Text Val version, @Text Val minimum, @Text Val maximum) {
        try {
            val v   = parseVersion(version);
            val min = parseVersion(minimum);
            val max = parseVersion(maximum);

            return Val.of(v.compareTo(min) >= 0 && v.compareTo(max) <= 0);
        } catch (Exception e) {
            return Val.error("Invalid version in comparison: " + e.getMessage());
        }
    }

    @Function(docs = """
            ```isPreRelease(TEXT version)```: Checks if version is a pre-release.

            Returns true if the version contains a pre-release identifier (e.g., alpha, beta, rc).

            **Examples:**
            ```sapl
            policy "stable_only"
            deny
            where
              semver.isPreRelease(resource.version);
            ```

            ```sapl
            policy "beta_testers"
            permit
            where
              semver.isPreRelease(resource.apiVersion);
              subject.role == "beta-tester";
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isPreRelease(@Text Val version) {
        try {
            val v = parseVersion(version);
            return Val.of(!v.getPreRelease().isEmpty());
        } catch (Exception e) {
            return Val.error("Invalid version: " + e.getMessage());
        }
    }

    @Function(docs = """
            ```isStable(TEXT version)```: Checks if version is a stable release.

            Returns true if the version does not contain a pre-release identifier.

            **Examples:**
            ```sapl
            policy "production_ready"
            permit
            where
              semver.isStable(resource.version);
            ```

            ```sapl
            policy "stable_api"
            permit
            where
              semver.isStable(resource.apiVersion);
              action.name == "production";
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isStable(@Text Val version) {
        try {
            val v = parseVersion(version);
            return Val.of(v.getPreRelease().isEmpty());
        } catch (Exception e) {
            return Val.error("Invalid version: " + e.getMessage());
        }
    }

    @Function(docs = """
            ```getMajor(TEXT version)```: Extracts the major version number.

            Returns the major version component as an integer.

            **Examples:**
            ```sapl
            policy "major_version_check"
            permit
            where
              semver.getMajor(resource.version) >= 2;
            ```

            ```sapl
            policy "api_v3"
            permit
            where
              semver.getMajor(subject.apiVersion) == 3;
            ```
            """, schema = RETURNS_NUMBER)
    public static Val getMajor(@Text Val version) {
        try {
            val v = parseVersion(version);
            return Val.of(v.getMajor());
        } catch (Exception e) {
            return Val.error("Invalid version: " + e.getMessage());
        }
    }

    @Function(docs = """
            ```getMinor(TEXT version)```: Extracts the minor version number.

            Returns the minor version component as an integer.

            **Examples:**
            ```sapl
            policy "minor_version_check"
            permit
            where
              semver.getMinor(resource.version) >= 5;
            ```

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
            val v = parseVersion(version);
            return Val.of(v.getMinor());
        } catch (Exception e) {
            return Val.error("Invalid version: " + e.getMessage());
        }
    }

    @Function(docs = """
            ```getPatch(TEXT version)```: Extracts the patch version number.

            Returns the patch version component as an integer.

            **Examples:**
            ```sapl
            policy "patch_level"
            permit
            where
              semver.getPatch(resource.version) >= 10;
            ```

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
            val v = parseVersion(version);
            return Val.of(v.getPatch());
        } catch (Exception e) {
            return Val.error("Invalid version: " + e.getMessage());
        }
    }

    /**
     * Parses version string and strips optional v-prefix.
     */
    private static Version parseVersion(Val versionVal) {
        return Version.parseVersion(stripVPrefix(versionVal.getText()));
    }

    /**
     * Removes leading 'v' or 'V' from version string if present.
     */
    private static String stripVPrefix(String version) {
        if (version != null && !version.isEmpty() && (version.charAt(0) == 'v' || version.charAt(0) == 'V')) {
            return version.substring(1);
        }
        return version;
    }
}
