package summer.foliaPhantom.config;

public class PluginConfig {
    private final String name;
    private final String originalJarPath;
    private final String patchedJarPath;
    private final boolean foliaEnabled;

    public PluginConfig(String name, String originalJarPath, String patchedJarPath, Boolean foliaEnabled) {
        this.name = name;
        this.originalJarPath = originalJarPath;
        this.patchedJarPath = patchedJarPath;
        this.foliaEnabled = (foliaEnabled != null) ? foliaEnabled : true; // Default to true if null
    }

    public String name() {
        return name;
    }

    public String originalJarPath() {
        return originalJarPath;
    }

    public String patchedJarPath() {
        return patchedJarPath;
    }

    public boolean foliaEnabled() {
        return foliaEnabled;
    }
}
