package com.cybersixseven.devicecommandservice.dynamodb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersixseven.devicecommandservice.command.DeviceCommandMessage;
import com.cybersixseven.devicecommandservice.command.DeviceEventStates;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.localstack.LocalStackContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

@Testcontainers
class DynamoDbDeviceEventStoreTests {

  @Container
  static final LocalStackContainer LOCALSTACK =
      new LocalStackContainer("localstack/localstack:4.7").withServices("dynamodb");

  private static DynamoDbDeviceEventStore store;

  @BeforeAll
  static void createTable() {
    DynamoDbClient client = DynamoDbClient.builder()
        .endpointOverride(LOCALSTACK.getEndpoint())
        .region(Region.of(LOCALSTACK.getRegion()))
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
        .build();
    client.createTable(CreateTableRequest.builder()
        .tableName("device_events")
        .attributeDefinitions(AttributeDefinition.builder()
            .attributeName("commandId")
            .attributeType(ScalarAttributeType.S)
            .build())
        .keySchema(KeySchemaElement.builder()
            .attributeName("commandId")
            .keyType(KeyType.HASH)
            .build())
        .billingMode(BillingMode.PAY_PER_REQUEST)
        .build());
    store = new DynamoDbDeviceEventStore(DynamoDbEnhancedClient.builder()
        .dynamoDbClient(client)
        .build()
        .table("device_events", TableSchema.fromBean(DeviceEventItem.class)));
  }

  private DeviceCommandMessage command;

  @BeforeEach
  void command() {
    command = new DeviceCommandMessage(
        UUID.randomUUID(), UUID.randomUUID(), "esp32-dev-001", "correct", 3);
  }

  @Test
  void createPendingIsIdempotentOnCommandId() {
    DeviceEventItem first = store.createPendingIfAbsent(command);
    DeviceEventItem second = store.createPendingIfAbsent(command);
    assertEquals(DeviceEventStates.PENDING, first.getState());
    assertEquals(first.getCreatedAt(), second.getCreatedAt());
    assertEquals(first.getCommandId(), second.getCommandId());
  }

  @Test
  void pendingToSentToAcknowledged() {
    store.createPendingIfAbsent(command);
    assertTrue(store.markSent(command.commandId().toString()));
    assertEquals(
        DeviceEventStates.SENT, store.find(command.commandId().toString()).orElseThrow().getState());
    assertTrue(store.markAcknowledged(command.commandId().toString()));
    assertEquals(
        DeviceEventStates.ACKNOWLEDGED,
        store.find(command.commandId().toString()).orElseThrow().getState());
    assertTrue(store.markSent(command.commandId().toString()));
    assertEquals(
        DeviceEventStates.ACKNOWLEDGED,
        store.find(command.commandId().toString()).orElseThrow().getState());
  }

  @Test
  void acknowledgementFromPendingDoesNotRegress() {
    store.createPendingIfAbsent(command);
    assertTrue(store.markAcknowledged(command.commandId().toString()));
    assertTrue(store.markSent(command.commandId().toString()));
    assertEquals(
        DeviceEventStates.ACKNOWLEDGED,
        store.find(command.commandId().toString()).orElseThrow().getState());
  }

  @Test
  void acknowledgementOfUnknownCommandIdIsFalse() {
    assertFalse(store.markAcknowledged(UUID.randomUUID().toString()));
  }
}
