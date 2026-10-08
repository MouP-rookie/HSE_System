package com.cmeim.hse.iot;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 直接使用 HttpURLConnection 作为底层实现。
 * 原因: 部分受限环境下 JDK 自带 HttpClient 初始化会因无法创建回环管道而失败,
 * 而 Spring Boot 默认的 RestClient.Builder 正是基于 JDK HttpClient。
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(120));
        return RestClient.builder().requestFactory(factory);
    }
}
