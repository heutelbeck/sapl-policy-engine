package io.sapl.generator.web;

import org.eclipse.xtext.xtext.generator.model.IXtextGeneratorFileSystemAccess;
import org.eclipse.xtext.xtext.generator.model.XtextGeneratorFileSystemAccess;
import org.eclipse.xtext.xtext.generator.model.project.WebProjectConfig;

public class TestWebProjectConfig extends WebProjectConfig {

	@Override
	public IXtextGeneratorFileSystemAccess getAssets() {
		return new XtextGeneratorFileSystemAccess("", false);
	}
}
