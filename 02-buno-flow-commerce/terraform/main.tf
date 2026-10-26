terraform {
 required_version=">= 1.5.0"
 required_providers { aws={source="hashicorp/aws",version="~> 5.0"} }
}
provider "aws" { region=var.region }
variable "region" { type=string; default="us-east-1" }
variable "environment" { type=string; default="dev" }
resource "aws_sqs_queue" "order_dlq" { name="buno-orders-${var.environment}-dlq" message_retention_seconds=1209600 }
resource "aws_sqs_queue" "order_events" { name="buno-orders-${var.environment}" redrive_policy=jsonencode({deadLetterTargetArn=aws_sqs_queue.order_dlq.arn,maxReceiveCount=5}) }
output "orders_queue_url" { value=aws_sqs_queue.order_events.url }
