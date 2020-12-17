package io.sapl.prp.filemonitoring;

import lombok.experimental.UtilityClass;

import java.io.*;
import java.nio.charset.StandardCharsets;

@UtilityClass
public class FileUtil {
    public static String resolveHomeFolderIfPresent(String policyPath) {
        if (policyPath.startsWith("~" + File.separator) || policyPath.startsWith("~/")) {
            return System.getProperty("user.home") + policyPath.substring(1);
        }
        return policyPath;
    }
    public static String readFile(File file) throws IOException {
        var fis = new FileInputStream(file);
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
}
