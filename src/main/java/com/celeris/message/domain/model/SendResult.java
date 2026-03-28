package com.celeris.message.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendResult {

    private boolean success;
    private String messageId;
    private String errorMsg;

    public static SendResult ok(String messageId) {
        return SendResult.builder().success(true).messageId(messageId).build();
    }

    public static SendResult fail(String errorMsg) {
        return SendResult.builder().success(false).errorMsg(errorMsg).build();
    }
}
