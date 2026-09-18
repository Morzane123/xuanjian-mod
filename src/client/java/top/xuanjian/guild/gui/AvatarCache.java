package top.xuanjian.guild.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.xuanjian.guild.XuanjianMod;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 官网头像纹理缓存。
 *
 * 流程：后台线程下载字节 → 渲染线程 NativeImage.read + DynamicTexture + TextureManager.register → blit 绘制。
 *
 * 几个关键点：
 *  - 绝不在渲染线程做网络请求（否则界面直接卡住）
 *  - 原始字节也缓存一份：资源包重载（F3+T）会把动态注册的纹理全部 close 掉，
 *    此时用字节重新 register 即可，不必重新下载
 *  - 失败（404/超时/非图片）记入 FAILED，不再重试，避免每帧反复发请求
 *  - 下载并发固定为 2，避免一次打开界面就打爆连接数
 */
public final class AvatarCache {

    private static final Logger LOGGER = LoggerFactory.getLogger("xuanjianmod");

    /** url -> 已注册的纹理 ID */
    private static final Map<String, Identifier> IDS = new ConcurrentHashMap<>();
    /** url -> 原始字节（资源重载后重建纹理用） */
    private static final Map<String, byte[]> RAW = new ConcurrentHashMap<>();
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "xj-avatar");
        t.setDaemon(true);
        return t;
    });

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private AvatarCache() { }

    /**
     * 取头像纹理。未就绪返回 null（调用方画占位），并自动触发后台下载。
     */
    public static Identifier get(String url) {
        if (url == null || url.isBlank()) return null;

        Identifier id = IDS.get(url);
        if (id != null) {
            // 资源包重载后纹理可能已被关闭 → 用缓存字节重建
            if (Minecraft.getInstance().getTextureManager().getTexture(id) != null) return id;
            byte[] cached = RAW.get(url);
            if (cached != null) {
                Identifier again = register(url, cached);
                if (again != null) return again;
            }
            IDS.remove(url);
        }

        if (FAILED.contains(url) || PENDING.contains(url)) return null;

        PENDING.add(url);
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(12))
                    .header("User-Agent", "xuanjianmod")
                    .GET()
                    .build();
        } catch (Exception e) {
            PENDING.remove(url);
            FAILED.add(url);
            return null;
        }

        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofByteArray())
                .whenComplete((resp, err) -> {
                    PENDING.remove(url);
                    if (err != null || resp == null || resp.statusCode() != 200) {
                        FAILED.add(url);
                        LOGGER.debug("头像下载失败 {} -> {}", url, err != null ? err.getMessage() : (resp == null ? "null" : resp.statusCode()));
                        return;
                    }
                    byte[] bytes = resp.body();
                    if (bytes == null || bytes.length == 0 || bytes.length > 4 * 1024 * 1024) {
                        FAILED.add(url);
                        return;
                    }
                    RAW.put(url, bytes);
                    // 纹理注册必须在渲染线程
                    Minecraft.getInstance().execute(() -> register(url, bytes));
                });

        return null;
    }

    /** 是否已失败（供界面显示占位说明） */
    public static boolean isFailed(String url) {
        return url != null && FAILED.contains(url);
    }

    /** 渲染线程：字节 → 纹理并注册 */
    private static Identifier register(String url, byte[] bytes) {
        try {
            NativeImage image = NativeImage.read(bytes);
            Identifier id = Identifier.fromNamespaceAndPath(XuanjianMod.MOD_ID, "avatar/" + digest(url));
            DynamicTexture texture = new DynamicTexture(() -> "xj-avatar", image);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            IDS.put(url, id);
            return id;
        } catch (Exception e) {
            FAILED.add(url);
            LOGGER.warn("头像纹理注册失败: {}", e.getMessage());
            return null;
        }
    }

    private static String digest(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
