package io.sapl.test.setup;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import io.sapl.test.interfaces.TestExecutionService;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

class TestExecutionServiceDefaultImpl implements TestExecutionService {

    @Override
    public void execute(final String className, TestExecutionListener testExecutionListener) {
        final var request = buildLauncherDiscoveryRequest(className);

        Launcher launcher = LauncherFactory.create();

        launcher.registerTestExecutionListeners(testExecutionListener);

        launcher.execute(request);
    }

    private LauncherDiscoveryRequest buildLauncherDiscoveryRequest(final String className) {
        return LauncherDiscoveryRequestBuilder.request().selectors(selectClass(className)).build();
    }
}
