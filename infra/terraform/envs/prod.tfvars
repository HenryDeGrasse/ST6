# Production environment variables (PRD §12.2)
environment          = "prod"
aws_region           = "us-east-1"
db_instance_class    = "db.t3.medium"
ecs_api_cpu          = 2048
ecs_api_memory       = 4096
weekly_service_image = "ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com/weekly-service:latest-prod"
