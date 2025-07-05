package summer.foliaPhantom.jar;

import java.io.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

public class JarPatcher {

    /**
     * Creates a new JAR file with a modified plugin.yml to support Folia.
     *
     * @param originalJar The original plugin JAR file.
     * @param patchedJar  The destination file for the patched JAR.
     * @throws Exception If any error occurs during patching.
     */
    public static void createFoliaSupportedJar(File originalJar, File patchedJar) throws Exception {
        try (JarInputStream jis = new JarInputStream(new FileInputStream(originalJar));
             JarOutputStream jos = new JarOutputStream(new FileOutputStream(patchedJar))) {

            JarEntry entry;
            byte[] buffer = new byte[1024];
            boolean pluginYmlFound = false;

            while ((entry = jis.getNextJarEntry()) != null) {
                String entryName = entry.getName();
                if ("plugin.yml".equals(entryName)) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    int len;
                    while ((len = jis.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }
                    String originalContent = baos.toString("UTF-8");
                    String modifiedContent = addFoliaSupport(originalContent);
                    JarEntry newEntry = new JarEntry("plugin.yml");
                    jos.putNextEntry(newEntry);
                    jos.write(modifiedContent.getBytes("UTF-8"));
                    jos.closeEntry();
                    pluginYmlFound = true;
                } else {
                    jos.putNextEntry(new JarEntry(entryName));
                    int len;
                    while ((len = jis.read(buffer)) > 0) {
                        jos.write(buffer, 0, len);
                    }
                    jos.closeEntry();
                }
                jis.closeEntry();
            }

            if (!pluginYmlFound) {
                throw new Exception("patch: plugin.yml was not found in " + originalJar.getName());
            }
        }
    }

    /**
     * Modifies the plugin.yml content to add or update 'folia-supported: true'.
     *
     * @param originalContent The original content of plugin.yml.
     * @return The modified content.
     */
    private static String addFoliaSupport(String originalContent) {
        StringBuilder sb = new StringBuilder(originalContent);
        if (!originalContent.contains("folia-supported:")) {
            if (!originalContent.endsWith("\n")) sb.append("\n");

            sb.append("folia-supported: true\n");

        } else {
            // Ensure replacement handles various spacing and existing true/false
            sb = new StringBuilder(originalContent.replaceAll(
                    "folia-supported:\\s*(true|false)", "folia-supported: true"));
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar <jar-file-name>.jar <original-jar-path> <patched-jar-path>");
            System.exit(1);
        }

        File originalJar = new File(args[0]);
        File patchedJar = new File(args[1]);

        if (!originalJar.exists()) {
            System.err.println("Error: Original JAR file not found at " + originalJar.getAbsolutePath());
            System.exit(1);
        }

        try {
            System.out.println("Patching " + originalJar.getName() + " to " + patchedJar.getName() + "...");
            createFoliaSupportedJar(originalJar, patchedJar);
            System.out.println("Successfully patched JAR: " + patchedJar.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Error during patching: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
