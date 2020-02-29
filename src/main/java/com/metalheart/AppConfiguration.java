package com.metalheart;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.messaging.MessageChannel;

@Configuration
@ComponentScan(basePackages = {
    "com.metalheart.converter",
    "com.metalheart.service"
})
public class AppConfiguration {

    public static final String MAIL_CHANNEL = "mail-channel";

    @Bean(name = MAIL_CHANNEL)
    public MessageChannel grabbingChannel() {
        return MessageChannels.queue(MAIL_CHANNEL).get();
    }
}
