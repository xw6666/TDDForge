package com.tddforge.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("mysql")
public class MysqlConfig {

    @NotBlank(message = "mysql.url must not be blank")
    private String url;

    @NotBlank(message = "mysql.username must not be blank")
    private String username;

    @NotBlank(message = "mysql.password must not be blank")
    private String password;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
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
}
