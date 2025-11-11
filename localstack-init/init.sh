#!/bin/bash

echo "Initializing LocalStack resources..."

# Create S3 bucket
echo "Creating S3 bucket: grain-files"
awslocal s3 mb s3://grain-files
echo "S3 bucket created successfully"

# Create KMS key
echo "Creating KMS key..."
KMS_KEY=$(awslocal kms create-key --description "Local development encryption key" --query 'KeyMetadata.KeyId' --output text)
echo "KMS Key created: $KMS_KEY"
echo "Use this KMS Key ID in your config: $KMS_KEY"

# Create alias for the KMS key
awslocal kms create-alias --alias-name alias/grain-local-key --target-key-id "$KMS_KEY"
echo "KMS alias created: alias/grain-local-key"

echo "LocalStack initialization complete!"
echo "================================================"
echo "S3 Bucket: grain-files"
echo "KMS Key ID: $KMS_KEY"
echo "KMS Alias: alias/grain-local-key"
echo "================================================"
echo "Note: Emails will be printed to console when LocalStack mode is enabled"
echo "================================================"
