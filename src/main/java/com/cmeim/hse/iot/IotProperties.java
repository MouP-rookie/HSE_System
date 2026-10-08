package com.cmeim.hse.iot;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "iot")
public class IotProperties {

    private String baseUrl = "https://iot.cme-im.com";
    private String username;
    private String password;
    private String powerHistoryStart = "2025-07-09T00:00:00+08:00";
    private int powerCacheSeconds = 300;
    private int queryLimit = 200000;
    private int gapMinutes = 20;
    private int powerDisplayDays = 90;
    private Device pressure = new Device();
    private Device th = new Device();

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPowerHistoryStart() {
        return powerHistoryStart;
    }

    public void setPowerHistoryStart(String powerHistoryStart) {
        this.powerHistoryStart = powerHistoryStart;
    }

    public int getPowerCacheSeconds() {
        return powerCacheSeconds;
    }

    public void setPowerCacheSeconds(int powerCacheSeconds) {
        this.powerCacheSeconds = powerCacheSeconds;
    }

    public int getQueryLimit() {
        return queryLimit;
    }

    public void setQueryLimit(int queryLimit) {
        this.queryLimit = queryLimit;
    }

    public int getGapMinutes() {
        return gapMinutes;
    }

    public void setGapMinutes(int gapMinutes) {
        this.gapMinutes = gapMinutes;
    }

    public int getPowerDisplayDays() {
        return powerDisplayDays;
    }

    public void setPowerDisplayDays(int powerDisplayDays) {
        this.powerDisplayDays = powerDisplayDays;
    }

    public Device getPressure() {
        return pressure;
    }

    public void setPressure(Device pressure) {
        this.pressure = pressure;
    }

    public Device getTh() {
        return th;
    }

    public void setTh(Device th) {
        this.th = th;
    }

    public static class Device {
        private String id;
        private String name;
        private String label;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }
    }
}

