package io.sapl.converter;

import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
public class XtextCodemirrorConverter {

    public String convertToESM(String originalCode) {
        String importPattern = "define\\((['\"])([^'\"]+)\\1,\\s*\\[([^\\]]*)\\]\\s*,\\s*function\\s*\\(([^)]*)\\)\\s*\\{";
        String exportPattern = "return\\s(?:[A-Z][a-z]+)+;\\s}\\);|return\\s(?:exports|\\{\\});\\s}\\);\n";

        String imports = extractImports(importPattern, originalCode);
        String codeWithoutImports = originalCode.replaceAll(importPattern, "");

        codeWithoutImports = codeWithoutImports.replaceAll("CodeMirrorEditorContext", "EditorContext");

        String exports = extractExports(codeWithoutImports);
        String functions = codeWithoutImports.replaceAll(exportPattern, "");

        return imports + functions + exports;
    }

    private String extractImports(String regex, String code) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(code);

        Set<String> uniqueImports = new HashSet<>();

        while (matcher.find()) {
            String dependencies = matcher.group(3).replaceAll("\\s+", "");
            String args = matcher.group(4).replaceAll("\\s+\\{", "");
            String[] modulePaths = dependencies.split(",");
            String[] modules = args.split(",");

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

        StringBuilder uniqueImportsBuilder = new StringBuilder();
        for (String importEntry : uniqueImports) {
            if (!importEntry.contains("xtext") && !importEntry.contains("  ")) {
                uniqueImportsBuilder.append(importEntry).append("\n");
            }
        }

        return uniqueImportsBuilder.toString();
    }


    private String extractExports(String code){
        List<String> exports = new ArrayList<>();
        Pattern pattern = Pattern.compile("return\\s+([a-zA-Z]+);\\s+}\\);");
        Matcher matcher = pattern.matcher(code);

        while (matcher.find()) {
            exports.add(matcher.group(1));
        }

        String exportString = "export { " + String.join(", ", exports) + " };";
        return exportString;
    }

}