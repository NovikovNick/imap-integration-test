package com.metalheart.testcontainer;

import java.io.File;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class MailServerDockerComposeContainer extends DockerComposeContainer<MailServerDockerComposeContainer> {

    private static final String IMAP_PORT_VAR_NAME = "IMAP_PORT";
    private static final String SMTP_PORT_VAR_NAME = "SMTP_PORT";
    private static final String MAIL_SERVER_IP_VAR_NAME = "MAIL_SERVER_IP";
    private static final String MAIL_SERVICE = "mail_1";
    private static final int IMAP_PORT = 143;
    private static final int SMTP_PORT = 25;

    private static MailServerDockerComposeContainer container;

    public MailServerDockerComposeContainer(File... composeFiles) {
        super(composeFiles);
    }

    public static MailServerDockerComposeContainer getInstance() {
        if (container == null) {
            ClassLoader classLoader = MailServerDockerComposeContainer.class.getClassLoader();
            File dockerCompose = new File(classLoader.getResource("docker-compose.yml").getFile());

            container = new MailServerDockerComposeContainer(dockerCompose)
                .waitingFor(MAIL_SERVICE, Wait.forListeningPort())
                .withExposedService(MAIL_SERVICE, IMAP_PORT)
                .withExposedService(MAIL_SERVICE, SMTP_PORT);
        }
        return container;
    }

    @Override
    public void start() {
        super.start();
        String externalIMAPPort = String.valueOf(container.getServicePort(MAIL_SERVICE, IMAP_PORT));
        String externalSMTPPort = String.valueOf(container.getServicePort(MAIL_SERVICE, SMTP_PORT));
        String ip = String.valueOf(container.getServiceHost(MAIL_SERVICE, SMTP_PORT));

        System.setProperty(IMAP_PORT_VAR_NAME, externalIMAPPort);
        System.setProperty(SMTP_PORT_VAR_NAME, externalSMTPPort);
        System.setProperty(MAIL_SERVER_IP_VAR_NAME, ip);
    }

    @Override
    public void stop() {
        //do nothing, JVM handles shut down
    }

    public static Integer getImapPort() {

        Integer imapPort = Integer.valueOf(System.getProperty(IMAP_PORT_VAR_NAME));
        return imapPort;
    }

    public static Integer getSmtpPort() {

        Integer imapPort = Integer.valueOf(System.getProperty(SMTP_PORT_VAR_NAME));
        return imapPort;
    }

    public static String getContainerIp() {

        String containerIp = System.getProperty(MAIL_SERVER_IP_VAR_NAME);
        return containerIp;
    }
}
