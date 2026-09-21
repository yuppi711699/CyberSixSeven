terraform {
  backend "s3" {
    # Filled at init from backend.hcl.example. The bucket must have encryption
    # and DynamoDB locking enabled before the first apply.
  }
}
