package com.metalheart.model;

import java.net.URLEncoder;
import lombok.Data;

import static java.nio.charset.StandardCharsets.UTF_8;

@Data
public class IMAPConnectionData {

    private String protocol;

    private String host;

    private Integer port;

    private String username;

    private String password;

    private String folder;

    public String getConnectionUrl() {

        String login = URLEncoder.encode(username, UTF_8);
        String pass = URLEncoder.encode(password, UTF_8);
        return String.format("%s://%s:%s@%s:%s/%s", protocol, login, pass, host, port, folder);
    }
}
