# =============================================================================
# PayBot GCP Infrastructure - Networking
# =============================================================================
# VPC, subnets, private service access, and firewall rules for the PayBot
# microservices cluster.
# =============================================================================

# -----------------------------------------------------------------------------
# VPC Network
# -----------------------------------------------------------------------------

resource "google_compute_network" "paybot_vpc" {
  name                    = "paybot-vpc"
  auto_create_subnetworks = false
  project                 = var.project_id

  depends_on = [google_project_service.apis]
}

# -----------------------------------------------------------------------------
# Subnet with secondary ranges for GKE pods and services
# -----------------------------------------------------------------------------

resource "google_compute_subnetwork" "paybot_subnet" {
  name          = "paybot-subnet"
  project       = var.project_id
  region        = var.region
  network       = google_compute_network.paybot_vpc.id
  ip_cidr_range = "10.0.0.0/20"

  # Secondary IP ranges used by GKE for pods and services
  secondary_ip_range {
    range_name    = "paybot-pods"
    ip_cidr_range = "10.4.0.0/14"
  }

  secondary_ip_range {
    range_name    = "paybot-services"
    ip_cidr_range = "10.8.0.0/20"
  }

  private_ip_google_access = true
}

# -----------------------------------------------------------------------------
# Private Services Access (for Cloud SQL and Memorystore)
# -----------------------------------------------------------------------------

# Reserve an IP range for Google-managed services (Cloud SQL, Memorystore)
resource "google_compute_global_address" "paybot_private_ip_range" {
  name          = "paybot-private-ip-range"
  project       = var.project_id
  purpose       = "VPC_PEERING"
  address_type  = "INTERNAL"
  prefix_length = 16
  network       = google_compute_network.paybot_vpc.id
}

# Establish the private connection between VPC and Google services
resource "google_service_networking_connection" "paybot_private_vpc_connection" {
  network                 = google_compute_network.paybot_vpc.id
  service                 = "servicenetworking.googleapis.com"
  reserved_peering_ranges = [google_compute_global_address.paybot_private_ip_range.name]
}

# -----------------------------------------------------------------------------
# Firewall Rules
# -----------------------------------------------------------------------------

# Allow internal traffic within the VPC (pods, services, nodes)
resource "google_compute_firewall" "paybot_allow_internal" {
  name    = "paybot-allow-internal"
  project = var.project_id
  network = google_compute_network.paybot_vpc.id

  allow {
    protocol = "tcp"
    ports    = ["0-65535"]
  }

  allow {
    protocol = "udp"
    ports    = ["0-65535"]
  }

  allow {
    protocol = "icmp"
  }

  # Allow traffic from the subnet, pod, and service CIDR ranges
  source_ranges = [
    "10.0.0.0/20",  # Subnet
    "10.4.0.0/14",  # Pods
    "10.8.0.0/20",  # Services
  ]

  description = "Allow internal communication between PayBot services within the VPC"
}
