# LocalStack Setup Guide

This project uses LocalStack to emulate AWS services (KMS, S3) for local development.

**Note**: When LocalStack mode is enabled, emails are logged via mulog instead of being sent through LocalStack SES. This provides a simple way to verify email functionality during development without requiring LocalStack Pro.

## Prerequisites

- Docker and Docker Compose installed
- AWS CLI installed (for `awslocal` command)
- LocalStack AWS CLI wrapper:
  ```bash
  pip install awscli-local
  ```

## Starting LocalStack

1. Start the services using Docker Compose:
   ```bash
   docker-compose up -d
   ```

2. Verify LocalStack is running:
   ```bash
   docker-compose ps
   ```

   You should see `pgvector` and `localstack` services running.

3. Check LocalStack logs:
   ```bash
   docker-compose logs -f localstack
   ```

## Initialization

The LocalStack initialization script (`localstack-init/init.sh`) automatically creates:

- **S3 Bucket**: `grain-files`
- **KMS Key**: Creates a key and alias `alias/grain-local-key`

The script runs automatically when LocalStack starts. You can check the output:

```bash
docker-compose logs localstack | grep "LocalStack initialization"
```

## Configuration

The application is configured to use LocalStack in `config.edn`:

```clojure
{:localstack/enabled true
 :localstack/endpoint "http://localhost:4566"
 :aws/region "us-east-1"
 :kms-key-id "alias/grain-local-key"
 :s3-bucket "grain-files"
 :email-from "noreply@demo.net"}
```

## Testing AWS Services

### Test S3

```bash
# List buckets
awslocal s3 ls

# Upload a file
echo "test content" > test.txt
awslocal s3 cp test.txt s3://grain-files/test.txt

# Download a file
awslocal s3 cp s3://grain-files/test.txt downloaded.txt
```

### Test KMS

```bash
# List KMS keys
awslocal kms list-keys

# Describe the key
awslocal kms describe-key --key-id alias/grain-local-key

# Encrypt data
awslocal kms encrypt --key-id alias/grain-local-key --plaintext "Hello World"
```

## Email Testing

When LocalStack mode is enabled, emails are **logged via mulog** rather than being sent through SES. This provides immediate visibility without requiring additional services.

The email will be logged as a structured event with the type `::ai.obney.workshop.email-ses.core/email-sent-localstack-mock` containing all email fields (from, to, cc, bcc, subject, body-text, body-html).

Example mulog JSON output:
```json
{
  "mulog/event-name": "ai.obney.workshop.email-ses.core/email-sent-localstack-mock",
  "mulog/timestamp": 1234567890,
  "from": "noreply@demo.net",
  "to": ["test@example.com"],
  "subject": "Test Email",
  "body-text": "This is a test message"
}
```

## Component Usage Examples

### Crypto-KMS Component

```clojure
(require '[ai.obney.workshop.crypto-kms.interface :as crypto-kms])

(def provider (crypto-kms/->KmsCryptoProvider
               {:kms-key-id "alias/grain-local-key"
                :localstack/enabled true
                :localstack/endpoint "http://localhost:4566"
                :aws/region "us-east-1"}))

;; Encrypt
(def encrypted (crypto-kms/encrypt provider {:plaintext "Hello, World!"}))

;; Decrypt
(crypto-kms/decrypt provider encrypted)
```

### Email-SES Component

```clojure
(require '[ai.obney.workshop.email-ses.interface :as email])

(def ses (email/->SES {:localstack/enabled true}))

;; Send email (will be logged via mulog)
(email/send ses {:to ["test@example.com"]
                 :from "noreply@demo.net"
                 :subject "Hello there!"
                 :body-text "TEST!"})
```

The email will be logged via mulog and appear in your console output as structured JSON.

### File-Store-S3 Component

```clojure
(require '[ai.obney.workshop.file-store-s3.interface :as file-store])

(def fs (file-store/start
         (file-store/->S3FileStore
          {:s3-bucket "grain-files"
           :localstack/enabled true
           :localstack/endpoint "http://localhost:4566"
           :aws/region "us-east-1"})))

;; Put file
(file-store/put-file fs
  {:file-id (random-uuid)
   :file-contents (.getBytes "Hello, world!")})

;; Get file
(String. (file-store/get-file fs {:file-id your-file-id}))
```

### URL-Presigner-AWS Component

The URL presigner automatically uses path-style URLs for LocalStack (e.g., `http://localhost:4566/bucket/key`) instead of virtual-hosted style URLs (e.g., `http://bucket.localhost:4566/key`).

```clojure
(require '[ai.obney.workshop.url-presigner-aws.interface :as presigner])
(require '[clj-http.client :as http])
(import '[software.amazon.awssdk.regions Region])

(def url-presigner
  (presigner/->URLPresignerAWS
    {:aws-region Region/US_EAST_1
     :localstack/enabled true
     :localstack/endpoint "http://localhost:4566"}))

;; Generate presigned PUT URL
(def put-response
  (presigner/presign-put
    url-presigner
    {:s3-bucket "grain-files"
     :s3-key "test"
     :s3-object-name "test.txt"
     :content-type "text/plain"
     :signature-duration-minutes 30}))

;; Upload using presigned URL
(http/put (:url put-response)
          {:body (.getBytes "Hello LocalStack!")
           :headers {"Content-Type" "text/plain"}})

;; Generate presigned GET URL
(def get-response
  (presigner/presign-get
    url-presigner
    {:s3-bucket "grain-files"
     :s3-key "test/test.txt"
     :signature-duration-minutes 30}))

;; Download using presigned URL
(-> (http/get (:url get-response) {:as :byte-array})
    :body
    (String.))
```

## Switching Between LocalStack and Real AWS

To switch to real AWS services, update `config.edn`:

```clojure
{:localstack/enabled false
 :aws/region "us-east-1"
 :kms-key-id "your-real-kms-key-id"
 :s3-bucket "your-real-s3-bucket"
 :email-from "your-verified-ses-email"}
```

## Troubleshooting

### LocalStack not starting

Check if port 4566 is already in use:
```bash
lsof -i :4566
```

### Services not initialized

Manually run the initialization script:
```bash
docker-compose exec localstack bash /etc/localstack/init/ready.d/init.sh
```

### Can't connect to LocalStack

Verify the service is healthy:
```bash
curl http://localhost:4566/_localstack/health
```

### Clear LocalStack data

Stop and remove containers:
```bash
docker-compose down
docker-compose up -d
```

## Stopping LocalStack

```bash
docker-compose down
```

To remove volumes as well:
```bash
docker-compose down -v
```
