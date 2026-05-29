package com.tddforge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("publish")
public class PublishConfig {

    private String remote = "origin";

    public String getRemote() {
        return remote;
    }

    public void setRemote(String remote) {
        this.remote = remote;
    }
}
