package com.cybersixseven.devicecommandservice.dynamodb;

import com.cybersixseven.devicecommandservice.command.DeviceCommandMessage;
import com.cybersixseven.devicecommandservice.command.DeviceEventStates;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

@Component
public class DynamoDbDeviceEventStore implements DeviceEventStore {

  private final DynamoDbTable<DeviceEventItem> table;

  public DynamoDbDeviceEventStore(DynamoDbTable<DeviceEventItem> table) {
    this.table = table;
  }

  @Override
  public Optional<DeviceEventItem> find(String commandId) {
    return Optional.ofNullable(table.getItem(Key.builder().partitionValue(commandId).build()));
  }

  @Override
  public DeviceEventItem createPendingIfAbsent(DeviceCommandMessage command) {
    Instant now = Instant.now();
    DeviceEventItem item = new DeviceEventItem();
    item.setCommandId(command.commandId().toString());
    item.setSubmissionId(command.submissionId().toString());
    item.setDeviceId(command.deviceId());
    item.setEvent(command.event());
    item.setIntensity(command.intensity());
    item.setAttempts(0);
    item.setState(DeviceEventStates.PENDING);
    item.setCreatedAt(DeviceEventItem.iso(now));
    item.setUpdatedAt(DeviceEventItem.iso(now));
    try {
      table.putItem(PutItemEnhancedRequest.builder(DeviceEventItem.class)
          .item(item)
          .conditionExpression(Expression.builder()
              .expression("attribute_not_exists(commandId)")
              .build())
          .build());
      return item;
    } catch (ConditionalCheckFailedException ignored) {
      return find(command.commandId().toString()).orElseThrow();
    }
  }

  @Override
  public boolean markSent(String commandId) {
    DeviceEventItem current = find(commandId).orElseThrow();
    if (DeviceEventStates.isComplete(current.getState())) {
      return true;
    }
    Instant now = Instant.now();
    current.setState(DeviceEventStates.SENT);
    current.setSentAt(DeviceEventItem.iso(now));
    current.setUpdatedAt(DeviceEventItem.iso(now));
    current.setAttempts(current.getAttempts() == null ? 1 : current.getAttempts() + 1);
    try {
      table.updateItem(UpdateItemEnhancedRequest.builder(DeviceEventItem.class)
          .item(current)
          .conditionExpression(Expression.builder()
              .expression("#st = :pending")
              .expressionNames(Map.of("#st", "state"))
              .expressionValues(Map.of(":pending", AttributeValue.fromS(DeviceEventStates.PENDING)))
              .build())
          .build());
      return true;
    } catch (ConditionalCheckFailedException ignored) {
      DeviceEventItem raced = find(commandId).orElseThrow();
      return DeviceEventStates.ACKNOWLEDGED.equals(raced.getState())
          || DeviceEventStates.SENT.equals(raced.getState());
    }
  }

  @Override
  public boolean markAcknowledged(String commandId) {
    DeviceEventItem current = find(commandId).orElse(null);
    if (current == null) {
      return false;
    }
    if (DeviceEventStates.ACKNOWLEDGED.equals(current.getState())) {
      return true;
    }
    if (!DeviceEventStates.canAcknowledge(current.getState())) {
      return false;
    }
    Instant now = Instant.now();
    current.setState(DeviceEventStates.ACKNOWLEDGED);
    current.setAcknowledgedAt(DeviceEventItem.iso(now));
    current.setUpdatedAt(DeviceEventItem.iso(now));
    try {
      table.updateItem(UpdateItemEnhancedRequest.builder(DeviceEventItem.class)
          .item(current)
          .conditionExpression(Expression.builder()
              .expression("#st = :pending OR #st = :sent")
              .expressionNames(Map.of("#st", "state"))
              .expressionValues(Map.of(
                  ":pending", AttributeValue.fromS(DeviceEventStates.PENDING),
                  ":sent", AttributeValue.fromS(DeviceEventStates.SENT)))
              .build())
          .build());
      return true;
    } catch (ConditionalCheckFailedException ignored) {
      DeviceEventItem raced = find(commandId).orElseThrow();
      return DeviceEventStates.ACKNOWLEDGED.equals(raced.getState());
    }
  }
}
