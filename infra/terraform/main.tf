# ---------------------------------------------------------------------------
# Weekly Commitments – Infrastructure as Code (PRD §12.8)
# Root module – wires up VPC, ECS, RDS, ElastiCache, SQS, S3/CDN
# ---------------------------------------------------------------------------
terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Backend configured per-environment in envs/*.tfvars
  backend "s3" {}
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "weekly-commitments"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

# ── Variables ───────────────────────────────────────────────────────────────

variable "aws_region" {
  description = "AWS region for all resources"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
}

variable "db_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t3.micro"
}

variable "ecs_api_cpu" {
  description = "ECS API task CPU units"
  type        = number
  default     = 512
}

variable "ecs_api_memory" {
  description = "ECS API task memory (MiB)"
  type        = number
  default     = 1024
}

variable "weekly_service_image" {
  description = "Container image URI for weekly-service"
  type        = string
}

# ── Data Sources ────────────────────────────────────────────────────────────

data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

# ── SQS Queues (PRD §12.3 D6) ──────────────────────────────────────────────

resource "aws_sqs_queue" "plan_events_dlq" {
  name                      = "wc-${var.environment}-plan-events-dlq"
  message_retention_seconds = 1209600 # 14 days
  sqs_managed_sse_enabled   = true
}

resource "aws_sqs_queue" "plan_events" {
  name                      = "wc-${var.environment}-plan-events"
  visibility_timeout_seconds = 30
  message_retention_seconds  = 86400 # 1 day
  sqs_managed_sse_enabled    = true

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.plan_events_dlq.arn
    maxReceiveCount     = 3
  })
}

# ── Outputs ─────────────────────────────────────────────────────────────────

output "sqs_plan_events_queue_url" {
  value = aws_sqs_queue.plan_events.url
}

output "sqs_plan_events_dlq_url" {
  value = aws_sqs_queue.plan_events_dlq.url
}
