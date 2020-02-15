package com.metalheart.model;

import java.util.List;
import lombok.Data;

@Data
public class Mail {

    private List<String> from;
    private List<String> to;

    private String subject;
    private String plainText;
}
