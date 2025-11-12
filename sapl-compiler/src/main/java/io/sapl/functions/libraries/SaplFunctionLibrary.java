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
import lombok.experimental.UtilityClass;
import lombok.val;

import java.io.IOException;
import java.util.Properties;
import java.util.HashMap;

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

    private static final Properties VERSION_PROPERTIES = loadProperties(VERSION_PROPERTIES_PATH);
    private static final Properties GIT_PROPERTIES     = loadProperties(GIT_PROPERTIES_PATH);

    private static final String PROPERTY_VERSION = "saplVersion";

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
        val infoObject = new HashMap<String, Value>();

        infoObject.put(PROPERTY_VERSION, textValue(VERSION_PROPERTIES.getProperty(PROPERTY_VERSION, UNKNOWN)));

        infoObject.put("gitCommitId", textValue(GIT_PROPERTIES, GIT_COMMIT_ID_ABBREV));
        infoObject.put("gitBranch", textValue(GIT_PROPERTIES, GIT_BRANCH));
        infoObject.put("gitBuildTime", textValue(GIT_PROPERTIES, GIT_BUILD_TIME));

        infoObject.put("jdkVersion", textValue(System.getProperty(JAVA_SPECIFICATION_VERSION, UNKNOWN)));
        infoObject.put("javaVersion", textValue(System.getProperty(JAVA_VERSION, UNKNOWN)));
        infoObject.put("javaVendor", textValue(System.getProperty(JAVA_VENDOR, UNKNOWN)));

        infoObject.put("osName", textValue(System.getProperty(OS_NAME, UNKNOWN)));
        infoObject.put("osVersion", textValue(System.getProperty(OS_VERSION, UNKNOWN)));
        infoObject.put("osArch", textValue(System.getProperty(OS_ARCH, UNKNOWN)));

        return new ObjectValue(infoObject, false);
    }

    /**
     * Loads a properties file from the classpath.
     *
     * @param path the classpath path to the properties file
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
     * Creates a TextNode from a property value with default fallback.
     *
     * @param properties the properties object
     * @param key the property key
     * @return TextNode with property value or "unknown"
     */
    private static TextValue textValue(Properties properties, String key) {
        return new TextValue(properties.getProperty(key, UNKNOWN), false);
    }

    /**
     * Creates a TextNode from a string value.
     *
     * @param value the string value
     * @return TextNode with the value
     */
    private static TextValue textValue(String value) {
        return new TextValue(value, false);
    }
}
