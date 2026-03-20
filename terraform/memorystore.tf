# =============================================================================
# PayBot GCP Infrastructure - Memorystore for Redis 7
# =============================================================================
# Managed Redis instance used by paybot-ai-service for:
#   - Chat session/conversation history caching
#   - General application caching
# =============================================================================

resource "google_redis_instance" "paybot_redis" {
  name           = "paybot-redis"
  project        = var.project_id
  region         = var.region
  display_name   = "PayBot Redis"

  tier           = "BASIC"
  memory_size_gb = var.redis_memory_size_gb
  redis_version  = "REDIS_7_0"

  # Connect via private service access through the VPC
  authorized_network = google_compute_network.paybot_vpc.id
  connect_mode       = "PRIVATE_SERVICE_ACCESS"

  # Redis configuration parameters
  redis_configs = {
    maxmemory-policy = "allkeys-lru"
  }

  labels = {
    app     = "paybot"
    managed = "terraform"
  }

  depends_on = [
    google_service_networking_connection.paybot_private_vpc_connection,
    google_project_service.apis,
  ]
}
