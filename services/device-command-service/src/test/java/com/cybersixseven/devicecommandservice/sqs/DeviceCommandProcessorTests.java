package com.cybersixseven.devicecommandservice.sqs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cybersixseven.devicecommandservice.command.DeviceCommandMessage;
import com.cybersixseven.devicecommandservice.command.DeviceEventStates;
import com.cybersixseven.devicecommandservice.dynamodb.DeviceEventItem;
import com.cybersixseven.devicecommandservice.dynamodb.DeviceEventStore;
import com.cybersixseven.devicecommandservice.mqtt.MqttCommandPublisher;
import com.cybersixseven.devicecommandservice.reward.RewardClient;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class DeviceCommandProcessorTests {

  @Mock
  private SqsClient sqsClient;

  @Mock
  private DeviceEventStore deviceEventStore;

  @Mock
  private MqttCommandPublisher mqttCommandPublisher;

  @Mock
  private ObjectProvider<MqttCommandPublisher> mqttPublisher;

  @Mock
  private RewardClient rewardClient;

  private DeviceCommandProcessor processor;
  private UUID commandId;
  private UUID submissionId;
  private String body;

  @BeforeEach
  void setUp() {
    lenient().when(mqttPublisher.getObject()).thenReturn(mqttCommandPublisher);
    lenient().when(rewardClient.selectIntensity(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(OptionalInt.empty());
    processor = new DeviceCommandProcessor(
        sqsClient, deviceEventStore, mqttPublisher, new JsonMapper(), rewardClient, "http://queue");
    commandId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    submissionId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    body =
        """
        {"commandId":"%s","submissionId":"%s","deviceId":"esp32-dev-001","event":"correct","intensity":3}
        """
            .formatted(commandId, submissionId);
  }

  @Test
  void firstDeliveryCreatesPendingPublishesAndDeletesAfterSent() {
    when(deviceEventStore.find(commandId.toString())).thenReturn(Optional.empty());
    when(deviceEventStore.markSent(commandId.toString())).thenReturn(true);

    processor.handle(message());

    ArgumentCaptor<DeviceCommandMessage> captor = ArgumentCaptor.forClass(DeviceCommandMessage.class);
    verify(deviceEventStore).createPendingIfAbsent(captor.capture());
    assertEquals(commandId, captor.getValue().commandId());
    verify(mqttCommandPublisher).publishCommand(captor.getValue());
    verify(deviceEventStore).markSent(commandId.toString());
    verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
  }

  @Test
  void redeliveryOfSentSkipsMqttAndDeletes() {
    DeviceEventItem item = new DeviceEventItem();
    item.setState(DeviceEventStates.SENT);
    when(deviceEventStore.find(commandId.toString())).thenReturn(Optional.of(item));

    processor.handle(message());

    verify(mqttCommandPublisher, never()).publishCommand(any());
    verify(deviceEventStore, never()).createPendingIfAbsent(any());
    verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
  }

  @Test
  void redeliveryOfAcknowledgedSkipsMqttAndDeletes() {
    DeviceEventItem item = new DeviceEventItem();
    item.setState(DeviceEventStates.ACKNOWLEDGED);
    when(deviceEventStore.find(commandId.toString())).thenReturn(Optional.of(item));

    processor.handle(message());

    verify(mqttCommandPublisher, never()).publishCommand(any());
    verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
  }

  @Test
  void pendingRedeliveryRetriesMqttWithSameCommandId() {
    DeviceEventItem item = new DeviceEventItem();
    item.setState(DeviceEventStates.PENDING);
    item.setCommandId(commandId.toString());
    when(deviceEventStore.find(commandId.toString())).thenReturn(Optional.of(item));
    when(deviceEventStore.markSent(commandId.toString())).thenReturn(true);

    processor.handle(message());

    ArgumentCaptor<DeviceCommandMessage> captor = ArgumentCaptor.forClass(DeviceCommandMessage.class);
    verify(mqttCommandPublisher).publishCommand(captor.capture());
    assertEquals(commandId, captor.getValue().commandId());
    verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
  }

  @Test
  void mqttFailureDoesNotDeleteMessage() {
    when(deviceEventStore.find(commandId.toString())).thenReturn(Optional.empty());
    org.mockito.Mockito.doThrow(new IllegalStateException("mqtt down"))
        .when(mqttCommandPublisher)
        .publishCommand(any());

    assertThrows(IllegalStateException.class, () -> processor.handle(message()));
    verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
  }

  @Test
  void acknowledgedDuringSentUpdateStillDeletes() {
    when(deviceEventStore.find(commandId.toString())).thenReturn(Optional.empty());
    when(deviceEventStore.markSent(commandId.toString())).thenReturn(true);

    processor.handle(message());

    verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
  }

  @Test
  void sentTransitionFailureDoesNotDeleteMessage() {
    when(deviceEventStore.find(commandId.toString())).thenReturn(Optional.empty());
    when(deviceEventStore.markSent(commandId.toString())).thenReturn(false);

    assertThrows(IllegalStateException.class, () -> processor.handle(message()));
    verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
  }

  @Test
  void pollUsesLongPollingAndSixtySecondVisibility() {
    when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
        .thenReturn(ReceiveMessageResponse.builder().messages(java.util.List.of()).build());

    processor.poll();

    ArgumentCaptor<ReceiveMessageRequest> captor = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
    verify(sqsClient).receiveMessage(captor.capture());
    assertEquals(5, captor.getValue().waitTimeSeconds());
    assertEquals(60, captor.getValue().visibilityTimeout());
    assertEquals(10, captor.getValue().maxNumberOfMessages());
  }

  @Test
  void pollDoesNotThrowWhenHandleFails() {
    when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
        .thenReturn(ReceiveMessageResponse.builder().messages(message()).build());
    when(deviceEventStore.find(commandId.toString())).thenReturn(Optional.empty());
    org.mockito.Mockito.doThrow(new IllegalStateException("mqtt down"))
        .when(mqttCommandPublisher)
        .publishCommand(any());

    processor.poll();

    verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
  }

  @Test
  void pollReturnsImmediatelyWhenQueueUrlIsBlank() {
    DeviceCommandProcessor idle = new DeviceCommandProcessor(
        sqsClient, deviceEventStore, mqttPublisher, new JsonMapper(), rewardClient, "");
    idle.poll();
    verify(sqsClient, never())
        .receiveMessage(any(ReceiveMessageRequest.class));
  }

  @Test
  void rewardSelectionReplacesIntensity() {
    when(deviceEventStore.find(commandId.toString())).thenReturn(Optional.empty());
    when(deviceEventStore.markSent(commandId.toString())).thenReturn(true);
    when(rewardClient.selectIntensity(2, 3)).thenReturn(OptionalInt.of(5));
    body =
        """
        {"commandId":"%s","submissionId":"%s","deviceId":"esp32-dev-001","event":"incorrect","intensity":3,"score":2,"totalQuestions":3}
        """
            .formatted(commandId, submissionId);

    processor.handle(message());

    ArgumentCaptor<DeviceCommandMessage> captor = ArgumentCaptor.forClass(DeviceCommandMessage.class);
    verify(mqttCommandPublisher).publishCommand(captor.capture());
    assertEquals(5, captor.getValue().intensity());
    assertEquals(commandId, captor.getValue().commandId());
  }

  @Test
  void rewardOutageFallsBackToIntensityThree() {
    when(deviceEventStore.find(commandId.toString())).thenReturn(Optional.empty());
    when(deviceEventStore.markSent(commandId.toString())).thenReturn(true);
    when(rewardClient.selectIntensity(1, 3)).thenReturn(OptionalInt.empty());
    body =
        """
        {"commandId":"%s","submissionId":"%s","deviceId":"esp32-dev-001","event":"incorrect","intensity":1,"score":1,"totalQuestions":3}
        """
            .formatted(commandId, submissionId);

    processor.handle(message());

    ArgumentCaptor<DeviceCommandMessage> captor = ArgumentCaptor.forClass(DeviceCommandMessage.class);
    verify(mqttCommandPublisher).publishCommand(captor.capture());
    assertEquals(DeviceCommandMessage.DEFAULT_INTENSITY, captor.getValue().intensity());
    verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
  }

  private Message message() {
    return Message.builder().messageId("m1").receiptHandle("rh").body(body).build();
  }
}
