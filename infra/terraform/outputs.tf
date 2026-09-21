output "api_url" {
  value = "https://${var.api_hostname}"
}

output "alb_dns_name" {
  value = aws_lb.api.dns_name
}

output "ecr_platform_api_url" {
  value = aws_ecr_repository.platform_api.repository_url
}

output "rds_endpoint" {
  value     = aws_db_instance.postgres.address
  sensitive = true
}

output "redis_endpoint" {
  value     = aws_elasticache_replication_group.redis.primary_endpoint_address
  sensitive = true
}
