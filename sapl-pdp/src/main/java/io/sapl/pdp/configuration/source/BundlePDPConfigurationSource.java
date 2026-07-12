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
package io.sapl.pdp.configuration.source;

import io.sapl.pdp.configuration.PDPConfigurationException;
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
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * PDP configuration source that loads configurations from .saplbundle files in
 * a directory with file watching for hot-reload.
 * <p>
 * Each bundle file represents a single PDP configuration. The PDP ID is derived
 * from the filename (minus the .saplbundle extension). Bundle parsing is
 * delegated to {@link BundleParser}.
 * </p>
 * <h2>Directory Layout</h2>
 *
 * <pre>
 * /bundles/
 *   production.saplbundle     # pdpId = "production"
 *   staging.saplbundle        # pdpId = "staging"
 *   development.saplbundle    # pdpId = "development"
 * </pre>
 *
 * <h2>Publishing Bundles</h2>
 * <p>
 * Publish a bundle atomically: write it to a temporary file and then atomically move
 * or rename it into the watched directory (for example {@link java.nio.file.Files#move}
 * with {@link java.nio.file.StandardCopyOption#ATOMIC_MOVE}). Writing a bundle in place
 * lets the file watcher observe a partial file, which is rejected as a broken bundle
 * and briefly marks the pdpId {@code STALE} until the write completes. An atomic
 * publish is observed as a single complete replacement.
 * </p>
 * <h2>Security Model</h2>
 * <p>
 * This source requires a {@link BundleSecurityPolicy} that determines how
 * bundle signatures are verified.
 * </p>
 * <h2>Thread Safety</h2>
 * <p>
 * This class is thread-safe. The file monitoring thread runs independently and
 * emits events to subscribed listeners.
 * </p>
 *
 * @see BundleParser
 * @see BundleSecurityPolicy
 */
@Slf4j
public final class BundlePDPConfigurationSource implements PDPConfigurationSource {

    private static final String BUNDLE_EXTENSION = ".saplbundle";

    private static final long POLL_INTERVAL_MS        = 500;
    private static final long MONITOR_STOP_TIMEOUT_MS = 5000;

    private static final String ERROR_FAILED_TO_LOAD_BUNDLES       = "Failed to load bundles from directory.";
    private static final String ERROR_FAILED_TO_START_FILE_MONITOR = "Failed to start file monitor for bundle directory.";
    private static final String ERROR_PATH_IS_NOT_DIRECTORY        = "Bundle path is not a directory.";
    private static final String ERROR_SECURITY_POLICY_NULL         = "Security policy must not be null.";

    private static final String WARN_NO_BUNDLES_SIGNED   = """
            No policy bundles found in '{}'. \
            The PDP is running but cannot make authorization decisions. \
            Health status: DOWN. All decisions: INDETERMINATE. \
            To deploy policies, generate a keypair and create a signed bundle: \
            sapl-node bundle keygen -o signing && \
            sapl-node bundle create -i <policy-dir> -o {}/default.saplbundle -k signing.pem \
            Then configure the public key in application.yml: \
            io.sapl.pdp.embedded.bundle-security.public-key-path=signing.pub""";
    private static final String WARN_NO_BUNDLES_UNSIGNED = """
            No policy bundles found in '{}'. \
            The PDP is running but cannot make authorization decisions. \
            Health status: DOWN. All decisions: INDETERMINATE. \
            To deploy policies, create a bundle: \
            sapl-node bundle create -i <policy-dir> -o {}/default.saplbundle \
            Signature verification is disabled. \
            For production, enable signing with: sapl-node bundle keygen -o signing""";
    private static final String WARN_SUBSCRIBER_THREW    = "Configuration subscriber threw on event {}: {}.";

    private final Path                              directoryPath;
    private final BundleSecurityPolicy              securityPolicy;
    private final FileAlterationMonitor             monitor;
    private final Set<Consumer<ConfigurationEvent>> subscribers = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean                     activated   = new AtomicBoolean(false);
    private final AtomicBoolean                     closed      = new AtomicBoolean(false);

    /**
     * Creates a source for the specified bundle directory.
     *
     * @param directoryPath the filesystem directory containing .saplbundle
     * files
     * @param securityPolicy the security policy for bundle signature
     * verification
     * @throws NullPointerException if any parameter is null
     * @throws BundleSignatureException if the security policy is invalid
     */
    public BundlePDPConfigurationSource(@NonNull Path directoryPath, @NonNull BundleSecurityPolicy securityPolicy) {
        Objects.requireNonNull(securityPolicy, ERROR_SECURITY_POLICY_NULL);
        securityPolicy.validate();

        this.directoryPath  = PdpIdValidator.resolveHomeFolderIfPresent(directoryPath).toAbsolutePath().normalize();
        this.securityPolicy = securityPolicy;
        this.monitor        = new FileAlterationMonitor(POLL_INTERVAL_MS);
    }

    @Override
    public void subscribe(@NonNull Consumer<ConfigurationEvent> listener) {
        if (closed.get()) {
            return;
        }
        subscribers.add(listener);
        if (activated.compareAndSet(false, true)) {
            activate();
        }
    }

    @Override
    public void unsubscribe(@NonNull Consumer<ConfigurationEvent> listener) {
        subscribers.remove(listener);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            stopMonitorSafely();
            subscribers.clear();
            log.debug("Closed bundle configuration source.");
        }
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    private void activate() {
        log.info("Loading PDP configurations from bundle directory: '{}'.", directoryPath);
        validateDirectory();
        loadInitialBundles();
        startFileMonitor();
    }

    private void emit(ConfigurationEvent event) {
        if (closed.get()) {
            return;
        }
        for (val subscriber : subscribers) {
            try {
                subscriber.accept(event);
            } catch (Exception e) {
                // One misbehaving subscriber must not abort delivery to the
                // others or kill the file-watch thread driving hot reload.
                log.warn(WARN_SUBSCRIBER_THREW, event, e.getMessage());
            }
        }
    }

    private void validateDirectory() {
        if (!Files.isDirectory(directoryPath)) {
            throw new PDPConfigurationException(ERROR_PATH_IS_NOT_DIRECTORY);
        }
    }

    private void loadInitialBundles() {
        try (var stream = Files.list(directoryPath)) {
            val bundles = stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(BUNDLE_EXTENSION)).toList();

            for (val bundlePath : bundles) {
                loadBundle(bundlePath);
            }

            log.info("Loaded {} bundle configurations.", bundles.size());
            if (bundles.isEmpty()) {
                logNoBundlesGuidance();
            }
        } catch (Exception e) {
            throw new PDPConfigurationException(ERROR_FAILED_TO_LOAD_BUNDLES, e);
        }
    }

    private void loadBundle(Path bundlePath) {
        val pdpId = derivePdpIdFromBundleName(bundlePath);
        if (pdpId == null) {
            log.warn("Skipping bundle with no filename: {}.", bundlePath);
            return;
        }

        if (!PdpIdValidator.isValidPdpId(pdpId)) {
            log.warn("Skipping bundle with invalid name: {}.", bundlePath.getFileName());
            return;
        }

        log.debug("Loading bundle '{}' from: {}.", pdpId, bundlePath);

        try {
            val config = BundleParser.parse(bundlePath, pdpId, securityPolicy);
            emit(new ConfigurationEvent.NewConfiguration(config));
            log.debug("Loaded bundle '{}' with {} SAPL documents.", pdpId, config.saplDocuments().size());
        } catch (Exception e) {
            // A present bundle that is definitively broken (bad signature, malformed).
            log.error("Failed to load bundle '{}': {}.", pdpId, e.getMessage(), e);
            emit(new ConfigurationEvent.ConfigurationError(pdpId,
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
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

    private void logNoBundlesGuidance() {
        if (securityPolicy.signatureRequired()) {
            log.warn(WARN_NO_BUNDLES_SIGNED, directoryPath, directoryPath);
        } else {
            log.warn(WARN_NO_BUNDLES_UNSIGNED, directoryPath, directoryPath);
        }
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
            throw new PDPConfigurationException(ERROR_FAILED_TO_START_FILE_MONITOR, e);
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

        @Override
        public void onFileDelete(File file) {
            if (closed.get()) {
                return;
            }
            val pdpId = derivePdpIdFromBundleName(file.toPath());
            if (pdpId != null && PdpIdValidator.isValidPdpId(pdpId)) {
                emit(new ConfigurationEvent.ConfigurationRemoved(pdpId));
                log.info("Removed configuration for deleted bundle '{}'.", pdpId);
            }
        }

        private void handleBundleChange(File file) {
            if (closed.get()) {
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
