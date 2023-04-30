package io.sapl.converter;

import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
public class SaplModeConverter {

    public String convertToESM(String amdCode) {
        final String namedDefineRegex = "define\\((['\"])([^'\"]+)\\1,\\s*\\[([^\\]]*)\\]\\s*,\\s*function\\s*\\(([^)]*)\\)\\s*\\{";
        final Pattern pattern = Pattern.compile(namedDefineRegex);
        final Matcher matcher = pattern.matcher(amdCode);
        final List<String> importStatements = new ArrayList<>();
        final StringBuilder esmCode = new StringBuilder();

        while (matcher.find()) {
            final String dependencies = matcher.group(3).replaceAll("\\s+", "");
            final String args = matcher.group(4).replaceAll("\\s+\\{", "");
            final String[] dependencyArray = dependencies.split(",");
            final String[] argArray = args.split(",");
            final StringBuilder importBuilder = new StringBuilder();

            for (int i = 0; i < dependencyArray.length; i++) {
                final String dependency = dependencyArray[i];
                final String arg = argArray[i];
                importBuilder.append("import ").append(arg.trim()).append(" from ").append(dependency.trim()).append(";\n");
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
