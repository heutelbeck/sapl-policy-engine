package io.sapl.test.utils;

import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.sapl.test.SaplTestException;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import reactor.core.Exceptions;

@UtilityClass
public class ClasspathHelper {

	private static final String DEFAULT_PATH = "policies/";
	private static final String ERROR_MAIN_MESSAGE = "Error finding the specified resource on the classpath on paths %s and %s";
	
	public Path findPathOnClasspath(@NonNull Class<?> clazz, @NonNull String path) {

		//try path as specified
		URL url = clazz.getClassLoader().getResource(path);
		if(url != null) {
			return getResourcePath(url);
		}
		
		//try DEFAULT_PATH + specified path
		String defaultPath = DEFAULT_PATH + path;
		URL urlFromDefaultPath = clazz.getClassLoader().getResource(defaultPath);
		if(urlFromDefaultPath != null) {
			return getResourcePath(urlFromDefaultPath);
		}

		//nothing found -> throw useful exception
		StringBuilder errorMessage = new StringBuilder(String.format(ERROR_MAIN_MESSAGE, path, defaultPath));
		ClassLoader loader = clazz.getClassLoader();
		if(loader instanceof URLClassLoader) {
			errorMessage.append(System.lineSeparator() + System.lineSeparator() + "We tried the following paths: " + System.lineSeparator());
			URL[] classpathElements = ((URLClassLoader) loader).getURLs();
			for(URL classpathElement : classpathElements) {
				errorMessage.append("  - " + classpathElement.toString());
			}
		}
		throw new SaplTestException(errorMessage.toString());
		
	}
	
	private static Path getResourcePath(URL url) {
		if ("jar".equals(url.getProtocol())) {
			throw new SaplTestException("Not supporting reading PDP configuration from jar during test execution");
		}
		
		Path configDirectoryPath;
		try {
			configDirectoryPath = Paths.get(url.toURI());
		} catch (URISyntaxException e) {
			throw Exceptions.propagate(e);
		}
		
		return configDirectoryPath;
	}
}
