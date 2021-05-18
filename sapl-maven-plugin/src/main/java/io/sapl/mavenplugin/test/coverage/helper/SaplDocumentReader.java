package io.sapl.mavenplugin.test.coverage.helper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.mavenplugin.test.coverage.SaplTestException;
import io.sapl.mavenplugin.test.coverage.model.SaplDocument;
import reactor.core.Exceptions;

public class SaplDocumentReader {
	private static final String ERROR_PATH_NOT_FOUND = "Unable to find the directory \"%s\" on the classpath";
	private static final String ERROR_PATH_WAS_FILE = "Unable to find policies at \"%s\" on the classpath. The path points to a file.";

	private MavenProject project;
	private Log log;
	
	public SaplDocumentReader(Log log, MavenProject project) {
		this.log = log;
		this.project = project;
	}
	
	public Collection<SaplDocument> retrievePolicyDocuments(String policyPath) {
		DefaultSAPLInterpreter interpreter = new DefaultSAPLInterpreter();

		File policyDir = findPolicyDirOnClasspath(policyPath);

		log.debug("Looking for policies in directory \"" + policyDir.getAbsolutePath() + "\"");

		File[] files = policyDir.listFiles();
		if (files == null) {
			throw new SaplTestException(String.format(ERROR_PATH_WAS_FILE, policyDir.getAbsolutePath()));
		}

		List<SaplDocument> saplDocuments = new LinkedList<>();
		for (File file : files) {
			if (file.isFile() && file.getName().endsWith(".sapl")) {
				String fileContent = null;
				try {
					log.debug(String.format("Loading coverage target from file \"%s\"", file.getPath()));
					fileContent = Files.readString(file.toPath());
					int linecount = Files.readAllLines(file.toPath()).size();
					saplDocuments.add(new SaplDocument(file.toPath(), linecount, interpreter.parse(fileContent)));
				} catch (IOException e) {
					log.error("Error reading file " + file.toString(), e);
				}
			}
		}
		return saplDocuments;
	}
	

	private File findPolicyDirOnClasspath(String policyPath) {

		List<String> projectTestClassPathElements;
		StringBuilder builder = new StringBuilder(String.format(ERROR_PATH_NOT_FOUND, policyPath));
		File result = null;
		try {
			projectTestClassPathElements = project.getRuntimeClasspathElements();
			builder.append(" - We looked at: " + System.lineSeparator());

			for (String element : projectTestClassPathElements) {
				Path path = Path.of(element).resolve(policyPath);
				builder.append("* " + path + System.lineSeparator());
				File dir = path.toFile();
				if (dir.exists()) {
					result = dir;
					break;
				}
			}
		} catch (DependencyResolutionRequiredException e) {
			throw Exceptions.propagate(e);
		}
		
		if(result != null) {
			return result;
		} else {
			throw new SaplTestException(builder.toString());
		}
	}
}
