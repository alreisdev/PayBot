# =============================================================================
# PayBot GCP Infrastructure - Main Configuration
# =============================================================================
# This Terraform configuration provisions GCP infrastructure for the PayBot
# microservices application (AI Service, Financial Service, PostgreSQL,
# RabbitMQ, Redis).
# =============================================================================

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
    google-beta = {
      source  = "hashicorp/google-beta"
      version = "~> 5.0"
    }
  }

  # Local backend for development. For production, use GCS:
  # backend "gcs" {
  #   bucket = "paybot-terraform-state"
  #   prefix = "terraform/state"
  # }
  backend "local" {
    path = "terraform.tfstate"
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
}

provider "google-beta" {
  project = var.project_id
  region  = var.region
}

# -----------------------------------------------------------------------------
# Enable required GCP APIs
# -----------------------------------------------------------------------------

locals {
  required_apis = [
    "compute.googleapis.com",            # Compute Engine (VPC, firewall)
    "container.googleapis.com",          # Google Kubernetes Engine
    "sqladmin.googleapis.com",           # Cloud SQL Admin
    "redis.googleapis.com",              # Memorystore for Redis
    "secretmanager.googleapis.com",      # Secret Manager
    "cloudtrace.googleapis.com",         # Cloud Trace (distributed tracing)
    "iam.googleapis.com",               # Identity and Access Management
    "servicenetworking.googleapis.com",  # Private service connection (Cloud SQL/Memorystore)
  ]
}

resource "google_project_service" "apis" {
  for_each = toset(local.required_apis)

  project            = var.project_id
  service            = each.value
  disable_on_destroy = false
}
