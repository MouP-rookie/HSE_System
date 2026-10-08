package com.cmeim.hse;

import com.cmeim.hse.iot.IotProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(IotProperties.class)
public class HseIotDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(HseIotDemoApplication.class, args);
    }
}
