/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.pdp.configuration;

import io.sapl.api.pdp.PDPConfiguration;
import io.sapl.pdp.configuration.bundle.BundleParser;
import io.sapl.pdp.configuration.bundle.BundleSecurityPolicy;
import io.sapl.pdp.configuration.bundle.BundleSignatureException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * PDP configuration source that loads configurations from .saplbundle files in
 * a directory with file watching for
 * hot-reload.
 * <p>
 * Each bundle file represents a single PDP configuration. The PDP ID is derived
 * from the filename (minus the
 * .saplbundle extension). Bundle parsing is delegated to {@link BundleParser}.
 * </p>
 * <h2>Directory Layout</h2>
 *
 * <pre>
 * /bundles/
 * ├── production.saplbundle     # pdpId = "production"
 * ├── staging.saplbundle        # pdpId = "staging"
 * └── development.saplbundle    # pdpId = "development"
 * </pre>
 *
 * <h2>Security Model</h2>
 * <p>
 * This source requires a {@link BundleSecurityPolicy} that determines how
 * bundle signatures are verified. In production
 * environments, use
 * {@link BundleSecurityPolicy#requireSignature(java.security.PublicKey)} to
 * enforce that all bundles
 * are cryptographically signed.
 * </p>
 * <p>
 * For development environments where signature verification needs to be
 * disabled, a two-factor opt-out is required:
 * both signature verification must be disabled AND the associated risks must be
 * explicitly accepted.
 * </p>
 * <h2>Callback Invocation</h2>
 * <p>
 * The callback is invoked:
 * </p>
 * <ul>
 * <li>Once per bundle during construction with initial configurations</li>
 * <li>On each bundle file change (create, modify) after debouncing</li>
 * </ul>
 * <p>
 * For bundle content security (ZIP bomb protection, path traversal prevention),
 * see {@link BundleParser}.
 * </p>
 * <h2>Thread Safety</h2>
 * <p>
 * This class is thread-safe. The file monitoring thread runs independently and
 * invokes the callback directly. The
 * callback implementation must handle thread safety for its own state.
 * </p>
 * <h2>Spring Integration</h2>
 * <p>
 * This class implements {@link reactor.core.Disposable} but Spring does not
 * automatically call {@code dispose()} on
 * Reactor's Disposable interface. When exposing as a Spring bean, explicitly
 * specify the destroy method:
 * </p>
 *
 * <pre>{@code
 * @Bean(destroyMethod = "dispose")
 * public BundlePDPConfigurationSource bundleSource(BundleSecurityPolicy policy) {
 *     return new BundlePDPConfigurationSource(Path.of("/bundles"), policy,
 *             security -> configRegister.loadConfiguration(security, false));
 * }
 * }</pre>
 *
 * @see BundleParser
 * @see BundleSecurityPolicy
 */
@Slf4j
public final class BundlePDPConfigurationSource implements PDPConfigurationSource {

    private static final String BUNDLE_EXTENSION = ".saplbundle";

    private static final long POLL_INTERVAL_MS        = 500;
    private static final long MONITOR_STOP_TIMEOUT_MS = 5000;

    private final Path                       directoryPath;
    private final BundleSecurityPolicy       securityPolicy;
    private final Consumer<PDPConfiguration> callback;
    private final FileAlterationMonitor      monitor;
    private final AtomicBoolean              disposed = new AtomicBoolean(false);

    /**
     * Creates a source loading bundles from the specified directory.
     * <p>
     * The security policy is validated during construction. If signature
     * verification is disabled without risk
     * acceptance, construction will fail.
     * </p>
     * <p>
     * Each bundle must contain a pdp.json file with a {@code configurationId}
     * field. The PDP ID is derived from the
     * bundle filename (minus the .saplbundle extension).
     * </p>
     *
     * @param directoryPath
     * the filesystem directory containing .saplbundle files
     * @param securityPolicy
     * the security policy for bundle signature verification
     * @param callback
     * called when a configuration is loaded or updated
     *
     * @throws NullPointerException
     * if any parameter is null
     * @throws BundleSignatureException
     * if the security policy is invalid
     */
    public BundlePDPConfigurationSource(@NonNull Path directoryPath,
            @NonNull BundleSecurityPolicy securityPolicy,
            @NonNull Consumer<PDPConfiguration> callback) {
        Objects.requireNonNull(securityPolicy, "Security policy must not be null.");
        securityPolicy.validate();

        this.directoryPath  = PDPConfigurationSource.resolveHomeFolderIfPresent(directoryPath).toAbsolutePath()
                .normalize();
        this.securityPolicy = securityPolicy;
        this.callback       = callback;
        this.monitor        = new FileAlterationMonitor(POLL_INTERVAL_MS);

        log.info("Loading PDP configurations from bundle directory: '{}'.", this.directoryPath);
        validateDirectory();
        loadInitialBundles();
        startFileMonitor();
    }

    @Override
    public void dispose() {
        if (disposed.compareAndSet(false, true)) {
            stopMonitorSafely();
            log.debug("Disposed bundle configuration source.");
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed.get();
    }

    private void validateDirectory() {
        if (!Files.exists(directoryPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new PDPConfigurationException("Bundle directory does not exist.");
        }
        if (Files.isSymbolicLink(directoryPath)) {
            throw new PDPConfigurationException("Bundle directory must not be a symbolic link.");
        }
        if (!Files.isDirectory(directoryPath)) {
            throw new PDPConfigurationException("Bundle path is not a directory.");
        }
    }

    private void loadInitialBundles() {
        try (var stream = Files.list(directoryPath)) {
            val bundles = stream.filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().endsWith(BUNDLE_EXTENSION)).toList();

            for (val bundlePath : bundles) {
                loadBundle(bundlePath);
            }

            log.info("Loaded {} bundle configurations.", bundles.size());
        } catch (Exception e) {
            throw new PDPConfigurationException("Failed to load bundles from directory.", e);
        }
    }

    private void loadBundle(Path bundlePath) {
        if (Files.isSymbolicLink(bundlePath)) {
            log.warn("Skipping symbolic link bundle: {}.", bundlePath.getFileName());
            return;
        }

        val pdpId = derivePdpIdFromBundleName(bundlePath);
        if (pdpId == null) {
            log.warn("Skipping bundle with no filename: {}.", bundlePath);
            return;
        }

        if (!PDPConfigurationSource.isValidPdpId(pdpId)) {
            log.warn("Skipping bundle with invalid name: {}.", bundlePath.getFileName());
            return;
        }

        log.debug("Loading bundle '{}' from: {}.", pdpId, bundlePath);

        try {
            val config = BundleParser.parse(bundlePath, pdpId, securityPolicy);
            callback.accept(config);
            log.debug("Loaded bundle '{}' with {} SAPL documents.", pdpId, config.saplDocuments().size());
        } catch (Exception e) {
            log.error("Failed to load bundle '{}': {}.", pdpId, e.getMessage(), e);
        }
    }

    private String derivePdpIdFromBundleName(Path bundlePath) {
        val fileNamePath = bundlePath.getFileName();
        if (fileNamePath == null) {
            return null;
        }
        val fileName = fileNamePath.toString();
        return fileName.substring(0, fileName.length() - BUNDLE_EXTENSION.length());
    }

    private void startFileMonitor() {
        try {
            val observer = FileAlterationObserver.builder().setFile(directoryPath.toFile())
                    .setFileFilter(this::isBundleFile).get();

            observer.addListener(new BundleChangeListener());
            monitor.addObserver(observer);
            monitor.start();
            log.debug("Started file monitoring on bundle directory: {}.", directoryPath);
        } catch (Exception e) {
            throw new PDPConfigurationException("Failed to start file monitor for bundle directory.", e);
        }
    }

    private boolean isBundleFile(File file) {
        return file.isFile() && file.getName().endsWith(BUNDLE_EXTENSION);
    }

    private void stopMonitorSafely() {
        try {
            monitor.stop(MONITOR_STOP_TIMEOUT_MS);
        } catch (Exception e) {
            log.warn("Error stopping file monitor, attempting interrupt.", e);
            try {
                Thread.currentThread().interrupt();
                monitor.stop(0);
            } catch (Exception e2) {
                log.error("Failed to force stop file monitor.", e2);
            }
        }
    }

    /**
     * Listener for bundle file changes in the watched directory.
     */
    private class BundleChangeListener extends FileAlterationListenerAdaptor {

        @Override
        public void onFileCreate(File file) {
            handleBundleChange(file);
        }

        @Override
        public void onFileChange(File file) {
            handleBundleChange(file);
        }

        private void handleBundleChange(File file) {
            if (disposed.get()) {
                return;
            }
            val bundlePath = file.toPath();
            log.debug("Detected bundle change: {}.", bundlePath.getFileName());
            try {
                loadBundle(bundlePath);
            } catch (Exception e) {
                log.error("Failed to reload bundle: {}.", bundlePath.getFileName(), e);
            }
        }
    }

}
