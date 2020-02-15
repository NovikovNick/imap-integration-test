package com.metalheart.service;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
@Component
public class MailService {

    @Autowired
    private JavaMailSenderImpl sender;

    public void send(String to, String subject, String content) {

        Session session = sender.getSession();
        try (ByteArrayOutputStream os = new ByteArrayOutputStream();
             PrintStream ps = new PrintStream(os, true, UTF_8)) {

            session.setDebugOut(ps);
            session.setDebug(true);

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(sender.getUsername()));
            message.setRecipients(Message.RecipientType.TO, to);
            message.setSubject(subject);
            message.setText(content);

            try {
                sender.send(message);
            } catch (Exception e) {
                log.error("Unable to send email. SMTP log: \n\n" + os.toString(UTF_8), e.getMessage(), e);
                throw new RuntimeException(e);
            }

            String messageID = message.getMessageID();

            log.info("Send email " + messageID);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
