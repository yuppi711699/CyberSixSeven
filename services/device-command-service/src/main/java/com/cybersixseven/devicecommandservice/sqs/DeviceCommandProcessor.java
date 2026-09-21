package com.cybersixseven.devicecommandservice.sqs;

import com.cybersixseven.devicecommandservice.command.DeviceCommandMessage;
import com.cybersixseven.devicecommandservice.command.DeviceEventStates;
import com.cybersixseven.devicecommandservice.dynamodb.DeviceEventItem;
import com.cybersixseven.devicecommandservice.dynamodb.DeviceEventStore;
import com.cybersixseven.devicecommandservice.mqtt.MqttCommandPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import tools.jackson.databind.ObjectMapper;

@Component
public class DeviceCommandProcessor {

  private static final Logger log = LoggerFactory.getLogger(DeviceCommandProcessor.class);

  private final SqsClient sqsClient;
  private final DeviceEventStore deviceEventStore;
  private final ObjectProvider<MqttCommandPublisher> mqttPublisher;
  private final ObjectMapper objectMapper;
  private final String queueUrl;

  public DeviceCommandProcessor(
      SqsClient sqsClient,
      DeviceEventStore deviceEventStore,
      ObjectProvider<MqttCommandPublisher> mqttPublisher,
      ObjectMapper objectMapper,
      @Value("${app.aws.device-commands-queue-url}") String queueUrl) {
    this.sqsClient = sqsClient;
    this.deviceEventStore = deviceEventStore;
    this.mqttPublisher = mqttPublisher;
    this.objectMapper = objectMapper;
    this.queueUrl = queueUrl;
  }

  @Scheduled(fixedDelay = 1)
  public void poll() {
    if (queueUrl == null || queueUrl.isBlank()) {
      return;
    }
    ReceiveMessageResponse response = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
        .queueUrl(queueUrl)
        // Must stay under AwsClientConfig apiCallAttemptTimeout (10s). Wait=20 +
        // attempt=10 made empty-queue long polls throw ApiCallTimeoutException, so
        // new quiz submits never reached MQTT after the first drain.
        .waitTimeSeconds(5)
        .maxNumberOfMessages(10)
        .visibilityTimeout(60)
        .build());
    for (Message message : response.messages()) {
      try {
        extendVisibility(message);
        handle(message);
      } catch (RuntimeException ex) {
        log.error("device command failed messageId={}", message.messageId(), ex);
      }
    }
  }

  public void handle(Message message) {
    DeviceCommandMessage command = objectMapper.readValue(message.body(), DeviceCommandMessage.class);
    DeviceEventItem existing = deviceEventStore.find(command.commandId().toString()).orElse(null);
    if (existing != null && DeviceEventStates.isComplete(existing.getState())) {
      log.info(
          "sqs redelivery already complete commandId={} submissionId={} state={}",
          command.commandId(),
          command.submissionId(),
          existing.getState());
      delete(message);
      return;
    }

    deviceEventStore.createPendingIfAbsent(command);
    mqttPublisher.getObject().publishCommand(command);
    boolean sent = deviceEventStore.markSent(command.commandId().toString());
    if (!sent) {
      throw new IllegalStateException("refusing to delete SQS; SENT transition failed commandId="
          + command.commandId());
    }
    delete(message);
    log.info(
        "sqs processed commandId={} submissionId={} deviceId={}",
        command.commandId(),
        command.submissionId(),
        command.deviceId());
  }

  private void extendVisibility(Message message) {
    sqsClient.changeMessageVisibility(ChangeMessageVisibilityRequest.builder()
        .queueUrl(queueUrl)
        .receiptHandle(message.receiptHandle())
        .visibilityTimeout(60)
        .build());
  }

  private void delete(Message message) {
    sqsClient.deleteMessage(DeleteMessageRequest.builder()
        .queueUrl(queueUrl)
        .receiptHandle(message.receiptHandle())
        .build());
  }
}
