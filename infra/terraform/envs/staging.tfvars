# Staging environment variables (PRD §12.2)
environment          = "staging"
aws_region           = "us-east-1"
db_instance_class    = "db.t3.small"
ecs_api_cpu          = 1024
ecs_api_memory       = 2048
weekly_service_image = "ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com/weekly-service:latest-staging"
