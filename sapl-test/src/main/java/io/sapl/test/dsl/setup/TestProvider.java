package io.sapl.test.dsl.setup;

import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;

import io.sapl.test.utils.ClasspathHelper;
import java.io.File;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.assertj.core.util.Arrays;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.TestFactory;

public interface TestProvider {
    @TestFactory
    default List<DynamicContainer> testProvider() {
        final var testPaths = discoverTests();

        return testPaths.stream().map(path -> dynamicContainer(path, TestBuilder.buildTests(path))).toList();
    }

    default List<String> discoverTests() {
        var dir = ClasspathHelper.findPathOnClasspath(getClass().getClassLoader(), "").toFile();

        return FileUtils
                .listFiles(dir, Arrays.array("sapltest"), true)
                .stream()
                .map(file -> file.getAbsolutePath().replace(dir.getAbsolutePath() + File.separatorChar, ""))
                .toList();
    }
}
