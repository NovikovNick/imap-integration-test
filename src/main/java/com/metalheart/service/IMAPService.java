package com.metalheart.service;

import com.metalheart.converter.MimeMessageToMailConverter;
import com.metalheart.model.IMAPConnectionData;
import com.sun.mail.imap.IMAPFolder;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import javax.mail.Flags;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.MimeMessage;
import javax.mail.search.FlagTerm;
import javax.mail.search.NotTerm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.StandardIntegrationFlow;
import org.springframework.integration.dsl.context.IntegrationFlowContext;
import org.springframework.integration.mail.ImapMailReceiver;
import org.springframework.integration.mail.dsl.Mail;
import org.springframework.stereotype.Component;

import static com.metalheart.AppConfiguration.MAIL_CHANNEL;

@Slf4j
@Component
public class IMAPService {

    @Autowired
    private IntegrationFlowContext flowContext;

    @Autowired
    MimeMessageToMailConverter converter;


    public IMAPFolder getFolder(IMAPConnectionData data) {

        String protocol = "imap";
        var props = new Properties();
        props.put("mail.store.protocol", protocol);
        props.put("mail.imap.timeout", 5000);
        props.put("mail.imap.connectiontimeout", 5000);
        props.put("mail.imap.ssl.enable", "false");

        try {

            log.info("Create session");
            Session session = Session.getDefaultInstance(props, null);
            session.setDebug(true);

            log.info("Get store from session");
            Store store = session.getStore(protocol);

            log.info("Try to connect...");
            store.connect(data.getHost(), data.getPort(), data.getUsername(), data.getPassword());

            log.info("Get folder");
            IMAPFolder folder = (IMAPFolder) store.getFolder(data.getFolder());

            log.info("return folder");
            return folder;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String registerIntegrationFlow(IMAPConnectionData data) {

        Properties props = new Properties();
        props.put("mail.debug", "true");

        ImapMailReceiver receiver = new ImapMailReceiver(data.toUrl());
        receiver.setJavaMailProperties(props);
        receiver.setShouldDeleteMessages(false);
        receiver.setShouldMarkMessagesAsRead(true);
        receiver.setAutoCloseFolder(false);
        receiver.setSearchTermStrategy((s, f) -> new NotTerm(new FlagTerm(new Flags(Flags.Flag.SEEN), true)));

        StandardIntegrationFlow integrationFlow = IntegrationFlows.from(Mail.imapIdleAdapter(receiver))
            .<MimeMessage, com.metalheart.model.Mail>transform(m -> converter.convert(m))
            .handle((m, h) -> {
                try {
                    TimeUnit.SECONDS.sleep(3);
                } catch (InterruptedException e) {
                    log.error(e.getMessage());
                }
                return m;
            })
            .channel(MAIL_CHANNEL)
            .get();

        return flowContext.registration(integrationFlow).autoStartup(false).register().getId();
    }

    public void startIntegrationFlow(String flowId) {
        flowContext.getRegistrationById(flowId).start();
    }
}
