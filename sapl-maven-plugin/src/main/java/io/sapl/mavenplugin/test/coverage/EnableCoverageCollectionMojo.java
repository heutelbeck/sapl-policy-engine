package io.sapl.mavenplugin.test.coverage;

import java.io.File;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "enable-coverage-collection", defaultPhase = LifecyclePhase.PROCESS_TEST_CLASSES)
public class EnableCoverageCollectionMojo extends AbstractMojo {

	@Parameter(defaultValue = "true")
	private boolean coverageEnabled;

	@Parameter(defaultValue = "")
	private String outputDir;

	@Parameter(defaultValue = "${project.build.directory}")
	private String projectBuildDir;

	@Override
	public void execute() throws MojoExecutionException {
		if (this.coverageEnabled) {
			deleteDirectory(PathHelper.resolveBaseDir(outputDir, projectBuildDir, getLog()).toFile());
		}
	}
	
	private boolean deleteDirectory(File directoryToBeDeleted) {
	    File[] allContents = directoryToBeDeleted.listFiles();
	    if (allContents != null) {
	        for (File file : allContents) {
	            deleteDirectory(file);
	        }
	    }
	    return directoryToBeDeleted.delete();
	}
}
