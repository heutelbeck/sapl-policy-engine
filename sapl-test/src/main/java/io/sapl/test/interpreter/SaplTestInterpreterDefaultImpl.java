package io.sapl.test.interpreter;

import com.google.inject.Injector;

import io.sapl.test.grammar.SAPLTestStandaloneSetup;
import io.sapl.test.grammar.sAPLTest.SAPLTest;
import io.sapl.test.interfaces.SaplTestDslInterpreter;
import java.io.IOException;
import java.io.InputStream;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.common.util.WrappedException;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.resource.XtextResourceSet;

public final class SaplTestInterpreterDefaultImpl implements SaplTestDslInterpreter {
    private static final Injector INJECTOR = new SAPLTestStandaloneSetup().createInjectorAndDoEMFRegistration();
    private static final String DUMMY_RESOURCE_URI = "policy:/test1.sapltest";

    public SAPLTest loadAsResource(InputStream policyInputStream) {
        final XtextResourceSet resourceSet = INJECTOR.getInstance(XtextResourceSet.class);
        final Resource resource = resourceSet.createResource(URI.createFileURI(DUMMY_RESOURCE_URI));

        try {
            resource.load(policyInputStream, resourceSet.getLoadOptions());
        } catch (IOException | WrappedException e) {
            throw new RuntimeException(e);
        }

        if (!resource.getErrors().isEmpty()) {
            throw new RuntimeException("Got Errors");
        }
        return (SAPLTest) resource.getContents().get(0);
    }
}
