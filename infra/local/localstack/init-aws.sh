#!/usr/bin/env bash
# Re-runnable LocalStack bootstrap for v0.4.
# Owns SNS fan-out, both SQS subscriptions + DLQs, DynamoDB, and the private
# accessories bucket. docker compose down -v && up must restore everything.
set -euo pipefail

REGION="${AWS_REGION:-us-east-1}"
TOPIC_NAME="submission-events"
TABLE_NAME="device_events"
BUCKET_NAME="cybersixseven-accessories"

# Nested SQS attribute values (RedrivePolicy/Policy) cannot be passed as
# comma-separated Key=Value shorthand — AWS CLI splits on the inner quotes.
write_queue_attributes() {
  python3 - "$1" "$2" "$3" "$4" "$5" <<'PY'
import json, sys

path, visibility, dlq_arn, queue_arn, topic_arn = sys.argv[1:]
attrs = {
    "VisibilityTimeout": visibility,
    "ReceiveMessageWaitTimeSeconds": "20",
    "RedrivePolicy": json.dumps(
        {"deadLetterTargetArn": dlq_arn, "maxReceiveCount": "5"}
    ),
    "Policy": json.dumps(
        {
            "Version": "2012-10-17",
            "Statement": [
                {
                    "Effect": "Allow",
                    "Principal": {"Service": "sns.amazonaws.com"},
                    "Action": "sqs:SendMessage",
                    "Resource": queue_arn,
                    "Condition": {"ArnEquals": {"aws:SourceArn": topic_arn}},
                }
            ],
        }
    ),
}
with open(path, "w", encoding="utf-8") as handle:
    json.dump(attrs, handle)
PY
}

awslocal sns create-topic --name "$TOPIC_NAME" --region "$REGION" >/dev/null
TOPIC_ARN="$(awslocal sns create-topic --name "$TOPIC_NAME" --region "$REGION" --query TopicArn --output text)"

subscribe_queue() {
  local queue_name="$1"
  local dlq_name="$2"
  local visibility="$3"

  awslocal sqs create-queue --queue-name "$dlq_name" --region "$REGION" >/dev/null
  local dlq_url
  dlq_url="$(awslocal sqs get-queue-url --queue-name "$dlq_name" --region "$REGION" --query QueueUrl --output text)"
  local dlq_arn
  dlq_arn="$(awslocal sqs get-queue-attributes --queue-url "$dlq_url" --attribute-names QueueArn --region "$REGION" --query 'Attributes.QueueArn' --output text)"

  awslocal sqs create-queue --queue-name "$queue_name" --region "$REGION" >/dev/null
  local queue_url
  queue_url="$(awslocal sqs get-queue-url --queue-name "$queue_name" --region "$REGION" --query QueueUrl --output text)"
  local queue_arn
  queue_arn="$(awslocal sqs get-queue-attributes --queue-url "$queue_url" --attribute-names QueueArn --region "$REGION" --query 'Attributes.QueueArn' --output text)"

  local attrs_file
  attrs_file="$(mktemp)"
  write_queue_attributes "$attrs_file" "$visibility" "$dlq_arn" "$queue_arn" "$TOPIC_ARN"
  awslocal sqs set-queue-attributes --queue-url "$queue_url" --region "$REGION" --attributes "file://${attrs_file}"
  rm -f "$attrs_file"

  local existing
  existing="$(awslocal sns list-subscriptions-by-topic --topic-arn "$TOPIC_ARN" --region "$REGION" --query "Subscriptions[?Endpoint=='${queue_arn}'].SubscriptionArn" --output text || true)"
  if [[ -z "${existing}" || "${existing}" == "None" ]]; then
    awslocal sns subscribe \
      --topic-arn "$TOPIC_ARN" \
      --protocol sqs \
      --notification-endpoint "$queue_arn" \
      --attributes RawMessageDelivery=true \
      --region "$REGION" >/dev/null
  else
    local sub_arn
    sub_arn="$(echo "$existing" | awk '{print $1}')"
    if [[ -n "$sub_arn" && "$sub_arn" != "PendingConfirmation" ]]; then
      awslocal sns set-subscription-attributes \
        --subscription-arn "$sub_arn" \
        --attribute-name RawMessageDelivery \
        --attribute-value true \
        --region "$REGION"
    fi
  fi

  printf '%s\n' "$queue_url"
}

# Device commands stay at 60s (MQTT publish is bounded). Model generation can
# take several seconds plus upload/callback, so visibility must exceed that.
DEVICE_QUEUE_URL="$(subscribe_queue "device-commands" "device-commands-dlq" 60)"
MODEL_QUEUE_URL="$(subscribe_queue "model-gen-jobs" "model-gen-jobs-dlq" 300)"

if ! awslocal dynamodb describe-table --table-name "$TABLE_NAME" --region "$REGION" >/dev/null 2>&1; then
  awslocal dynamodb create-table \
    --table-name "$TABLE_NAME" \
    --attribute-definitions AttributeName=commandId,AttributeType=S \
    --key-schema AttributeName=commandId,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST \
    --region "$REGION" >/dev/null
fi

if ! awslocal s3api head-bucket --bucket "$BUCKET_NAME" >/dev/null 2>&1; then
  awslocal s3api create-bucket --bucket "$BUCKET_NAME" --region "$REGION" >/dev/null
fi
awslocal s3api put-public-access-block \
  --bucket "$BUCKET_NAME" \
  --public-access-block-configuration \
  BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true

echo "localstack v0.4 ready"
echo "topic=$TOPIC_ARN"
echo "device-queue=$DEVICE_QUEUE_URL"
echo "model-gen-queue=$MODEL_QUEUE_URL"
echo "table=$TABLE_NAME"
echo "bucket=$BUCKET_NAME"
