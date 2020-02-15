package com.metalheart;

import com.metalheart.model.Mail;
import com.metalheart.service.MailService;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.PollableChannel;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import static com.metalheart.AppConfiguration.MAIL_CHANNEL;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = App.class, properties = "spring.main.allow-bean-definition-overriding=true")
@ActiveProfiles("test")
@ContextConfiguration(classes = TestConfiguration.class)
public class LocalImapTest {

    @Autowired
    private MailService mailService;

    @Autowired
    @Qualifier(MAIL_CHANNEL)
    private PollableChannel mailChannel;

    @Test
    public void test() {

        mailService.send("user@domain.com", "test", "test");

        Message message = mailChannel.receive(10000);

        Mail mail = (Mail) message.getPayload();

        Assert.assertEquals("user@domain.com", mail.getTo().get(0));
        Assert.assertEquals("test", mail.getSubject());
    }
}
