# =============================================================================
# PayBot GCP Infrastructure - Variables
# =============================================================================

# -----------------------------------------------------------------------------
# Project & Region
# -----------------------------------------------------------------------------

variable "project_id" {
  description = "The GCP project ID where all resources will be created"
  type        = string
}

variable "region" {
  description = "The GCP region for resource deployment"
  type        = string
  default     = "us-central1"
}

variable "zone" {
  description = "The GCP zone for zonal resources (e.g., GKE nodes)"
  type        = string
  default     = "us-central1-a"
}

# -----------------------------------------------------------------------------
# GKE Configuration
# -----------------------------------------------------------------------------

variable "gke_num_nodes" {
  description = "Number of GKE nodes per zone in the default node pool"
  type        = number
  default     = 3
}

variable "gke_machine_type" {
  description = "Machine type for GKE worker nodes"
  type        = string
  default     = "e2-medium"
}

# -----------------------------------------------------------------------------
# Cloud SQL Configuration
# -----------------------------------------------------------------------------

variable "db_tier" {
  description = "Cloud SQL instance tier (machine type)"
  type        = string
  default     = "db-f1-micro"
}

variable "db_password" {
  description = "Password for the Cloud SQL 'paybot' database user"
  type        = string
  sensitive   = true
}

# -----------------------------------------------------------------------------
# Redis (Memorystore) Configuration
# -----------------------------------------------------------------------------

variable "redis_memory_size_gb" {
  description = "Memory size in GB for the Memorystore Redis instance"
  type        = number
  default     = 1
}

variable "redis_password" {
  description = "Password for the Redis instance (stored in Secret Manager)"
  type        = string
  sensitive   = true
}

# -----------------------------------------------------------------------------
# RabbitMQ Configuration
# -----------------------------------------------------------------------------

variable "rabbitmq_password" {
  description = "Password for the RabbitMQ instance (deployed on GKE, stored in Secret Manager)"
  type        = string
  sensitive   = true
}

# -----------------------------------------------------------------------------
# Gemini / AI Credentials
# -----------------------------------------------------------------------------

variable "gemini_credentials_file" {
  description = "Path to the Gemini service account credentials JSON file (for Secret Manager)"
  type        = string
  default     = ""
}
