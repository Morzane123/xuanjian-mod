package top.xuanjian.guild.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 模组配置：读取/写入 config/xuanjianmod.properties
 */
public class ModConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("xuanjianmod");

    private static final String FILE_NAME = "xuanjianmod.properties";
    private static final String DEFAULT_API_BASE = "https://xuanjian.top";
    private static final int DEFAULT_SYNC_INTERVAL = 60;
    private static final int DEFAULT_HEARTBEAT_INTERVAL = 1800;

    private final Path configDir;
    private final Path file;

    private String apiBase = DEFAULT_API_BASE;
    private String serverKey = "";
    private String serverIp = "";
    private int syncInterval = DEFAULT_SYNC_INTERVAL;
    private int heartbeatInterval = DEFAULT_HEARTBEAT_INTERVAL;

    public ModConfig(Path configDir) {
        this.configDir = configDir;
        this.file = configDir.resolve(FILE_NAME);
        load();
    }

    private void load() {
        Properties props = new Properties();
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
                apiBase = props.getProperty("api.base", DEFAULT_API_BASE);
                serverKey = props.getProperty("server.key", "");
                serverIp = props.getProperty("server.ip", "");
                syncInterval = parseInt(props.getProperty("sync.interval"), DEFAULT_SYNC_INTERVAL);
                heartbeatInterval = parseInt(props.getProperty("heartbeat.interval"), DEFAULT_HEARTBEAT_INTERVAL);
            } catch (IOException e) {
                LOGGER.error("读取配置失败，使用默认值", e);
            }
        } else {
            save();
            LOGGER.info("已生成默认配置文件: {}", file);
        }
    }

    public void save() {
        Properties props = new Properties();
        props.setProperty("api.base", apiBase);
        props.setProperty("server.key", serverKey);
        props.setProperty("server.ip", serverIp);
        props.setProperty("sync.interval", String.valueOf(syncInterval));
        props.setProperty("heartbeat.interval", String.valueOf(heartbeatInterval));
        try {
            Files.createDirectories(configDir);
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "Xuanjian Guild Mod 配置文件");
            }
        } catch (IOException e) {
            LOGGER.error("保存配置失败", e);
        }
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    public String getApiBase() {
        return apiBase;
    }

    public void setApiBase(String apiBase) {
        this.apiBase = apiBase;
    }

    public String getServerKey() {
        return serverKey;
    }

    public void setServerKey(String serverKey) {
        this.serverKey = serverKey;
    }

    public String getServerIp() {
        return serverIp;
    }

    public void setServerIp(String serverIp) {
        this.serverIp = serverIp;
    }

    public int getSyncInterval() {
        return syncInterval;
    }

    public void setSyncInterval(int syncInterval) {
        this.syncInterval = syncInterval;
    }

    public int getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public void setHeartbeatInterval(int heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }
}
