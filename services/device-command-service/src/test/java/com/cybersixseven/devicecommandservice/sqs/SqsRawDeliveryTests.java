package com.cybersixseven.devicecommandservice.sqs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.localstack.LocalStackContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;

@Testcontainers
class SqsRawDeliveryTests {

  @Container
  static final LocalStackContainer LOCALSTACK =
      new LocalStackContainer("localstack/localstack:4.7").withServices("sns", "sqs");

  private static SnsClient sns;
  private static SqsClient sqs;
  private static String queueUrl;
  private static String topicArn;

  @BeforeAll
  static void topicAndQueue() {
    var creds = StaticCredentialsProvider.create(
        AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey()));
    sns = SnsClient.builder()
        .endpointOverride(LOCALSTACK.getEndpoint())
        .region(Region.of(LOCALSTACK.getRegion()))
        .credentialsProvider(creds)
        .build();
    sqs = SqsClient.builder()
        .endpointOverride(LOCALSTACK.getEndpoint())
        .region(Region.of(LOCALSTACK.getRegion()))
        .credentialsProvider(creds)
        .build();
    topicArn = sns.createTopic(CreateTopicRequest.builder().name("submission-events").build())
        .topicArn();
    queueUrl = sqs.createQueue(CreateQueueRequest.builder().queueName("device-commands").build())
        .queueUrl();
    String queueArn = sqs.getQueueAttributes(GetQueueAttributesRequest.builder()
            .queueUrl(queueUrl)
            .attributeNames(QueueAttributeName.QUEUE_ARN)
            .build())
        .attributes()
        .get(QueueAttributeName.QUEUE_ARN);
    sqs.setQueueAttributes(SetQueueAttributesRequest.builder()
        .queueUrl(queueUrl)
        .attributes(java.util.Map.of(
            QueueAttributeName.POLICY,
            "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":\"*\",\"Action\":\"sqs:SendMessage\",\"Resource\":\"*\"}]}"))
        .build());
    sns.subscribe(SubscribeRequest.builder()
        .topicArn(topicArn)
        .protocol("sqs")
        .endpoint(queueArn)
        .attributes(java.util.Map.of("RawMessageDelivery", "true"))
        .build());
  }

  @Test
  void rawDeliveryBodyIsTheEventJsonNotAnSnsEnvelope() {
    String commandId = UUID.randomUUID().toString();
    String json =
        "{\"commandId\":\"%s\",\"submissionId\":\"s\",\"deviceId\":\"esp32-dev-001\",\"event\":\"correct\",\"intensity\":3}"
            .formatted(commandId);
    sns.publish(PublishRequest.builder().topicArn(topicArn).message(json).build());

    var received = sqs.receiveMessage(ReceiveMessageRequest.builder()
            .queueUrl(queueUrl)
            .waitTimeSeconds(10)
            .maxNumberOfMessages(1)
            .build())
        .messages();
    assertEquals(1, received.size());
    assertEquals(json, received.get(0).body());
    assertFalse(received.get(0).body().contains("\"Type\":\"Notification\""));
  }
}
