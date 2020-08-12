# Configure the Artifactory provider
provider "artifactory" {
  url = "artifactory_url"
  username = "username"
  password = "password"
}

# Create a new Artifactory group called terraform
resource "artifactory_group" "test-group" {
  name             = "terraform"
  description      = "test group"
  admin_privileges = false
}

# Create a new Artifactory user called terraform
resource "artifactory_user" "test-user" {
  name     = "terraform"
  email    = "test-user@artifactory-terraform.com"
  groups   = ["terraform"]
  password = "my super secret password"
}

# Create a new Artifactory local repository called my-local
resource "artifactory_local_repository" "my-local" {
  key          = "my-local"
  package_type = "npm"
}

# Create a new Artifactory remote repository called my-remote
resource "artifactory_remote_repository" "my-remote" {
  key             = "my-remote"
  package_type    = "npm"
  url             = "https://registry.npmjs.org/"
  repo_layout_ref = "npm-default"
}

//  REPLICATIONS DON'T WORK
//# Create a replication between two artifactory local repositories
//resource "artifactory_local_repository" "provider_test_source" {
//	key = "provider_test_source"
//	package_type = "maven"
//}
//
//resource "artifactory_local_repository" "provider_test_dest" {
//	key = "provider_test_dest"
//	package_type = "maven"
//}
//
//resource "artifactory_replication_config" "foo-rep" {
//	repo_key = "artifactory_local_repository.provider_test_source.key"
//	cron_exp = "0 0 * * * ?"
//	enable_event_replication = true
//    replications = [
//      {
//        url = "http://rtharmdhr2.westus2.cloudapp.azure.com/artifactory"
//        username = "admin"
//        password = "password1"
//      }
//  ]
//}

# Create a replication between two artifactory local repositories
resource "artifactory_local_repository" "provider_test_source" {
  key = "provider_test_source"
  package_type = "maven"
}

resource "artifactory_local_repository" "provider_test_dest" {
  key = "provider_test_dest"
  package_type = "maven"
}

resource "artifactory_single_replication_config" "foo-rep" {
  repo_key = artifactory_local_repository.provider_test_source.key
  cron_exp = "0 0 * * * ?"
  enable_event_replication = true
  url = "artifactory_url"
  username = "username"
  password = "password"
}

resource "artifactory_local_repository" "bar" {
  key = "bar"
  package_type = "maven"
}

resource "artifactory_local_repository" "baz" {
  key = "baz"
  package_type = "maven"
}

resource "artifactory_virtual_repository" "foo" {
  key          = "foo"
  package_type = "maven"
  repositories = [
    artifactory_local_repository.bar.key,
    artifactory_local_repository.baz.key
  ]
}

# Create a new Artifactory certificate called my-cert
resource "artifactory_certificate" "my-cert" {
  alias   = "my-cert"
  content = file("/key.pem")
}

# This can then be used by a remote repository
resource "artifactory_remote_repository" "my-remote1" {
  client_tls_certificate = artifactory_certificate.my-cert.alias
  key             = "my-remote1"
  package_type    = "npm"
  url             = "https://registry.npmjs.org/"
  repo_layout_ref = "npm-default"
}