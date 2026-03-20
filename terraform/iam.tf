# =============================================================================
# PayBot GCP Infrastructure - IAM & Service Accounts
# =============================================================================
# Service account and role bindings for PayBot workloads running on GKE.
# Uses Workload Identity to map Kubernetes service accounts to GCP service
# accounts without needing to manage keys.
# =============================================================================

# -----------------------------------------------------------------------------
# GCP Service Account for PayBot workloads
# -----------------------------------------------------------------------------

resource "google_service_account" "paybot_sa" {
  account_id   = "paybot-sa"
  project      = var.project_id
  display_name = "PayBot Service Account"
  description  = "Service account used by PayBot microservices on GKE via Workload Identity"
}

# -----------------------------------------------------------------------------
# IAM Role Bindings
# -----------------------------------------------------------------------------

# Allow PayBot to call Vertex AI / Gemini APIs
resource "google_project_iam_member" "paybot_aiplatform_user" {
  project = var.project_id
  role    = "roles/aiplatform.user"
  member  = "serviceAccount:${google_service_account.paybot_sa.email}"
}

# Allow PayBot to access secrets from Secret Manager
resource "google_project_iam_member" "paybot_secret_accessor" {
  project = var.project_id
  role    = "roles/secretmanager.secretAccessor"
  member  = "serviceAccount:${google_service_account.paybot_sa.email}"
}

# Allow PayBot to export traces to Cloud Trace (Zipkin-compatible)
resource "google_project_iam_member" "paybot_trace_agent" {
  project = var.project_id
  role    = "roles/cloudtrace.agent"
  member  = "serviceAccount:${google_service_account.paybot_sa.email}"
}

# Allow PayBot to connect to Cloud SQL via the Cloud SQL Proxy
resource "google_project_iam_member" "paybot_cloudsql_client" {
  project = var.project_id
  role    = "roles/cloudsql.client"
  member  = "serviceAccount:${google_service_account.paybot_sa.email}"
}

# -----------------------------------------------------------------------------
# Workload Identity Binding
# -----------------------------------------------------------------------------
# This allows the Kubernetes service account "paybot-ksa" in the "paybot"
# namespace to impersonate the GCP service account "paybot-sa".
#
# On the Kubernetes side, you must annotate the KSA:
#   kubectl annotate serviceaccount paybot-ksa \
#     --namespace paybot \
#     iam.gke.io/gcp-service-account=paybot-sa@<PROJECT_ID>.iam.gserviceaccount.com

resource "google_service_account_iam_member" "paybot_workload_identity" {
  service_account_id = google_service_account.paybot_sa.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "serviceAccount:${var.project_id}.svc.id.goog[paybot/paybot-ksa]"

  depends_on = [
    google_container_cluster.paybot_cluster,
  ]
}
