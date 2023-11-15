package io.sapl.test.dsl.utils;

import io.sapl.test.utils.ClasspathHelper;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.apache.commons.io.FileUtils;
import org.assertj.core.util.Arrays;

@UtilityClass
public class TestDiscoveryHelper {

    private static final String[] SAPLTEST_FILE_EXTENSIONS = Arrays.array("sapltest");

    public static List<String> discoverTests() {
        var dir = ClasspathHelper.findPathOnClasspath(TestDiscoveryHelper.class.getClassLoader(), "").toFile();

        return FileUtils
                .listFiles(dir, SAPLTEST_FILE_EXTENSIONS, true)
                .stream()
                .map(file -> dir.toPath().relativize(file.toPath()).toString())
                .toList();
    }
}
