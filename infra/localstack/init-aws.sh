#!/bin/bash
# Create SQS queues for local development (PRD §12.3 D6)
set -euo pipefail

echo "Creating SQS queues for weekly-commitments..."

awslocal sqs create-queue --queue-name wc-local-plan-events-dlq
awslocal sqs create-queue --queue-name wc-local-plan-events \
  --attributes '{
    "RedrivePolicy": "{\"deadLetterTargetArn\":\"arn:aws:sqs:us-east-1:000000000000:wc-local-plan-events-dlq\",\"maxReceiveCount\":\"3\"}"
  }'

echo "SQS queues created successfully."
