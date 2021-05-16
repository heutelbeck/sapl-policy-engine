package io.sapl.util;

import java.io.BufferedInputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;

import lombok.Cleanup;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

@UtilityClass
public class JarUtility {

	public URL inferUrlOfRecourcesPath(Class<?> clazz, String path) {
		var url = clazz.getResource(path);
		if (url == null)
			throw new RuntimeException(
					"Folder in application resources is either empty or not present at all. Path:" + path);
		return url;
	}

	public String getJarFilePath(URL url) {
		return url.toString().split("!")[0].substring("jar:file:".length());
	}

	public String getPathWithinJar(URL url) {
		var jarPathElements = url.toString().split("!");
		var pathWithinJar = new StringBuilder();
		for (int i = 1; i < jarPathElements.length; i++) {
			pathWithinJar.append(jarPathElements[i]);
		}
		if (pathWithinJar.charAt(0) == '/') {
			pathWithinJar.deleteCharAt(0);
		}
		return pathWithinJar.toString();
	}

	@SneakyThrows
	public String readStringFromZipEntry(ZipFile jarFile, ZipEntry entry) {
		@Cleanup
		var bis = new BufferedInputStream(jarFile.getInputStream(entry));
		return IOUtils.toString(bis, StandardCharsets.UTF_8);
	}
}
