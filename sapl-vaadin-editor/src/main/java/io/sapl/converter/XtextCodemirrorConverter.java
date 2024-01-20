/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.converter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.regex.Pattern;

import lombok.experimental.UtilityClass;

/**
 * Converts XTextCodemirror to ESM
 */
@UtilityClass
public class XtextCodemirrorConverter {

    /**
     * @param originalCode the original code
     * @return code in ESM
     */
    public String convertToESM(String originalCode) {
        var importPattern = "define\\((['\"])([^'\"]+)\\1,\\s*\\[([^\\]]*)\\]\\s*,\\s*function\\s*\\(([^)]*)\\)\\s*\\{";
        var exportPattern = "return\\s(?:[A-Z][a-z]+)+;\\s}\\);|return\\s(?:exports|\\{\\});\\s}\\);\n";

        var imports            = extractImports(importPattern, originalCode);
        var codeWithoutImports = originalCode.replaceAll(importPattern, "");

        codeWithoutImports = codeWithoutImports.replace("CodeMirrorEditorContext", "EditorContext");

        var exports   = extractExports(codeWithoutImports);
        var functions = codeWithoutImports.replaceAll(exportPattern, "");

        return imports + functions + exports;
    }

    private String extractImports(String regex, String code) {
        var pattern = Pattern.compile(regex);
        var matcher = pattern.matcher(code);

        var uniqueImports = new HashSet<String>();

        while (matcher.find()) {
            var dependencies = matcher.group(3).replaceAll("\\s+", "");
            var args         = matcher.group(4).replaceAll("\\s+\\{", "");
            var modulePaths  = dependencies.split(",");
            var modules      = args.split(",");

            for (int i = 0; i < modulePaths.length; i++) {
                modulePaths[i] = modulePaths[i].replaceAll("\\s", "");
                String importEntry;
                if (!"'codemirror/mode/javascript/javascript'".equals(modulePaths[i])) {
                    importEntry = "import " + modules[i].replaceAll("\\s", "") + " from " + modulePaths[i] + ";";
                } else {
                    importEntry = "import " + modulePaths[i] + ";";
                }
                uniqueImports.add(importEntry);
            }
        }

        var uniqueImportsBuilder = new StringBuilder();
        for (var importEntry : uniqueImports) {
            if (!importEntry.contains("xtext") && !importEntry.contains("  ")) {
                uniqueImportsBuilder.append(importEntry).append('\n');
            }
        }

        return uniqueImportsBuilder.toString();
    }

    private String extractExports(String code) {
        var exports = new ArrayList<String>();
        var pattern = Pattern.compile("return\\s+([a-zA-Z]+);\\s+}\\);");
        var matcher = pattern.matcher(code);

        while (matcher.find()) {
            exports.add(matcher.group(1));
        }

        return "export { " + String.join(", ", exports) + " };";
    }

}
