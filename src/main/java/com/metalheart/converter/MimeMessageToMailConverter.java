package com.metalheart.converter;


import com.metalheart.model.Mail;
import java.util.List;
import java.util.Objects;
import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.springframework.util.CollectionUtils.arrayToList;

@Slf4j
@Component
public class MimeMessageToMailConverter implements Converter<MimeMessage, Mail> {

    @Override
    public Mail convert(MimeMessage src) {
        Mail dst = new Mail();

        try {

            dst.setMessageId(src.getMessageID());
            dst.setSize(src.getSize());

            dst.setFrom(convert(src.getFrom()));
            dst.setTo(convert(src.getRecipients(Message.RecipientType.TO)));

            dst.setSubject(src.getSubject());

        } catch (MessagingException e) {
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

    private static String getAddress(Address address) {
        return address instanceof InternetAddress ? ((InternetAddress) address).getAddress() : address.toString();
    }
}
