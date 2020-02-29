package com.metalheart.model;

import com.sun.mail.imap.IMAPFolder;
import lombok.Data;

@Data
public class IMAPConnection {

    private IMAPFolder folder;
}
