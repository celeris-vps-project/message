package com.celeris.message.grpc;

import com.celeris.message.domain.enums.ChannelType;
import com.celeris.message.domain.model.MessageRecord;
import com.celeris.message.domain.model.MessageRequest;
import com.celeris.message.domain.model.SendResult;
import com.celeris.message.grpc.proto.*;
import com.celeris.message.service.MessageService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.HashMap;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class MessageGrpcServiceImpl extends MessageServiceGrpc.MessageServiceImplBase {

    private final MessageService messageService;

    @Override
    public void send(SendRequest request, StreamObserver<SendResponse> responseObserver) {
        log.info("gRPC Send: channel={}, recipient={}, bizId={}", request.getChannel(), request.getRecipient(), request.getBizId());

        MessageRequest msgRequest = toMessageRequest(request);
        SendResult result = messageService.send(msgRequest);

        SendResponse response = SendResponse.newBuilder()
                .setSuccess(result.isSuccess())
                .setMessageId(result.getMessageId() != null ? result.getMessageId() : "")
                .setErrorMsg(result.getErrorMsg() != null ? result.getErrorMsg() : "")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void batchSend(BatchSendRequest request, StreamObserver<BatchSendResponse> responseObserver) {
        log.info("gRPC BatchSend: count={}", request.getRequestsCount());

        BatchSendResponse.Builder builder = BatchSendResponse.newBuilder();
        int successCount = 0;
        int failCount = 0;

        for (SendRequest req : request.getRequestsList()) {
            MessageRequest msgRequest = toMessageRequest(req);
            SendResult result = messageService.send(msgRequest);

            SendResponse resp = SendResponse.newBuilder()
                    .setSuccess(result.isSuccess())
                    .setMessageId(result.getMessageId() != null ? result.getMessageId() : "")
                    .setErrorMsg(result.getErrorMsg() != null ? result.getErrorMsg() : "")
                    .build();
            builder.addResults(resp);

            if (result.isSuccess()) {
                successCount++;
            } else {
                failCount++;
            }
        }

        builder.setSuccessCount(successCount);
        builder.setFailCount(failCount);

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void getStatus(StatusRequest request, StreamObserver<StatusResponse> responseObserver) {
        log.info("gRPC GetStatus: bizId={}", request.getBizId());

        MessageRecord record = messageService.getStatus(request.getBizId());

        StatusResponse.Builder builder = StatusResponse.newBuilder()
                .setBizId(request.getBizId());

        if (record != null) {
            builder.setStatus(record.getStatus().name())
                    .setChannel(record.getChannel().name())
                    .setRecipient(record.getRecipient())
                    .setRetryCount(record.getRetryCount())
                    .setErrorMsg(record.getErrorMsg() != null ? record.getErrorMsg() : "")
                    .setCreatedAt(record.getCreatedAt() != null ? record.getCreatedAt().toString() : "")
                    .setSentAt(record.getSentAt() != null ? record.getSentAt().toString() : "");
        } else {
            builder.setStatus("NOT_FOUND");
        }

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    private MessageRequest toMessageRequest(SendRequest request) {
        return MessageRequest.builder()
                .templateCode(request.getTemplateCode().isEmpty() ? null : request.getTemplateCode())
                .channel(parseChannel(request.getChannel()))
                .recipient(request.getRecipient())
                .subject(request.getSubject().isEmpty() ? null : request.getSubject())
                .content(request.getContent().isEmpty() ? null : request.getContent())
                .vars(new HashMap<>(request.getVarsMap()))
                .bizId(request.getBizId().isEmpty() ? null : request.getBizId())
                .build();
    }

    private ChannelType parseChannel(String channel) {
        try {
            return ChannelType.valueOf(channel.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown channel type: " + channel);
        }
    }
}
