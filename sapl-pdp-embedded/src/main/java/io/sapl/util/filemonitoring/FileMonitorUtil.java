package io.sapl.util.filemonitoring;

import lombok.experimental.UtilityClass;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@UtilityClass
public class FileMonitorUtil {

    private static final long POLL_INTERVAL = 500; // ms

    public static String resolveHomeFolderIfPresent(String policyPath) {
        policyPath = policyPath.replaceAll("/", File.separator);

        if (policyPath.startsWith("~" + File.separator)) {
            return getUserHomeProperty() + policyPath.substring(1);
        }
        return policyPath;
    }

    static String getUserHomeProperty() {
        return System.getProperty("user.home");
    }

    public static String readFile(File file) throws IOException {
        var fis = Files.newInputStream(file.toPath());
        try (BufferedReader br = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
                sb.append('\n');
            }
            return sb.toString();
        }
    }

    public static Flux<FileEvent> monitorDirectory(final String watchDir, final FileFilter fileFilter) {
        return Flux.push(emitter -> {
            var adaptor = new FileEventAdaptor(emitter);
            FileAlterationMonitor monitor = new FileAlterationMonitor(POLL_INTERVAL);
            FileAlterationObserver observer = new FileAlterationObserver(watchDir, fileFilter);
            monitor.addObserver(observer);
            observer.addListener(adaptor);
            emitter.onCancel(() -> {
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
