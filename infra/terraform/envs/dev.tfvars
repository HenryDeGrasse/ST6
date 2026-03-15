# Dev environment variables (PRD §12.2)
environment          = "dev"
aws_region           = "us-east-1"
db_instance_class    = "db.t3.micro"
ecs_api_cpu          = 256
ecs_api_memory       = 512
weekly_service_image = "ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com/weekly-service:latest-dev"
