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
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueMetadata;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.io.IOException;
import java.util.Properties;

/**
 * Collection of SAPL system information functions.
 */
@UtilityClass
@FunctionLibrary(name = SaplFunctionLibrary.NAME, description = SaplFunctionLibrary.DESCRIPTION, libraryDocumentation = SaplFunctionLibrary.DOCUMENTATION)
public class SaplFunctionLibrary {

    public static final String NAME        = "sapl";
    public static final String DESCRIPTION = "SAPL system information functions.";

    public static final String DOCUMENTATION = """
            Runtime environment introspection for authorization policies. Query application version,
            JDK details, and operating system information to make platform-aware access control decisions.

            The library provides a single function that returns system metadata cached at class initialization.
            Use this to enforce runtime requirements, restrict operations to specific platforms, or capture
            environment context in audit trails.
            """;

    private static final String UNKNOWN = "unknown";

    private static final String VERSION_PROPERTIES_PATH = "/saplversion.properties";
    private static final String GIT_PROPERTIES_PATH     = "/git.properties";

    private static final String PROPERTY_GIT_COMMIT_ID  = "gitCommitId";
    private static final String PROPERTY_GIT_BRANCH     = "gitBranch";
    private static final String PROPERTY_GIT_BUILD_TIME = "gitBuildTime";
    private static final String PROPERTY_JDK_VERSION    = "jdkVersion";
    private static final String PROPERTY_JAVA_VERSION   = "javaVersion";
    private static final String PROPERTY_JAVA_VENDOR    = "javaVendor";
    private static final String PROPERTY_OS_NAME        = "osName";
    private static final String PROPERTY_OS_VERSION     = "osVersion";
    private static final String PROPERTY_OS_ARCH        = "osArch";
    private static final String PROPERTY_SAPL_VERSION   = "saplVersion";

    private static final String GIT_COMMIT_ID_ABBREV = "git.commit.id.abbrev";
    private static final String GIT_BRANCH           = "git.branch";
    private static final String GIT_BUILD_TIME       = "git.build.time";

    private static final String JAVA_SPECIFICATION_VERSION = "java.specification.version";
    private static final String JAVA_VERSION               = "java.version";
    private static final String JAVA_VENDOR                = "java.vendor";

    private static final String OS_NAME    = "os.name";
    private static final String OS_VERSION = "os.version";
    private static final String OS_ARCH    = "os.arch";

    private static final String RETURNS_OBJECT = """
            {
                "type": "object"
            }
            """;

    private static final Properties VERSION_PROPERTIES = loadProperties(VERSION_PROPERTIES_PATH);
    private static final Properties GIT_PROPERTIES     = loadProperties(GIT_PROPERTIES_PATH);

    @Function(docs = """
            ```info()```: Returns system information including application version, git details, JDK/JRE, and operating system information.

            The returned object contains the following properties:
            - ```saplVersion```: Version of the SAPL Engine
            - ```gitCommitId```: Abbreviated git commit hash
            - ```gitBranch```: Git branch name
            - ```gitBuildTime```: Build timestamp
            - ```jdkVersion```: JDK version used for compilation
            - ```javaVersion```: Current JRE version
            - ```javaVendor```: Java vendor name
            - ```osName```: Operating system name
            - ```osVersion```: Operating system version
            - ```osArch```: Operating system architecture

            If properties cannot be loaded from the classpath, fields will contain "unknown" as a fallback value.

            Use this function to validate system requirements, log runtime environment details for audit trails, or conditionally enable features based on platform capabilities.

            **Example - Enforce Minimum JDK Version:**
            ```sapl
            policy "require_jdk21"
            permit action == "system:deploy"
            where
              var info = sapl.info();
              info.jdkVersion >= "21";
            ```

            **Example - Platform-Specific Access Control:**
            ```sapl
            policy "linux_only_operations"
            permit action == "admin:configure-network"
            where
              var info = sapl.info();
              info.osName =~ "Linux";
            ```

            **Example - Audit Logging with Environment Context:**
            ```sapl
            policy "audit_with_environment"
            permit action == "data:access"
            obligation
              {
                "type": "log-access",
                "environment": sapl.info()
              }
            ```
            """, schema = RETURNS_OBJECT)
    public static Value info() {
        val builder = ObjectValue.builder();

        builder.put(PROPERTY_SAPL_VERSION, saplVersion());

        builder.put(PROPERTY_GIT_COMMIT_ID, gitProperty(GIT_COMMIT_ID_ABBREV));
        builder.put(PROPERTY_GIT_BRANCH, gitProperty(GIT_BRANCH));
        builder.put(PROPERTY_GIT_BUILD_TIME, gitProperty(GIT_BUILD_TIME));

        builder.put(PROPERTY_JDK_VERSION, systemProperty(JAVA_SPECIFICATION_VERSION));
        builder.put(PROPERTY_JAVA_VERSION, systemProperty(JAVA_VERSION));
        builder.put(PROPERTY_JAVA_VENDOR, systemProperty(JAVA_VENDOR));

        builder.put(PROPERTY_OS_NAME, systemProperty(OS_NAME));
        builder.put(PROPERTY_OS_VERSION, systemProperty(OS_VERSION));
        builder.put(PROPERTY_OS_ARCH, systemProperty(OS_ARCH));

        return builder.build();
    }

    /**
     * Loads a properties file from the classpath.
     *
     * @param path
     * the classpath path to the properties file
     *
     * @return loaded Properties object, or empty Properties if file not found
     */
    private static Properties loadProperties(String path) {
        val properties = new Properties();
        try (val inputStream = SaplFunctionLibrary.class.getResourceAsStream(path)) {
            if (inputStream != null) {
                properties.load(inputStream);
            }
        } catch (IOException exception) {
            // Properties remain empty if loading fails
        }
        return properties;
    }

    /**
     * Retrieves the SAPL version from version properties.
     *
     * @return TextValue containing the version string, or "unknown" if not
     * available
     */
    private static TextValue saplVersion() {
        return new TextValue(VERSION_PROPERTIES.getProperty(PROPERTY_SAPL_VERSION, UNKNOWN), ValueMetadata.EMPTY);
    }

    /**
     * Retrieves a property from git properties.
     *
     * @param key
     * the property key
     *
     * @return TextValue containing the property value, or "unknown" if not
     * available
     */
    private static TextValue gitProperty(String key) {
        return new TextValue(SaplFunctionLibrary.GIT_PROPERTIES.getProperty(key, UNKNOWN), ValueMetadata.EMPTY);
    }

    /**
     * Retrieves a system property.
     *
     * @param key
     * the system property key
     *
     * @return TextValue containing the property value, or "unknown" if not
     * available
     */
    private static TextValue systemProperty(String key) {
        return new TextValue(System.getProperty(key, UNKNOWN), ValueMetadata.EMPTY);
    }
}
