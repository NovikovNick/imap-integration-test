package com.metalheart;

import com.metalheart.integration.transformer.MimeMessageToMailTransformer;
import java.net.URLEncoder;
import java.util.Properties;
import javax.mail.Flags;
import javax.mail.search.FlagTerm;
import javax.mail.search.NotTerm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.integration.mail.ImapMailReceiver;
import org.springframework.integration.mail.dsl.Mail;
import org.springframework.messaging.MessageChannel;

import static java.nio.charset.StandardCharsets.UTF_8;

@Configuration
public class AppConfiguration {

    public static final String MAIL_CHANNEL = "mail-channel";

    @Autowired
    private AppProperties props;

    @Autowired
    private MimeMessageToMailTransformer mimeMessageToMail;

    @Bean(name = MAIL_CHANNEL)
    public MessageChannel grabbingChannel() {
        return MessageChannels.queue(MAIL_CHANNEL).get();
    }

    @Bean
    public IntegrationFlow mailFlow() {

        String user = URLEncoder.encode(props.getUsername(), UTF_8);
        String pass = URLEncoder.encode(props.getPassword(), UTF_8);
        String url =  String.format("%s://%s:%s@%s:%d/%s", props.getProtocol(),
            user, pass, props.getHost(), props.getPort(), props.getFolder());

        Properties props = new Properties();
        props.put("mail.debug", "true");

        ImapMailReceiver receiver = new ImapMailReceiver(url);
        receiver.setJavaMailProperties(props);
        receiver.setShouldDeleteMessages(false);
        receiver.setShouldMarkMessagesAsRead(true);
        receiver.setSearchTermStrategy((s, f) -> new NotTerm(new FlagTerm(new Flags(Flags.Flag.SEEN), true)));

        return IntegrationFlows.from(Mail.imapIdleAdapter(receiver))
            .transform(mimeMessageToMail)
            .channel(MAIL_CHANNEL)
            .get();
    }
}
