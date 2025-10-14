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

import com.fasterxml.jackson.databind.node.TextNode;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.io.IOException;
import java.util.Properties;

/**
 * Collection of SAPL system information functions.
 */
@UtilityClass
@FunctionLibrary(name = SaplFunctionLibrary.NAME, description = SaplFunctionLibrary.DESCRIPTION)
public class SaplFunctionLibrary {

    public static final String NAME        = "sapl";
    public static final String DESCRIPTION = "SAPL system information functions.";

    private static final String UNKNOWN = "unknown";

    private static final String VERSION_PROPERTIES_PATH = "/saplversion.properties";
    private static final String GIT_PROPERTIES_PATH     = "/git.properties";

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

            **Example:**
            ```sapl
            policy "example"
            permit
            where
              var systemInfo = sapl.info();
              systemInfo.version == "3.0.0";
            ```
            """, schema = RETURNS_OBJECT)
    public static Val info() {
        val versionProperties = loadProperties(VERSION_PROPERTIES_PATH);
        val gitProperties     = loadProperties(GIT_PROPERTIES_PATH);

        val infoObject = Val.JSON.objectNode();

        infoObject.set(PROPERTY_VERSION, textNode(versionProperties.getProperty(PROPERTY_VERSION, UNKNOWN)));

        infoObject.set("gitCommitId", textNode(gitProperties, GIT_COMMIT_ID_ABBREV));
        infoObject.set("gitBranch", textNode(gitProperties, GIT_BRANCH));
        infoObject.set("gitBuildTime", textNode(gitProperties, GIT_BUILD_TIME));

        infoObject.set("jdkVersion", textNode(System.getProperty(JAVA_SPECIFICATION_VERSION, UNKNOWN)));
        infoObject.set("javaVersion", textNode(System.getProperty(JAVA_VERSION, UNKNOWN)));
        infoObject.set("javaVendor", textNode(System.getProperty(JAVA_VENDOR, UNKNOWN)));

        infoObject.set("osName", textNode(System.getProperty(OS_NAME, UNKNOWN)));
        infoObject.set("osVersion", textNode(System.getProperty(OS_VERSION, UNKNOWN)));
        infoObject.set("osArch", textNode(System.getProperty(OS_ARCH, UNKNOWN)));

        return Val.of(infoObject);
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
    private static TextNode textNode(Properties properties, String key) {
        return TextNode.valueOf(properties.getProperty(key, UNKNOWN));
    }

    /**
     * Creates a TextNode from a string value.
     *
     * @param value the string value
     * @return TextNode with the value
     */
    private static TextNode textNode(String value) {
        return TextNode.valueOf(value);
    }
}
