package com.metalheart;

import com.metalheart.model.IMAPConnectionData;
import com.metalheart.model.Mail;
import com.metalheart.service.IMAPConnectionService;
import com.metalheart.service.IMAPService;
import com.metalheart.service.SMTPService;
import com.metalheart.testcontainer.MailServerDockerComposeContainer;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.mail.Folder;
import javax.mail.MessagingException;
import javax.mail.search.MessageIDTerm;
import lombok.Builder;
import lombok.Data;
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
import org.springframework.util.CollectionUtils;

import static com.metalheart.AppConfiguration.MAIL_CHANNEL;
import static java.util.stream.Collectors.toList;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = App.class, properties = "spring.main.allow-bean-definition-overriding=true")
@ActiveProfiles("test")
@ContextConfiguration(classes = TestConfiguration.class)
public class LocalImapTest {

    /*@ClassRule
    public static DockerComposeContainer ENVIRONMENT = MailServerDockerComposeContainer.getInstance();*/

    @Autowired
    private SMTPService mailService;

    @Autowired
    private IMAPService imapService;

    @Autowired
    @Qualifier(MAIL_CHANNEL)
    private PollableChannel mailChannel;

    @Autowired
    private IMAPConnectionService imapConnectionService;

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
    public void test2() throws Exception {

        List<String> messageIds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            messageIds.add(mailService.send("user@domain.com", "test2", "test_" + i));
        }

        TimeUnit.SECONDS.sleep(2);

        IMAPFolder folder = imapService.getFolder(getConnectionData());
        folder.open(Folder.READ_ONLY);
        javax.mail.Message[] messages = folder.search(new MessageIDTerm(messageIds.get(9)));

        Assert.assertEquals(1, messages.length);

        log.info("Try to get content");
        Object content = messages[0].getContent();
        Assert.assertNotNull(content);
        folder.close();
    }


    @Test
    public void test3() throws Exception {

        List<String> messageIds = new ArrayList<>();

        TimeUnit.SECONDS.sleep(2);

        imapConnectionService.start(getConnectionData());

        //mailService.send("metalheart.bot1@gmail.com", "test3", 1000);

        TimeUnit.SECONDS.sleep(20);
        imapConnectionService.stop();

        log.info(". Checked emails: " + imapConnectionService.getMails().size());
        log.info(". Loaded emails: " + imapConnectionService.getLoadedEmailsCount());
        log.info(". Threads: " + imapConnectionService.getThreads());

    }


    public IMAPConnectionData getConnectionData() {
        IMAPConnectionData data = new IMAPConnectionData();
        data.setUsername("user@domain.com");
        data.setPassword("password");
        data.setProtocol("imap");
        data.setHost("localhost");
        data.setPort(143);
        data.setFolder("inbox");
        return data;
    }

    public IMAPConnectionData getConnectionRealData() {
        IMAPConnectionData data = new IMAPConnectionData();
        data.setUsername("metalheart.bot1@gmail.com");
        //data.setPassword
        data.setProtocol("imap");
        data.setHost("imap.gmail.com");
        data.setPort(993);
        data.setFolder("INBOX");
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
