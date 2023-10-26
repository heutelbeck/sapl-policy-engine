/*
 * Streaming Attribute Policy Language (SAPL) Engine
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.converter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.experimental.UtilityClass;

/**
 * Utility to convert JS AMD code to ESM
 */
@UtilityClass
public class SaplModeConverter {

    /**
     * @param amdCode the AMD code
     * @return code as ESM
     */
    public String convertToESM(String amdCode) {
        final String        namedDefineRegex = "define\\((['\"])([^'\"]+)\\1,\\s*\\[([^\\]]*)\\]\\s*,\\s*function\\s*\\(([^)]*)\\)\\s*\\{";
        final Pattern       pattern          = Pattern.compile(namedDefineRegex);
        final Matcher       matcher          = pattern.matcher(amdCode);
        final List<String>  importStatements = new ArrayList<>();
        final StringBuilder esmCode          = new StringBuilder();

        while (matcher.find()) {
            final String        dependencies    = matcher.group(3).replaceAll("\\s+", "");
            final String        args            = matcher.group(4).replaceAll("\\s+\\{", "");
            final String[]      dependencyArray = dependencies.split(",");
            final String[]      argArray        = args.split(",");
            final StringBuilder importBuilder   = new StringBuilder();

            for (int i = 0; i < dependencyArray.length; i++) {
                final String dependency = dependencyArray[i];
                final String arg        = argArray[i];
                importBuilder.append("import ").append(arg.trim()).append(" from ").append(dependency.trim())
                        .append(";\n");
            }

            importStatements.add(importBuilder.toString());
            matcher.appendReplacement(esmCode, "");
        }

        matcher.appendTail(esmCode);
        esmCode.insert(0, String.join("", importStatements));
        esmCode.delete(esmCode.lastIndexOf("});"), esmCode.length());

        return esmCode.toString();
    }

}
