package io.sapl.test.lang;

import org.eclipse.xtext.resource.XtextResourceSet;

import com.google.inject.Injector;

import io.sapl.grammar.SAPLStandaloneSetup;
import io.sapl.grammar.sapl.SaplPackage;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.test.coverage.api.CoverageHitRecorder;

public class TestSaplInterpreter extends DefaultSAPLInterpreter {
	
	protected  static final Injector INJECTOR = new SAPLStandaloneSetup().createInjectorAndDoEMFRegistration();
	
	public TestSaplInterpreter(CoverageHitRecorder recorder) {
		
		String property = System.getProperty("io.sapl.test.coverage.collect");
		if( property== null || Boolean.parseBoolean(property)) {
			INJECTOR.getInstance(XtextResourceSet.class).getPackageRegistry().getEPackage(SaplPackage.eNS_URI).setEFactoryInstance(new SaplFactoryImplCoverage(recorder));
		} else {
			//if disabled, set default SaplFactory
			INJECTOR.getInstance(XtextResourceSet.class).getPackageRegistry().getEPackage(SaplPackage.eNS_URI).setEFactoryInstance(new SaplFactoryImpl());
		}
	}

	/*
	@Override
	protected SAPL loadAsResource(InputStream policyInputStream) {
		
		//insert custom SaplFactory
		String property = System.getProperty("io.sapl.test.coverage.collect");
		if( property== null || Boolean.parseBoolean(property)) {
			INJECTOR.getInstance(XtextResourceSet.class).getPackageRegistry().getEPackage(SaplPackage.eNS_URI).setEFactoryInstance(new SaplFactoryImplCoverage(recorder));
		} else {
			//if disabled, set default SaplFactory
			INJECTOR.getInstance(XtextResourceSet.class).getPackageRegistry().getEPackage(SaplPackage.eNS_URI).setEFactoryInstance(new SaplFactoryImpl());
		}
		
		return super.loadAsResource(policyInputStream);
	}
	*/
	
	/*
	@Override
	public SAPL parse(InputStream saplInputStream) {
		return loadAsResource(saplInputStream);
	}
	*/

}