package io.sapl.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class JarPathUtil {

    public String getJarFilePath(String[] jarPathElements) {
        return jarPathElements[0].substring("jar:file:".length());
    }
}
