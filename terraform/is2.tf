variable "region" {
  type    = string
  default = "europe-west1"
}

variable "appengine-region" {
  type    = string
  default = "europe-west"
}

variable "project" {
  type    = string
  default = "i3s-dev"
}

provider "google" {
  credentials = file("~/code/credentials/i3s-dev-b538d40535d9.json") # Ugly!
  project     = var.project
  region      = var.region
  zone        = "europe-west1-a"
}

resource "google_sql_database_instance" "master" {
  name             = "is2-master-instance"
  database_version = "POSTGRES_11"
  region           = var.region

  settings {
    tier = "db-f1-micro"
  }
}

resource "google_app_engine_application" "is2-app" {
  project     = var.project
  location_id = var.appengine-region
} 