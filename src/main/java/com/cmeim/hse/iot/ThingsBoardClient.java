package com.cmeim.hse.iot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 ThingsBoard REST 客户端: 负责登录取 JWT、缓存 token、按需重试 401。
 */
@Component
public class ThingsBoardClient {

    private static final Logger log = LoggerFactory.getLogger(ThingsBoardClient.class);

    private final IotProperties props;
    private final RestClient rest;
    private final ObjectMapper mapper;

    private String token;
    private long tokenExpiresAt;

    public ThingsBoardClient(IotProperties props, RestClient.Builder restClientBuilder, ObjectMapper mapper) {
        this.props = props;
        this.rest = restClientBuilder.build();
        this.mapper = mapper;
    }

    public synchronized String token() {
        long now = System.currentTimeMillis();
        if (token != null && now < tokenExpiresAt - 60_000L) {
            return token;
        }
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("username", props.getUsername());
        payload.put("password", props.getPassword());
        String body;
        try {
            body = mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("构造登录请求失败", e);
        }
        JsonNode resp = postJson(trimBase() + "/api/auth/login", body);
        String fresh = resp.path("token").asText(null);
        if (fresh == null || fresh.isBlank()) {
            throw new IllegalStateException("ThingsBoard 登录响应中没有 token");
        }
        this.token = fresh;
        this.tokenExpiresAt = expiryOf(fresh);
        log.info("ThingsBoard 登录成功, token 到期时间戳 {}", tokenExpiresAt);
        return token;
    }

    public synchronized void invalidate() {
        this.token = null;
        this.tokenExpiresAt = 0L;
    }

    /**
     * 查询遥测数据。返回结构为 { key: [ { ts, value }, ... ] }。
     */
    public JsonNode timeseries(String deviceId, List<String> keys, long startTs, long endTs,
                               String agg, int limit, String orderBy) {
        HttpClientErrorException lastError = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            URI uri = buildUri(deviceId, keys, startTs, endTs, agg, limit, orderBy);
            try {
                String text = rest.get()
                        .uri(uri)
                        .header("X-Authorization", "Bearer " + token())
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .body(String.class);
                return mapper.readTree(text);
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                    log.warn("ThingsBoard 返回 {}, 重新登录后重试", e.getStatusCode());
                    invalidate();
                    lastError = e;
                    continue;
                }
                throw new IllegalStateException("ThingsBoard 查询失败: HTTP " + e.getStatusCode()
                        + " " + e.getResponseBodyAsString(), e);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("ThingsBoard 响应解析失败", e);
            }
        }
        throw new IllegalStateException("ThingsBoard 鉴权失败, 重试后仍未通过", lastError);
    }

    private JsonNode postJson(String url, String body) {
        try {
            String text = rest.post()
                    .uri(URI.create(url))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return mapper.readTree(text);
        } catch (HttpClientErrorException e) {
            throw new IllegalStateException("ThingsBoard 登录失败: HTTP " + e.getStatusCode()
                    + " " + e.getResponseBodyAsString(), e);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("ThingsBoard 登录响应解析失败", e);
        }
    }

    private URI buildUri(String deviceId, List<String> keys, long startTs, long endTs,
                         String agg, int limit, String orderBy) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(trimBase() + "/api/plugins/telemetry/DEVICE/" + deviceId + "/values/timeseries");
        if (keys != null && !keys.isEmpty()) {
            builder.queryParam("keys", String.join(",", keys));
        }
        builder.queryParam("startTs", startTs);
        builder.queryParam("endTs", endTs);
        if (agg != null && !agg.isBlank()) {
            builder.queryParam("agg", agg);
        }
        if (limit > 0) {
            builder.queryParam("limit", limit);
        }
        if (orderBy != null && !orderBy.isBlank()) {
            builder.queryParam("orderBy", orderBy);
        }
        return builder.build().encode().toUri();
    }

    private String trimBase() {
        String base = props.getBaseUrl() == null ? "" : props.getBaseUrl().trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private long expiryOf(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length >= 2) {
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                JsonNode node = mapper.readTree(payload);
                if (node.hasNonNull("exp")) {
                    return node.get("exp").asLong() * 1000L;
                }
            }
        } catch (Exception e) {
            log.warn("解析 JWT 过期时间失败, 按 30 分钟缓存处理: {}", e.getMessage());
        }
        return System.currentTimeMillis() + 30L * 60_000L;
    }
}
