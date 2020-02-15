package com.metalheart;

import com.metalheart.testcontainer.MailServerDockerComposeContainer;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Slf4j
@Configuration
public class TestConfiguration {
    @Bean
    @Primary
    public AppProperties appProperties() {
        AppProperties props = new AppProperties();
        props.setUsername("user@domain.com");
        props.setPassword("password");
        props.setProtocol("imap");

        props.setHost(MailServerDockerComposeContainer.getContainerIp());
        props.setPort(MailServerDockerComposeContainer.getImapPort());

        props.setFolder("inbox");
        return props;
    }

    @Bean
    @Primary
    public JavaMailSenderImpl getSender() {

        JavaMailSenderImpl javaMailSender = new JavaMailSenderImpl();
        javaMailSender.setProtocol("smtp");
        javaMailSender.setUsername("user@domain.com");
        javaMailSender.setPassword("password");

        javaMailSender.setHost(MailServerDockerComposeContainer.getContainerIp());
        javaMailSender.setPort(MailServerDockerComposeContainer.getSmtpPort());

        Properties props = new Properties();
        props.put("mail.smtp.auth", true);
        props.put("mail.smtp.starttls.enable", false);
        props.put("mail.smtp.ssl.enable", false);
        javaMailSender.setJavaMailProperties(props);

        return javaMailSender;
    }
}