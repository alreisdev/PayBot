# =============================================================================
# PayBot GCP Infrastructure - Cloud SQL (PostgreSQL 16)
# =============================================================================
# Managed PostgreSQL instance for the paybot-financial-service.
# Database schema is managed by Flyway migrations in the financial service.
# =============================================================================

# -----------------------------------------------------------------------------
# Cloud SQL Instance
# -----------------------------------------------------------------------------

resource "google_sql_database_instance" "paybot_postgres" {
  name             = "paybot-postgres"
  project          = var.project_id
  region           = var.region
  database_version = "POSTGRES_16"

  deletion_protection = false

  settings {
    tier              = var.db_tier
    availability_type = "ZONAL"

    # Private IP only - no public internet access
    ip_configuration {
      ipv4_enabled                                  = false
      private_network                               = google_compute_network.paybot_vpc.id
      enable_private_path_for_google_cloud_services = true
    }

    # Automated daily backups at 03:00 UTC
    backup_configuration {
      enabled    = true
      start_time = "03:00"

      backup_retention_settings {
        retained_backups = 7
      }
    }

    # Maintenance window: Sunday 04:00 UTC
    maintenance_window {
      day          = 7
      hour         = 4
      update_track = "stable"
    }

    disk_autoresize = true

    database_flags {
      name  = "max_connections"
      value = "100"
    }
  }

  # Cloud SQL requires the private services connection to be established first
  depends_on = [
    google_service_networking_connection.paybot_private_vpc_connection,
    google_project_service.apis,
  ]
}

# -----------------------------------------------------------------------------
# Database
# -----------------------------------------------------------------------------

resource "google_sql_database" "paybot_db" {
  name     = "paybotdb"
  project  = var.project_id
  instance = google_sql_database_instance.paybot_postgres.name
}

# -----------------------------------------------------------------------------
# Database User
# -----------------------------------------------------------------------------

resource "google_sql_user" "paybot_user" {
  name     = "paybot"
  project  = var.project_id
  instance = google_sql_database_instance.paybot_postgres.name
  password = var.db_password
}
