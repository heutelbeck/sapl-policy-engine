package io.sapl.mavenplugin.test.coverage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.plugin.logging.Log;

public class PathHelper {
	static Path resolveBaseDir(String configBaseDir, String projectBuildDir, Log log) {
		if (configBaseDir != null && !configBaseDir.isEmpty()) {
			// apply configured basedir if it is set
			log.debug(String.format("Using \"%s\" as base dir for sapl coverage", configBaseDir));
			return Paths.get(configBaseDir).resolve("sapl-coverage");
		} else {
			// if not use the maven project build output dir
			log.debug(String.format("Using \"%s\" as base dir for sapl coverage", projectBuildDir));
			return Paths.get(projectBuildDir).resolve("sapl-coverage");
		}
	}

	public static void createFile(Path filePath) throws IOException {
		if (!Files.exists(filePath)) {
			Path parent = filePath.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Files.createFile(filePath);
		}

	}

	public static void creatParentDirs(Path filePath) throws IOException {
		if (!Files.exists(filePath)) {
			Path parent = filePath.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
		}
	}
}
