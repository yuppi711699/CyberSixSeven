variable "aws_region" {
  type    = string
  default = "us-west-2"
}

variable "environment" {
  type    = string
  default = "auth-preview"
}

variable "hosted_zone_name" {
  type    = string
  default = "aiastrologyperdictions.com"
}

variable "api_hostname" {
  type    = string
  default = "api.aiastrologyperdictions.com"
}

variable "vpc_cidr" {
  type    = string
  default = "10.50.0.0/16"
}

variable "platform_api_image" {
  type        = string
  description = "Immutable ECR image for platform-api, tagged with the git commit SHA. Never latest."
}

variable "platform_api_cpu" {
  type    = number
  default = 512
}

variable "platform_api_memory" {
  type    = number
  default = 1024
}

variable "db_password" {
  type        = string
  sensitive   = true
  description = "RDS password. Set only in an uncommitted tfvars file; never commit the value."
}

variable "redis_auth_token" {
  type        = string
  sensitive   = true
  description = "ElastiCache AUTH token. Set only in an uncommitted tfvars file; never commit the value."
}
