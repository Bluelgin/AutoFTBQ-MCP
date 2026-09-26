package dev.autoftbq.mcp.platform;

import java.nio.file.Path;

public interface ModEnvironment {
    String loaderId();
    String modVersion(String modId);
    Path configDirectory();
}
