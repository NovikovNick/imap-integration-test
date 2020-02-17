package com.metalheart;

import com.metalheart.model.IMAPConnectionData;
import com.metalheart.model.Mail;
import com.metalheart.service.IMAPService;
import com.metalheart.service.MailService;
import com.metalheart.testcontainer.MailServerDockerComposeContainer;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.MessagingException;
import javax.mail.search.FlagTerm;
import javax.mail.search.MessageIDTerm;
import javax.mail.search.NotTerm;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.ClassRule;
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
import org.springframework.util.CollectionUtils;
import org.testcontainers.containers.DockerComposeContainer;

import static com.metalheart.AppConfiguration.MAIL_CHANNEL;
import static java.util.stream.Collectors.toList;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = App.class, properties = "spring.main.allow-bean-definition-overriding=true")
@ActiveProfiles("test")
@ContextConfiguration(classes = TestConfiguration.class)
public class LocalImapTest {

    @ClassRule
    public static DockerComposeContainer ENVIRONMENT = MailServerDockerComposeContainer.getInstance();

    @Autowired
    private MailService mailService;

    @Autowired
    private IMAPService imapService;

    @Autowired
    @Qualifier(MAIL_CHANNEL)
    private PollableChannel mailChannel;

    @Test
    public void test() {



        IMAPConnectionData connectionData = getConnectionData();
        String flowId = imapService.registerIntegrationFlow(connectionData);
        imapService.startIntegrationFlow(flowId);

        mailService.send("user@domain.com", "1", "test 1");
        mailService.send("user@domain.com", "2", "test 2");

        Message message = mailChannel.receive(10000);

        Mail mail = (Mail) message.getPayload();

        Assert.assertEquals("user@domain.com", mail.getTo().get(0));
        Assert.assertEquals("1", mail.getSubject());
    }

    @Test
    public void test2() throws InterruptedException {

        for (int i = 0; i < 5; i++) {

            String messageId = mailService.send("user@domain.com", "test", "test");

            TimeUnit.SECONDS.sleep(1);

            try (IMAPFolder folder = imapService.getFolder(getConnectionData())) {

                folder.open(Folder.READ_ONLY);

                var messages1 = folder.search(new NotTerm(new FlagTerm(new Flags(Flags.Flag.SEEN), true)));
                var messages2 = folder.search(new MessageIDTerm(messageId));

                log.info("1. " + toMessageIDList(messages1));
                log.info("2. " + toMessageIDList(messages2));

                // folder.fetch(null, FetchProfile.Item.SIZE, FetchProfile.Item.SIZE);

            } catch (MessagingException e) {
                log.error(e.getMessage(), e);
            }

            log.info("\n\n\n");

        }

    }

    public IMAPConnectionData getConnectionData() {
        IMAPConnectionData data = new IMAPConnectionData();
        data.setUsername("user@domain.com");
        data.setPassword("password");
        data.setProtocol("imap");
        data.setHost(MailServerDockerComposeContainer.getContainerIp());
        data.setPort(MailServerDockerComposeContainer.getImapPort());
        data.setFolder("inbox");
        return data;
    }

    private List<IMAPMail> toMessageIDList(javax.mail.Message[] messages) {
        List<javax.mail.Message> list = CollectionUtils.arrayToList(messages);
        return list.stream()
            .map(msg -> {

                IMAPMail.IMAPMailBuilder builder = IMAPMail.builder();

                try {

                    IMAPMessage imapMessage = (IMAPMessage) msg;

                    return builder
                        .messageId(imapMessage.getMessageID())
                        .build();
                } catch (MessagingException e) {

                    return builder.messageId(e.getMessage()).build();
                }
            })
            .collect(toList());
    }

    @Data
    @Builder
    public static class IMAPMail {
        private String messageId;
    }
}
