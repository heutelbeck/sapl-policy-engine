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
package io.sapl.node.boot;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.source.ConfigurationProperty;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.PropertySourceOrigin;
import org.springframework.boot.origin.SystemEnvironmentOrigin;
import org.springframework.boot.origin.TextResourceOrigin;
import org.springframework.core.convert.ConversionException;
import org.springframework.core.env.CommandLinePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.Resource;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.val;

/**
 * The insecure modes of the node that run only with an explicit opt-in, each
 * with its key and its command-line option. The warning of each mode names the
 * source through which the opt-in arrived, so that an operator can find and
 * remove it.
 */
@Getter
@RequiredArgsConstructor
public enum OptIn {

    /**
     * Requests without authentication are served.
     */
    NO_AUTH("--no-auth", "io.sapl.node.allow-no-auth"),

    /**
     * An API without TLS is served in plaintext.
     */
    NO_TLS("--no-tls", "io.sapl.node.allow-no-tls");

    /**
     * The name of the property source that holds the opt-ins given as
     * command-line options.
     */
    public static final String COMMAND_LINE_OPTIONS = "saplNodeCommandLineOptions";

    private final String option;
    private final String key;

    /**
     * Finds the opt-in of a command-line option.
     *
     * @param argument an argument of the command line
     * @return the opt-in whose option the argument is, or empty
     */
    public static Optional<OptIn> ofOption(String argument) {
        return Arrays.stream(values()).filter(optIn -> optIn.option.equals(argument)).findFirst();
    }

    /**
     * Tells through which source the mode was allowed. The source with the
     * highest precedence that sets the key decides. A value that is not a
     * boolean allows nothing.
     *
     * @param environment the environment of the node
     * @return the source as the warning of the mode names it, or empty if the
     * mode is not allowed
     */
    public Optional<String> source(Environment environment) {
        val name = ConfigurationPropertyName.of(key);
        for (val source : ConfigurationPropertySources.get(environment)) {
            val property = source.getConfigurationProperty(name);
            if (property != null) {
                return isTrue(property.getValue()) ? Optional.of(describe(property, source.getUnderlyingSource()))
                        : Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static boolean isTrue(Object value) {
        try {
            return Boolean.TRUE.equals(ApplicationConversionService.getSharedInstance().convert(value, Boolean.class));
        } catch (ConversionException e) {
            return false;
        }
    }

    private String describe(ConfigurationProperty property, @Nullable Object underlyingSource) {
        val sourceName = underlyingSource instanceof PropertySource<?> propertySource ? propertySource.getName() : "";
        val spelled    = property.getOrigin() instanceof PropertySourceOrigin propertySourceOrigin
                ? propertySourceOrigin.getPropertyName()
                : key;
        val origin     = innermost(property.getOrigin());
        if (COMMAND_LINE_OPTIONS.equals(sourceName)) {
            return "the command-line option " + option;
        }
        if (origin instanceof SystemEnvironmentOrigin environmentOrigin) {
            return "the environment variable %s=true".formatted(environmentOrigin.getProperty());
        }
        return switch (sourceName) {
        case StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME ->
            "the environment variable %s=true".formatted(spelled);
        case CommandLinePropertySource.COMMAND_LINE_PROPERTY_SOURCE_NAME ->
            "the command-line option --%s=true".formatted(spelled);
        case StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME  ->
            "the system property %s=true".formatted(spelled);
        default                                                          ->
            "%s=true in %s".formatted(key, location(origin, sourceName));
        };
    }

    // A property source adapted by Spring wraps the origin that its own source
    // tracked, such as the line in a file.
    private static @Nullable Origin innermost(@Nullable Origin origin) {
        var current = origin;
        while (current instanceof PropertySourceOrigin wrapper && wrapper.getOrigin() != null) {
            current = wrapper.getOrigin();
        }
        return current;
    }

    private static String location(@Nullable Origin origin, String sourceName) {
        if (origin instanceof TextResourceOrigin textOrigin) {
            val resource = textOrigin.getResource();
            if (resource != null) {
                return fileName(resource);
            }
        }
        return sourceName;
    }

    // A resource in a jar is no file and is named by its description.
    private static String fileName(Resource resource) {
        try {
            return resource.getFile().getPath();
        } catch (IOException e) {
            return resource.getDescription();
        }
    }

}
