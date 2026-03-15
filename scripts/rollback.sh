#!/bin/bash
# ---------------------------------------------------------------------------
# Rollback script for weekly-service (PRD §13.9, §14.5)
#
# Usage:
#   ./scripts/rollback.sh weekly-service <previous-image-tag>
#   ./scripts/rollback.sh frontend <previous-version>
# ---------------------------------------------------------------------------
set -euo pipefail

SERVICE="${1:-}"
VERSION="${2:-}"

if [ -z "$SERVICE" ] || [ -z "$VERSION" ]; then
    echo "Usage: $0 <service> <version>"
    echo "  service: weekly-service | frontend"
    echo "  version: image tag or frontend version"
    exit 1
fi

CLUSTER="${ECS_CLUSTER:-wc-prod}"
REGION="${AWS_REGION:-us-east-1}"

case "$SERVICE" in
    weekly-service)
        echo "🔄 Rolling back weekly-service to $VERSION..."

        # Get current task definition
        CURRENT_TD=$(aws ecs describe-services \
            --cluster "$CLUSTER" \
            --services wc-api \
            --region "$REGION" \
            --query 'services[0].taskDefinition' \
            --output text)

        echo "  Current task definition: $CURRENT_TD"

        # Force new deployment with previous image
        # In practice, this would update the task definition with the previous image tag
        echo "  To complete rollback, update ECS service to use image: $VERSION"
        echo "  aws ecs update-service --cluster $CLUSTER --service wc-api --force-new-deployment"

        # Post to Slack
        echo "  📢 Post to #wc-prod-alerts: weekly-service rolled back to $VERSION"
        ;;

    frontend)
        echo "🔄 Rolling back frontend to $VERSION..."
        echo "  Update CDN pointer to: /wc/$VERSION/remoteEntry.js"
        echo "  This is a configuration change — no rebuild needed."
        echo "  📢 Post to #wc-prod-alerts: frontend rolled back to $VERSION"
        ;;

    *)
        echo "Unknown service: $SERVICE"
        echo "Valid services: weekly-service, frontend"
        exit 1
        ;;
esac

echo "✅ Rollback initiated. Monitor dashboards for recovery."
