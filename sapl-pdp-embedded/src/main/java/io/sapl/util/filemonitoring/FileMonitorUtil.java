package io.sapl.util.filemonitoring;

import java.io.File;
import java.io.FileFilter;

import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;

import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;

@UtilityClass
public class FileMonitorUtil {

	private static final long POLL_INTERVAL_IN_MS = 500;

	public static String resolveHomeFolderIfPresent(String policyPath) {
		policyPath = policyPath.replace("/", File.separator);

		if (policyPath.startsWith("~" + File.separator))
			return getUserHomeProperty() + policyPath.substring(1);

		return policyPath;
	}

	static String getUserHomeProperty() {
		return System.getProperty("user.home");
	}

	public static Flux<FileEvent> monitorDirectory(final String watchDir, final FileFilter fileFilter) {
		return Flux.push(emitter -> {
			var adaptor = new FileEventAdaptor(emitter);
			var monitor = new FileAlterationMonitor(POLL_INTERVAL_IN_MS);
			var observer = new FileAlterationObserver(watchDir, fileFilter);
			monitor.addObserver(observer);
			observer.addListener(adaptor);
			emitter.onDispose(() -> {
				try {
					monitor.stop();
				} catch (Exception e) {
					emitter.error(e);
				}
			});

			try {
				monitor.start();
			} catch (Exception e) {
				emitter.error(e);
			}
		});
	}

}
