package com.metalheart;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "imap")
@AllArgsConstructor
@NoArgsConstructor
public class AppProperties {

    private String username;

    private String password;

    private String protocol;

    private String host;

    private Integer port;

    private String folder;
}
