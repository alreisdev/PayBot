# =============================================================================
# PayBot GCP Infrastructure - Outputs
# =============================================================================
# Key values needed for deploying PayBot services to the provisioned
# infrastructure. Use these outputs to configure Kubernetes manifests,
# Helm charts, or CI/CD pipelines.
# =============================================================================

# -----------------------------------------------------------------------------
# GKE Cluster
# -----------------------------------------------------------------------------

output "gke_cluster_name" {
  description = "Name of the GKE cluster"
  value       = google_container_cluster.paybot_cluster.name
}

output "gke_cluster_endpoint" {
  description = "Endpoint (IP) of the GKE cluster control plane"
  value       = google_container_cluster.paybot_cluster.endpoint
  sensitive   = true
}

# -----------------------------------------------------------------------------
# Cloud SQL (PostgreSQL)
# -----------------------------------------------------------------------------

output "cloudsql_instance_connection_name" {
  description = "Cloud SQL instance connection name (used by Cloud SQL Proxy)"
  value       = google_sql_database_instance.paybot_postgres.connection_name
}

output "cloudsql_private_ip" {
  description = "Private IP address of the Cloud SQL instance"
  value       = google_sql_database_instance.paybot_postgres.private_ip_address
}

# -----------------------------------------------------------------------------
# Memorystore (Redis)
# -----------------------------------------------------------------------------

output "memorystore_host" {
  description = "Hostname of the Memorystore Redis instance"
  value       = google_redis_instance.paybot_redis.host
}

output "memorystore_port" {
  description = "Port of the Memorystore Redis instance"
  value       = google_redis_instance.paybot_redis.port
}

# -----------------------------------------------------------------------------
# IAM
# -----------------------------------------------------------------------------

output "service_account_email" {
  description = "Email of the PayBot GCP service account (for Workload Identity annotation)"
  value       = google_service_account.paybot_sa.email
}
