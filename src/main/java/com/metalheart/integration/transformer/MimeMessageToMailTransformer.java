package com.metalheart.integration.transformer;

import com.metalheart.model.Mail;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.transformer.GenericTransformer;
import org.springframework.stereotype.Component;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.springframework.util.CollectionUtils.arrayToList;

@Slf4j
@Component
public class MimeMessageToMailTransformer implements GenericTransformer<MimeMessage, Mail> {

    @Override
    public Mail transform(MimeMessage src) {

        Mail dst = new Mail();

        try {
            dst.setFrom(convert(src.getFrom()));
            dst.setTo(convert(src.getRecipients(Message.RecipientType.TO)));

            dst.setSubject(src.getSubject());
            dst.setPlainText(src.getContent() + "");

        } catch (IOException | MessagingException e) {
            log.error(e.getMessage(), e);
        }


        return dst;
    }

    private static List<String> convert(Address[] addresses) {

        if (Objects.isNull(addresses)) {
            return emptyList();
        }

        if (addresses.length == 0) {
            return emptyList();
        }

        List<Address> list = arrayToList(addresses);
        return list.stream()
            .map(address -> getAddress(address))
            .collect(toList());
    }

    public static String getAddress(Address address) {
        return address instanceof InternetAddress ? ((InternetAddress) address).getAddress() : address.toString();
    }
}
