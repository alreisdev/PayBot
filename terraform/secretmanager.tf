# =============================================================================
# PayBot GCP Infrastructure - Secret Manager
# =============================================================================
# Stores sensitive configuration values used by PayBot microservices.
# Secrets are accessed at runtime via Workload Identity + Secret Manager API.
# =============================================================================

# -----------------------------------------------------------------------------
# Database Password
# -----------------------------------------------------------------------------

resource "google_secret_manager_secret" "paybot_db_password" {
  secret_id = "paybot-db-password"
  project   = var.project_id

  replication {
    auto {}
  }

  labels = {
    app     = "paybot"
    managed = "terraform"
  }

  depends_on = [google_project_service.apis]
}

resource "google_secret_manager_secret_version" "paybot_db_password_version" {
  secret      = google_secret_manager_secret.paybot_db_password.id
  secret_data = var.db_password
}

# -----------------------------------------------------------------------------
# RabbitMQ Password
# -----------------------------------------------------------------------------

resource "google_secret_manager_secret" "paybot_rabbitmq_password" {
  secret_id = "paybot-rabbitmq-password"
  project   = var.project_id

  replication {
    auto {}
  }

  labels = {
    app     = "paybot"
    managed = "terraform"
  }

  depends_on = [google_project_service.apis]
}

resource "google_secret_manager_secret_version" "paybot_rabbitmq_password_version" {
  secret      = google_secret_manager_secret.paybot_rabbitmq_password.id
  secret_data = var.rabbitmq_password
}

# -----------------------------------------------------------------------------
# Redis Password
# -----------------------------------------------------------------------------

resource "google_secret_manager_secret" "paybot_redis_password" {
  secret_id = "paybot-redis-password"
  project   = var.project_id

  replication {
    auto {}
  }

  labels = {
    app     = "paybot"
    managed = "terraform"
  }

  depends_on = [google_project_service.apis]
}

resource "google_secret_manager_secret_version" "paybot_redis_password_version" {
  secret      = google_secret_manager_secret.paybot_redis_password.id
  secret_data = var.redis_password
}

# -----------------------------------------------------------------------------
# Gemini Service Account Credentials
# -----------------------------------------------------------------------------

resource "google_secret_manager_secret" "paybot_gemini_credentials" {
  secret_id = "paybot-gemini-credentials"
  project   = var.project_id

  replication {
    auto {}
  }

  labels = {
    app     = "paybot"
    managed = "terraform"
  }

  depends_on = [google_project_service.apis]
}

resource "google_secret_manager_secret_version" "paybot_gemini_credentials_version" {
  count       = var.gemini_credentials_file != "" ? 1 : 0
  secret      = google_secret_manager_secret.paybot_gemini_credentials.id
  secret_data = file(var.gemini_credentials_file)
}
