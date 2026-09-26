package com.cybersixseven.devicecommandservice.sqs;

import com.cybersixseven.devicecommandservice.command.DeviceCommandMessage;
import com.cybersixseven.devicecommandservice.command.DeviceEventStates;
import com.cybersixseven.devicecommandservice.dynamodb.DeviceEventItem;
import com.cybersixseven.devicecommandservice.dynamodb.DeviceEventStore;
import com.cybersixseven.devicecommandservice.mqtt.MqttCommandPublisher;
import com.cybersixseven.devicecommandservice.observability.Correlation;
import com.cybersixseven.devicecommandservice.observability.PipelineMeters;
import com.cybersixseven.devicecommandservice.reward.RewardClient;
import java.time.Instant;
import java.time.format.DateTimeParseException;
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
  private final RewardClient rewardClient;
  private final PipelineMeters meters;
  private final String queueUrl;

  public DeviceCommandProcessor(
      SqsClient sqsClient,
      DeviceEventStore deviceEventStore,
      ObjectProvider<MqttCommandPublisher> mqttPublisher,
      ObjectMapper objectMapper,
      RewardClient rewardClient,
      PipelineMeters meters,
      @Value("${app.aws.device-commands-queue-url}") String queueUrl) {
    this.sqsClient = sqsClient;
    this.deviceEventStore = deviceEventStore;
    this.mqttPublisher = mqttPublisher;
    this.objectMapper = objectMapper;
    this.rewardClient = rewardClient;
    this.meters = meters;
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
        .messageAttributeNames("All")
        .build());
    for (Message message : response.messages()) {
      try {
        extendVisibility(message);
        handle(message);
      } catch (RuntimeException ex) {
        meters.queueFailure();
        log.error("device command failed messageId={}", message.messageId(), ex);
      }
    }
  }

  public void handle(Message message) {
    Instant snsPublishedAt = snsPublishedAt(message);
    DeviceCommandMessage command = applyReward(objectMapper.readValue(message.body(), DeviceCommandMessage.class));
    Correlation.put(command.submissionId(), command.commandId(), command.deviceId());
    try {
      DeviceEventItem existing = deviceEventStore.find(command.commandId().toString()).orElse(null);
      if (existing != null && DeviceEventStates.isComplete(existing.getState())) {
        meters.queueRedelivery();
        log.info("sqs redelivery already complete state={}", existing.getState());
        delete(message);
        return;
      }

      deviceEventStore.createPendingIfAbsent(command);
      mqttPublisher.getObject().publishCommand(command);
      meters.snsToMqtt(snsPublishedAt);
      boolean sent = deviceEventStore.markSent(command.commandId().toString());
      if (!sent) {
        throw new IllegalStateException("refusing to delete SQS; SENT transition failed commandId="
            + command.commandId());
      }
      delete(message);
      meters.queueSuccess();
      log.info("sqs processed");
    } finally {
      Correlation.clear();
    }
  }

  private DeviceCommandMessage applyReward(DeviceCommandMessage command) {
    if (command.score() == null || command.totalQuestions() == null) {
      return command.withIntensity(DeviceCommandMessage.clampIntensity(command.intensity()));
    }
    int intensity = rewardClient
        .selectIntensity(command.score(), command.totalQuestions())
        .orElseGet(() -> {
          meters.rewardFallback();
          return DeviceCommandMessage.DEFAULT_INTENSITY;
        });
    return command.withIntensity(DeviceCommandMessage.clampIntensity(intensity));
  }

  private static Instant snsPublishedAt(Message message) {
    if (!message.hasMessageAttributes()) {
      return null;
    }
    var attribute = message.messageAttributes().get("snsPublishedAt");
    if (attribute == null || attribute.stringValue() == null || attribute.stringValue().isBlank()) {
      return null;
    }
    try {
      return Instant.parse(attribute.stringValue());
    } catch (DateTimeParseException ex) {
      return null;
    }
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
