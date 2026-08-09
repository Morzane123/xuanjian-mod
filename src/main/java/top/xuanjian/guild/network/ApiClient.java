package top.xuanjian.guild.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 官网 API HTTP 客户端（仅依赖 JDK 内置 HttpClient 与 Gson）
 */
public class ApiClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("xuanjianmod");
    private static final Gson GSON = new Gson();
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private String baseUrl;
    private String serverKey;

    public ApiClient(String baseUrl, String serverKey) {
        this.baseUrl = trimSlash(baseUrl);
        this.serverKey = serverKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public void update(String baseUrl, String serverKey) {
        this.baseUrl = trimSlash(baseUrl);
        this.serverKey = serverKey;
    }

    private static String trimSlash(String url) {
        if (url == null || url.isBlank()) return "";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * GET 请求，返回 JSON 对象；失败返回 null
     */
    public JsonObject get(String path) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET(), null);
    }

    /**
     * POST 请求（JSON body），返回 JSON 对象；失败返回 null
     */
    public JsonObject post(String path, Map<String, Object> body) {
        String json = body == null ? "{}" : GSON.toJson(body);
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)), json);
    }

    private JsonObject send(HttpRequest.Builder builder, String bodyForLog) {
        if (baseUrl == null || baseUrl.isBlank()) return null;
        builder.header("User-Agent", "xuanjianmod")
                .timeout(TIMEOUT);
        if (serverKey != null && !serverKey.isBlank()) {
            builder.header("X-Server-Key", serverKey);
        }
        try {
            HttpRequest request = builder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                if (response.body() == null || response.body().isBlank()) return new JsonObject();
                return JsonParser.parseString(response.body()).getAsJsonObject();
            }
            LOGGER.warn("API 请求失败 {} {} -> HTTP {}", methodOf(request), request.uri().getPath(), response.statusCode());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            LOGGER.warn("API 请求异常 {} -> {}", baseUrl, e.getMessage());
        } catch (Exception e) {
            LOGGER.warn("API 响应解析失败: {}", e.getMessage());
        }
        return null;
    }

    private static String methodOf(HttpRequest request) {
        return request.method();
    }
}
