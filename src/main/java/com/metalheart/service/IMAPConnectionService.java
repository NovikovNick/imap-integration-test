package com.metalheart.service;

import com.metalheart.converter.MimeMessageToMailConverter;
import com.metalheart.model.IMAPConnection;
import com.metalheart.model.IMAPConnectionData;
import com.metalheart.model.Mail;
import com.sun.mail.imap.IMAPFolder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.event.MessageCountEvent;
import javax.mail.event.MessageCountListener;
import javax.mail.internet.MimeMessage;
import javax.mail.search.FlagTerm;
import javax.mail.search.MessageIDTerm;
import javax.mail.search.NotTerm;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Slf4j
@Component
public class IMAPConnectionService {

    private final boolean debug = false;

    private final int corePoolSize = 4;

    private ScheduledExecutorService taskExecutor = Executors.newScheduledThreadPool(corePoolSize);

    private ReentrantLock lock = new ReentrantLock();

    @Getter
    private AtomicInteger loadedEmailsCount = new AtomicInteger(0);


    @Getter
    private Map<Integer, AtomicInteger> threads = new ConcurrentHashMap<>();

    @Getter
    private BlockingQueue<Mail> mails = new LinkedBlockingDeque<>(100);

    @Autowired
    private MimeMessageToMailConverter converter;

    private Duration cancelIdleInterval = Duration.of(5, ChronoUnit.SECONDS);

    private ScheduledFuture<Boolean> pingTask;


    public void start(IMAPConnectionData data) {

        IMAPConnection connection = connect(data);

        taskExecutor.submit(() -> {

            while (true) {

                for (Mail mail : checkMessages(connection)) {
                    mails.put(mail);
                }

                ping(connection);
                idle(connection);
                cancelPingTask();
            }
        });

        for (int i = 0; i < corePoolSize - 2; i++) {

            AtomicInteger threadCount = new AtomicInteger(0);
            threads.put(i, threadCount);

            int count = i;
            taskExecutor.submit(() -> {

                while (true) {
                    log.info("Try to get email from queue " + count);

                    Mail mail = mails.take();
                    threadCount.incrementAndGet();

                    if (canBeProcessed(mail)) {
                        load(connection, mail);
                        remove(mail);
                    } else {
                        move(mail);
                    }
                }
            });
        }
    }

    public IMAPConnection connect(IMAPConnectionData data) {

        String protocol = "imap";
        var props = new Properties();
        props.put("mail.store.protocol", protocol);
        props.put("mail.imap.timeout", 5000);
        props.put("mail.imap.connectiontimeout", 5000);
        props.put("mail.imap.ssl.enable", "false");

        try {

            Session session = Session.getDefaultInstance(props, null);
            session.setDebug(debug);

            Store store = session.getStore(protocol);

            log.info("Try to connect...");
            store.connect(data.getHost(), data.getPort(), data.getUsername(), data.getPassword());

            IMAPFolder folder = (IMAPFolder) store.getFolder(data.getFolder());
            IMAPConnection connection = new IMAPConnection();
            connection.setFolder(folder);

            log.info("connected");

            return connection;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void stop() {
        cancelPingTask();
        taskExecutor.shutdown();
    }

    private void cancelPingTask() {

        if (Objects.nonNull(pingTask)) {
            log.info("Cancel ping task");
            pingTask.cancel(true);
        }
    }

    public List<Mail> checkMessages(IMAPConnection connection) {

        Optional<List<Mail>> res = openFolder(connection, Folder.READ_WRITE, true, folder -> {

            List<Mail> mails = new ArrayList<>();
            log.info("try to check messages");
            try {


                Message[] messages = folder.search(new NotTerm(new FlagTerm(new Flags(Flags.Flag.SEEN), true)));
                log.info("Has " + messages.length + " messages");

                for (Message message : messages) {

                    Mail mail = converter.convert((MimeMessage) message);
                    mails.add(mail);
                    // message.setFlag(Flags.Flag.SEEN, true);
                }

            } catch (MessagingException e) {
                log.error(e.getMessage(), e);
            }
            return mails;
        });

        return res.isPresent() ? res.get() : Collections.emptyList();
    }

    public void idle(IMAPConnection connection) {

        log.info("Try to start idle task");

        openFolder(connection, Folder.READ_ONLY, false, folder -> {

            MessageCountListener messageCountListener = new MessageCountListener() {
                @Override
                public void messagesAdded(MessageCountEvent e) {
                    Message[] messages = e.getMessages();
                    log.info("Receive " + messages.length + " messages. Cancel idle task");
                    if (messages.length > 0) {
                        // this will return the flow to the idle call
                        messages[0].getFolder().isOpen();
                    }
                }

                @Override
                public void messagesRemoved(MessageCountEvent e) {

                }
            };
            folder.addMessageCountListener(messageCountListener);
            try {
                folder.idle();
            } catch (MessagingException e) {
                throw new RuntimeException(e);
            }

            folder.removeMessageCountListener(messageCountListener);

            return null;
        });
    }

    public void ping(IMAPConnection connection) {

        log.info("Register ping after " + cancelIdleInterval);

        this.pingTask = taskExecutor.schedule(() -> {

            log.info("ping connection");

            try {

                return connection.getFolder().isOpen();

            } catch (Exception e) {
                log.info(e.getMessage(), e);
                return false;
            }
        }, cancelIdleInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    public boolean canBeProcessed(Mail mail) {
        return true;
    }

    public void load(IMAPConnection connection, Mail mail) {
        openFolder(connection, Folder.READ_WRITE, false, folder -> {

            log.info("try to load email");

            try {

                Message[] messages = folder.search(new MessageIDTerm(mail.getMessageId()));
                Message loaded = messages[0];

                Session mailSession = loaded.getSession();

                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                loaded.writeTo(buffer);

                byte[] data = buffer.toByteArray();
                MimeMessage res = new MimeMessage(mailSession, new ByteArrayInputStream(data));

                log.info(loadedEmailsCount.incrementAndGet() + ". loaded " + data.length);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }

            return null;
        });
    }

    public void move(Mail mail) {

    }

    public void remove(Mail mail) {

    }

    public <T> Optional<T> openFolder(IMAPConnection connection,
                                      int mode,
                                      boolean expunge,
                                      Function<IMAPFolder, T> consumer) {

        lock.lock();
        IMAPFolder folder = connection.getFolder();
        try {


            folder.open(mode);

            log.info("Open folder. Try to start operation");

            Instant t0 = Instant.now();
            T res = consumer.apply(folder);
            Instant t1 = Instant.now();

            log.info("Operation elapsed by " + Duration.between(t0, t1));

            return Optional.ofNullable(res);

        } catch (Exception e) {
            log.info(e.getMessage(), e);
        } finally {
            try {
                folder.close(expunge);
            } catch (MessagingException e) {
                log.info(e.getMessage(), e);
            }
            lock.unlock();
        }

        return Optional.empty();
    }
}
