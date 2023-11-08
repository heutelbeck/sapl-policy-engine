package io.sapl.test.dsl.setup;

import io.sapl.test.utils.ClasspathHelper;
import java.io.File;
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
                .map(file -> file.getAbsolutePath().replace(dir.getAbsolutePath() + File.separatorChar, ""))
                .toList();
    }
}
