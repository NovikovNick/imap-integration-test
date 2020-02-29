package com.metalheart.model;

import java.util.List;
import lombok.Data;

@Data
public class Mail {

    private String messageId;
    private Integer size;

    private List<String> from;
    private List<String> to;

    private String subject;
}
