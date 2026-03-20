# =============================================================================
# PayBot GCP Infrastructure - Google Kubernetes Engine
# =============================================================================
# GKE cluster and node pool for running PayBot microservices:
#   - paybot-ai-service (:8080)
#   - paybot-financial-service (:8081)
#   - RabbitMQ 3 (deployed as a StatefulSet on GKE)
# =============================================================================

# -----------------------------------------------------------------------------
# GKE Cluster
# -----------------------------------------------------------------------------

resource "google_container_cluster" "paybot_cluster" {
  name     = "paybot-cluster"
  project  = var.project_id
  location = var.zone

  # Remove the default node pool after creation; we manage our own below
  remove_default_node_pool = true
  initial_node_count       = 1

  deletion_protection = false

  network    = google_compute_network.paybot_vpc.id
  subnetwork = google_compute_subnetwork.paybot_subnet.id

  # VPC-native networking using the secondary ranges defined in the subnet
  ip_allocation_policy {
    cluster_secondary_range_name  = "paybot-pods"
    services_secondary_range_name = "paybot-services"
  }

  # Private cluster configuration - nodes have no public IPs
  private_cluster_config {
    enable_private_nodes    = true
    enable_private_endpoint = false
    master_ipv4_cidr_block  = "172.16.0.0/28"
  }

  # Enable Workload Identity for secure pod-to-GCP-service authentication
  workload_identity_config {
    workload_pool = "${var.project_id}.svc.id.goog"
  }

  # Logging and monitoring
  logging_service    = "logging.googleapis.com/kubernetes"
  monitoring_service = "monitoring.googleapis.com/kubernetes"

  depends_on = [
    google_project_service.apis,
    google_compute_subnetwork.paybot_subnet,
  ]
}

# -----------------------------------------------------------------------------
# GKE Node Pool
# -----------------------------------------------------------------------------

resource "google_container_node_pool" "paybot_nodes" {
  name     = "paybot-node-pool"
  project  = var.project_id
  location = var.zone
  cluster  = google_container_cluster.paybot_cluster.name

  node_count = var.gke_num_nodes

  # Autoscaling: scale between 2 and 5 nodes based on demand
  autoscaling {
    min_node_count = 2
    max_node_count = 5
  }

  node_config {
    machine_type = var.gke_machine_type

    # Use Workload Identity metadata on nodes
    workload_metadata_config {
      mode = "GKE_METADATA"
    }

    # Broad OAuth scope; actual permissions are controlled by IAM
    oauth_scopes = [
      "https://www.googleapis.com/auth/cloud-platform",
    ]

    labels = {
      app     = "paybot"
      managed = "terraform"
    }

    tags = ["paybot-node"]
  }

  management {
    auto_repair  = true
    auto_upgrade = true
  }
}
