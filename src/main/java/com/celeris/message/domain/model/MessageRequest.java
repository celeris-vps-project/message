package com.celeris.message.domain.model;

import com.celeris.message.domain.enums.ChannelType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageRequest {

    private String templateCode;
    private ChannelType channel;
    private String recipient;
    private String subject;
    private String content;
    private Map<String, String> vars;
    private String bizId;
}
