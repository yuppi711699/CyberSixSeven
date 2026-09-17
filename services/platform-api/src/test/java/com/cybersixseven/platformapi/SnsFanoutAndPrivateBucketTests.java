package com.cybersixseven.platformapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.localstack.LocalStackContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetPublicAccessBlockRequest;
import software.amazon.awssdk.services.s3.model.PublicAccessBlockConfiguration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutPublicAccessBlockRequest;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sns.model.ListSubscriptionsByTopicRequest;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sns.model.Subscription;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;

@Testcontainers
class SnsFanoutAndPrivateBucketTests {

    @Container
    static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer("localstack/localstack:4.7").withServices("sns", "sqs", "s3");

    private static SnsClient sns;
    private static SqsClient sqs;
    private static S3Client s3;
    private static String topicArn;
    private static String deviceQueueUrl;
    private static String modelQueueUrl;

    @BeforeAll
    static void bootstrap() {
        var creds = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey()));
        Region region = Region.of(LOCALSTACK.getRegion());
        sns = SnsClient.builder()
                .endpointOverride(LOCALSTACK.getEndpoint())
                .region(region)
                .credentialsProvider(creds)
                .build();
        sqs = SqsClient.builder()
                .endpointOverride(LOCALSTACK.getEndpoint())
                .region(region)
                .credentialsProvider(creds)
                .build();
        s3 = S3Client.builder()
                .endpointOverride(LOCALSTACK.getEndpoint())
                .region(region)
                .credentialsProvider(creds)
                .forcePathStyle(true)
                .build();

        topicArn = sns.createTopic(CreateTopicRequest.builder().name("submission-events").build())
                .topicArn();
        deviceQueueUrl = subscribedQueue("device-commands", "device-commands-dlq", "60");
        modelQueueUrl = subscribedQueue("model-gen-jobs", "model-gen-jobs-dlq", "300");

        s3.createBucket(CreateBucketRequest.builder().bucket("cybersixseven-accessories").build());
        s3.putPublicAccessBlock(PutPublicAccessBlockRequest.builder()
                .bucket("cybersixseven-accessories")
                .publicAccessBlockConfiguration(PublicAccessBlockConfiguration.builder()
                        .blockPublicAcls(true)
                        .ignorePublicAcls(true)
                        .blockPublicPolicy(true)
                        .restrictPublicBuckets(true)
                        .build())
                .build());
    }

    @Test
    void bothSubscriptionsAreRawAndDlqBacked() {
        List<Subscription> subscriptions = sns.listSubscriptionsByTopic(
                        ListSubscriptionsByTopicRequest.builder().topicArn(topicArn).build())
                .subscriptions();
        assertEquals(2, subscriptions.size());
        for (Subscription subscription : subscriptions) {
            assertEquals("true", rawDelivery(subscription.subscriptionArn()));
        }

        assertEquals("5", redriveMaxReceive(deviceQueueUrl));
        assertEquals("5", redriveMaxReceive(modelQueueUrl));
        assertEquals("60", visibility(deviceQueueUrl));
        assertEquals("300", visibility(modelQueueUrl));
        assertTrue(hasDlq(deviceQueueUrl));
        assertTrue(hasDlq(modelQueueUrl));
    }

    @Test
    void onePublishReachesBothQueuesAsRawJsonIndependently() {
        String commandId = UUID.randomUUID().toString();
        String json =
                "{\"commandId\":\"%s\",\"submissionId\":\"s\",\"deviceId\":\"esp32-dev-001\",\"event\":\"correct\",\"intensity\":3}"
                        .formatted(commandId);
        sns.publish(PublishRequest.builder().topicArn(topicArn).message(json).build());

        String deviceBody = receiveBody(deviceQueueUrl);
        String modelBody = receiveBody(modelQueueUrl);
        assertEquals(json, deviceBody);
        assertEquals(json, modelBody);
        assertFalse(deviceBody.contains("\"Type\":\"Notification\""));
        assertFalse(modelBody.contains("\"Type\":\"Notification\""));
    }

    @Test
    void accessoriesBucketBlocksPublicAccessAndStoresAPrivateObject() throws Exception {
        var block = s3.getPublicAccessBlock(GetPublicAccessBlockRequest.builder()
                        .bucket("cybersixseven-accessories")
                        .build())
                .publicAccessBlockConfiguration();
        assertTrue(block.blockPublicAcls());
        assertTrue(block.ignorePublicAcls());
        assertTrue(block.blockPublicPolicy());
        assertTrue(block.restrictPublicBuckets());

        UUID submissionId = UUID.randomUUID();
        String key = "accessories/" + submissionId + "/reward.stl";
        s3.putObject(
                PutObjectRequest.builder()
                        .bucket("cybersixseven-accessories")
                        .key(key)
                        .build(),
                software.amazon.awssdk.core.sync.RequestBody.fromBytes("solid".getBytes()));
        try (var object = s3.getObject(GetObjectRequest.builder()
                .bucket("cybersixseven-accessories")
                .key(key)
                .build())) {
            assertTrue(object.response().contentLength() > 0);
        }
    }

    private static String subscribedQueue(String queueName, String dlqName, String visibility) {
        String dlqUrl = sqs.createQueue(CreateQueueRequest.builder().queueName(dlqName).build())
                .queueUrl();
        String dlqArn = sqs.getQueueAttributes(GetQueueAttributesRequest.builder()
                        .queueUrl(dlqUrl)
                        .attributeNames(QueueAttributeName.QUEUE_ARN)
                        .build())
                .attributes()
                .get(QueueAttributeName.QUEUE_ARN);
        String redrive = "{\"deadLetterTargetArn\":\"%s\",\"maxReceiveCount\":\"5\"}".formatted(dlqArn);
        String queueUrl = sqs.createQueue(CreateQueueRequest.builder()
                        .queueName(queueName)
                        .attributes(Map.of(
                                QueueAttributeName.VISIBILITY_TIMEOUT,
                                visibility,
                                QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS,
                                "1",
                                QueueAttributeName.REDRIVE_POLICY,
                                redrive))
                        .build())
                .queueUrl();
        String queueArn = sqs.getQueueAttributes(GetQueueAttributesRequest.builder()
                        .queueUrl(queueUrl)
                        .attributeNames(QueueAttributeName.QUEUE_ARN)
                        .build())
                .attributes()
                .get(QueueAttributeName.QUEUE_ARN);
        sqs.setQueueAttributes(SetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributes(Map.of(
                        QueueAttributeName.POLICY,
                        "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":\"*\",\"Action\":\"sqs:SendMessage\",\"Resource\":\"*\"}]}"))
                .build());
        sns.subscribe(SubscribeRequest.builder()
                .topicArn(topicArn)
                .protocol("sqs")
                .endpoint(queueArn)
                .attributes(Map.of("RawMessageDelivery", "true"))
                .build());
        return queueUrl;
    }

    private static String rawDelivery(String subscriptionArn) {
        return sns.getSubscriptionAttributes(builder -> builder.subscriptionArn(subscriptionArn))
                .attributes()
                .getOrDefault("RawMessageDelivery", "false");
    }

    private static String redriveMaxReceive(String queueUrl) {
        String policy = sqs.getQueueAttributes(GetQueueAttributesRequest.builder()
                        .queueUrl(queueUrl)
                        .attributeNames(QueueAttributeName.REDRIVE_POLICY)
                        .build())
                .attributes()
                .get(QueueAttributeName.REDRIVE_POLICY);
        assertTrue(policy.contains("\"maxReceiveCount\":\"5\"") || policy.contains("\"maxReceiveCount\": \"5\""), policy);
        return "5";
    }

    private static String visibility(String queueUrl) {
        return sqs.getQueueAttributes(GetQueueAttributesRequest.builder()
                        .queueUrl(queueUrl)
                        .attributeNames(QueueAttributeName.VISIBILITY_TIMEOUT)
                        .build())
                .attributes()
                .get(QueueAttributeName.VISIBILITY_TIMEOUT);
    }

    private static boolean hasDlq(String queueUrl) {
        String policy = sqs.getQueueAttributes(GetQueueAttributesRequest.builder()
                        .queueUrl(queueUrl)
                        .attributeNames(QueueAttributeName.REDRIVE_POLICY)
                        .build())
                .attributes()
                .get(QueueAttributeName.REDRIVE_POLICY);
        return policy.contains("deadLetterTargetArn");
    }

    private static String receiveBody(String queueUrl) {
        var messages = sqs.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .waitTimeSeconds(10)
                        .maxNumberOfMessages(1)
                        .build())
                .messages();
        assertEquals(1, messages.size());
        return messages.get(0).body();
    }
}
