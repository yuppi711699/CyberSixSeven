package com.cybersixseven.devicecommandservice.config;

import com.cybersixseven.devicecommandservice.dynamodb.DeviceEventItem;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

@Configuration
public class AwsClientConfig {

  @Bean
  SqsClient sqsClient(
      @Value("${app.aws.region}") String region,
      @Value("${app.aws.endpoint}") String endpoint,
      @Value("${app.aws.access-key}") String accessKey,
      @Value("${app.aws.secret-key}") String secretKey) {
    SqsClientBuilder builder = SqsClient.builder()
        .region(Region.of(region))
        .httpClientBuilder(UrlConnectionHttpClient.builder()
            .connectionTimeout(Duration.ofSeconds(2))
            .socketTimeout(Duration.ofSeconds(25)))
        .overrideConfiguration(timeouts());
    applyEndpoint(builder, endpoint, accessKey, secretKey);
    return builder.build();
  }

  @Bean
  DynamoDbClient dynamoDbClient(
      @Value("${app.aws.region}") String region,
      @Value("${app.aws.endpoint}") String endpoint,
      @Value("${app.aws.access-key}") String accessKey,
      @Value("${app.aws.secret-key}") String secretKey) {
    DynamoDbClientBuilder builder = DynamoDbClient.builder()
        .region(Region.of(region))
        .httpClientBuilder(UrlConnectionHttpClient.builder()
            .connectionTimeout(Duration.ofSeconds(2))
            .socketTimeout(Duration.ofSeconds(10)))
        .overrideConfiguration(timeouts());
    applyEndpoint(builder, endpoint, accessKey, secretKey);
    return builder.build();
  }

  @Bean
  DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
    return DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDbClient).build();
  }

  @Bean
  DynamoDbTable<DeviceEventItem> deviceEventsTable(
      DynamoDbEnhancedClient enhancedClient,
      @Value("${app.aws.device-events-table}") String tableName) {
    return enhancedClient.table(tableName, TableSchema.fromBean(DeviceEventItem.class));
  }

  private static ClientOverrideConfiguration timeouts() {
    return ClientOverrideConfiguration.builder()
        .apiCallTimeout(Duration.ofSeconds(30))
        .apiCallAttemptTimeout(Duration.ofSeconds(10))
        .build();
  }

  private static void applyEndpoint(
      SqsClientBuilder builder, String endpoint, String accessKey, String secretKey) {
    if (endpoint != null && !endpoint.isBlank()) {
      builder.endpointOverride(URI.create(endpoint))
          .credentialsProvider(StaticCredentialsProvider.create(
              AwsBasicCredentials.create(accessKey, secretKey)));
    } else {
      builder.credentialsProvider(DefaultCredentialsProvider.create());
    }
  }

  private static void applyEndpoint(
      DynamoDbClientBuilder builder, String endpoint, String accessKey, String secretKey) {
    if (endpoint != null && !endpoint.isBlank()) {
      builder.endpointOverride(URI.create(endpoint))
          .credentialsProvider(StaticCredentialsProvider.create(
              AwsBasicCredentials.create(accessKey, secretKey)));
    } else {
      builder.credentialsProvider(DefaultCredentialsProvider.create());
    }
  }
}
