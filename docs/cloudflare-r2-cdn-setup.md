# Cloudflare R2 and CDN Setup Guide

## 1. Purpose

Configure Cloudflare R2 for image object storage and a Cloudflare-managed custom domain for CDN delivery. This guide documents the initial setup, cache policy, bucket-scoped S3 credentials, and Spring Boot environment configuration.

All account identifiers, domains, and credentials use placeholders. The verification section distinguishes completed dashboard steps from backend functionality that has not yet been tested.

## 2. Prerequisites

- A Cloudflare account with dashboard access.
- A domain managed by Cloudflare DNS (`<DOMAIN>`).
- A payment method if required to activate R2.
- A Spring Boot backend with an existing S3-compatible storage integration.
- Permission to configure backend environment variables.

> **Pricing:** During activation, the R2 free tier displayed 10 GB of Standard storage, 1 million Class A operations, and 10 million Class B operations per month. Limits and pricing may change. Registering a payment method does not guarantee zero charges if free allowances are exceeded; review current billing terms.

## 3. Placeholders

| Placeholder | Meaning | Illustrative value |
| --- | --- | --- |
| `<DOMAIN>` | Domain managed in Cloudflare | `example.com` |
| `<MEDIA_HOST>` | Public image subdomain | `img.example.com` |
| `<BUCKET_NAME>` | R2 bucket name | `project-media` |
| `<ACCOUNT_ID>` | Cloudflare account identifier | `your-account-id` |
| `<ACCESS_KEY_ID>` | R2 S3 access key identifier | `your-access-key-id` |
| `<SECRET_ACCESS_KEY>` | R2 S3 secret access key | `your-secret-access-key` |

Use a bucket name that complies with R2 naming rules; lowercase letters and hyphens are recommended.

## 4. Enable Cloudflare R2

1. Open the [Cloudflare Dashboard](https://dash.cloudflare.com/).
2. Navigate to **R2 Object Storage** (possibly under **Storage & Databases**).
3. Review the available plan and billing terms.
4. Select **Get Started**, **Enable R2**, or the equivalent activation action.
5. Add a payment method if requested and finish activation.
6. Confirm that **Create bucket** is available.

**Expected result:** R2 is enabled for the account.

## 5. Create the image bucket

1. On the main **R2 Object Storage** page, select **Create bucket**.
2. Configure the following fields:

   | Field | Value |
   | --- | --- |
   | Bucket name | `<BUCKET_NAME>` |
   | Location | `Automatic` |
   | Default Storage Class | `Standard` |

3. Select **Create bucket**.
4. Verify that the bucket appears in **Buckets**.

**Rationale:** Standard storage suits frequently accessed product catalog images.

## 6. Connect a custom domain

1. Open **R2 Object Storage → Buckets → `<BUCKET_NAME>`**.
2. Navigate to **Settings → Custom Domains**.
3. Select **Connect Domain**.
4. Enter `<MEDIA_HOST>` (for example, `img.example.com`) without `https://`.
5. Set the minimum TLS version to **TLS 1.2**.
6. Select **Connect Domain**.
7. Wait for the status to change from **Initializing** to **Active**.

Cloudflare normally creates or manages the necessary DNS configuration as part of this workflow; no manual DNS record was created in the documented setup.

**Expected result:** `<MEDIA_HOST>` is **Active** and serves content over HTTPS, independently of the main `<DOMAIN>` website.

> **Security:** Objects accessible through this public domain should be treated as publicly available. Do not store private files or secrets under publicly accessible paths.

## 7. Create a CDN cache rule

1. Under **Websites**, select the zone for `<DOMAIN>`.
2. Navigate to **Caching → Cache Rules** (the menu location may vary).
3. Select **Create rule → Create Rule**.
4. Set the rule name to `media-images-cache`.
5. Under **If incoming requests match**, choose **Custom filter expression**.
6. Set the filter:

   | Field | Value |
   | --- | --- |
   | Field | `Hostname` |
   | Operator | `equals` |
   | Value | `<MEDIA_HOST>` |

7. Set **Cache eligibility** to **Eligible for cache**.
8. Under **Edge TTL**, select the option to override the origin cache-control value (for example, **Ignore cache-control header and use this TTL**).
9. Set the TTL to **7 days** (`604800` seconds).
10. Leave **Browser TTL** without an additional override and **Cache Key** at its defaults.
11. Select **Deploy**.

**Expected result:** The rule applies only to `<MEDIA_HOST>` and does not change caching for the main website.

### 7.1. Why seven days?

Seven days was selected for initial development and testing rather than 30 days. A longer TTL can be considered for production when every image update uses a new object key.

Edge TTL is not a guarantee that every cache location will retain the object for seven days: Cloudflare may evict it sooner. Browser caching is governed separately, because no Browser TTL override was configured.

## 8. Verify public access and CDN caching

### 8.1. Upload a test image

1. Navigate to **R2 Object Storage → Buckets → `<BUCKET_NAME>` → Objects**.
2. Select **Upload** or **Upload files**.
3. Upload a sample image, such as `test.webp`.
4. Confirm that the object appears in the bucket.
5. Open the corresponding public URL:

   ```text
   https://<MEDIA_HOST>/test.webp
   ```

6. Verify that the image loads successfully.

### 8.2. Inspect the cache response

1. Open the image URL in Chrome.
2. Press **F12** and open **Network**.
3. Reload the page and select the image request.
4. Under **Headers → Response Headers**, locate `cf-cache-status`.
5. Interpret the response:

   | Value | Meaning |
   | --- | --- |
   | `HIT` | The response was served from a Cloudflare cache. |
   | `MISS` | The cache location did not have a usable copy. |
   | `EXPIRED` | The cached copy had expired and required refreshing. |

**Observed result:** `CF-Cache-Status: HIT`, confirming that the test image was served from Cloudflare cache for that request.

> An isolated `MISS` does not necessarily indicate an incorrect cache rule; it may be the first request reaching a particular cache location.

## 9. Create bucket-scoped S3 credentials

1. Return to the main **R2 Object Storage** page, outside the bucket settings.
2. Open **Manage R2 API Tokens**.
3. Choose **Account API Tokens**.
4. Select **Create API Token**.
5. Configure:

   | Field | Value |
   | --- | --- |
   | Name | `backend-r2-objects` |
   | Permissions | `Object Read & Write` |
   | Bucket scope | Only `<BUCKET_NAME>` |
   | TTL | `Forever` / `No expiration` |
   | Client IP Address Filter | Not configured during initial setup |

6. Create the token.
7. Securely save the displayed **Access Key ID** and **Secret Access Key**.

**Least privilege:** Object Read & Write was selected instead of Admin Read & Write, and access was limited to the image bucket.

### 9.1. Credentials and identifiers

| Item | Purpose |
| --- | --- |
| Account ID | Identifies the Cloudflare account and is used in the S3 endpoint. |
| Access Key ID | Identifies the backend's S3 credentials. |
| Secret Access Key | Signs and authenticates S3 requests. |
| Token Value | Used for Cloudflare API authentication; not required directly by this S3 integration. |

The S3 credentials are associated with the created R2 token. Revoking the token invalidates those credentials.

### 9.2. Locate the Account ID

The R2 dashboard displays an S3 endpoint with a structure such as:

```text
https://<ACCOUNT_ID>.r2.cloudflarestorage.com
```

The `<ACCOUNT_ID>` segment is the Cloudflare account identifier, **not** the Access Key ID. If Cloudflare provides a jurisdiction-specific endpoint, use the actual endpoint shown for the account rather than assuming the standard hostname.

## 10. Configure Spring Boot environment variables

The backend's storage integration was already implemented. Configure its environment using placeholders:

```dotenv
MEDIA_R2_ACCOUNT_ID=<ACCOUNT_ID>
MEDIA_R2_ACCESS_KEY_ID=<ACCESS_KEY_ID>
MEDIA_R2_SECRET_ACCESS_KEY=<SECRET_ACCESS_KEY>
MEDIA_R2_BUCKET=<BUCKET_NAME>
MEDIA_R2_PUBLIC_BASE_URL=https://<MEDIA_HOST>
```

Distinguish the two URLs:

- **S3 endpoint:** `https://<ACCOUNT_ID>.r2.cloudflarestorage.com` — used by the backend for object operations.
- **Public CDN base URL:** `https://<MEDIA_HOST>` — used to serve images to clients.

The backend should construct public URLs from object keys and correctly encode any special characters in paths.

> **Secrets:** Never commit keys to Git or print them in logs. Use environment variables or a secret manager. `Forever` prevents automatic expiry but does not eliminate the need for credential rotation. Consider IP restrictions when a stable backend egress IP is available.

## 11. Image lifecycle recommendations

### 11.1. Upload

1. Validate file type, size, and tenant authorization in the backend.
2. Generate a unique object key under a tenant-specific prefix.
3. Upload the object to R2 using the S3-compatible API.
4. Store the object key and product association in the application database.
5. Build the public URL using `MEDIA_R2_PUBLIC_BASE_URL`.

Suggested object-key structure:

```text
tenants/<TENANT_ID>/products/<PRODUCT_ID>/<IMAGE_ID>.webp
```

**Recommendation:** Persist the object key rather than the full public URL. This allows the temporary CDN domain to be replaced later without rewriting stored object references.

### 11.2. Replace an image

Generate a **new object key** (such as a new UUID), upload the replacement, and update the product reference. The URL changes, so clients can request the new version without waiting for the old cached response to expire.

### 11.3. Delete an image and invalidate its cache

Deleting an object from R2 does **not** automatically remove copies cached by Cloudflare or browsers. A robust deletion flow should:

1. Remove or unlink the application's reference according to business rules.
2. Delete the object from R2.
3. When immediate CDN removal is required, call Cloudflare's **purge by URL** API with separate, appropriately scoped zone credentials.
4. Record failures and retry safely.

A CDN purge cannot guarantee removal of copies already stored by a browser or another intermediary. Do not rely on a publicly cacheable URL for sensitive content or strict revocation requirements.

> Automatic purging and the full deletion workflow were discussed as implementation recommendations; they were **not configured or verified** during the initial setup.

## 12. Verification checklist

| Check | Initial setup status |
| --- | --- |
| R2 enabled | Completed |
| Bucket created with Automatic location and Standard storage | Completed |
| Custom domain connected | `Active` |
| Minimum TLS version | TLS 1.2 |
| Subdomain-specific cache rule deployed | Completed |
| Edge TTL | 7 days |
| Test image uploaded to R2 | Completed |
| Image served through custom domain | Verified |
| `CF-Cache-Status: HIT` | Verified |
| Bucket-scoped R2 token | Created |
| Environment variable names and values identified | Configured for the backend environment |
| Upload through Spring Boot | Verified by the tenant-isolated media E2E flow |
| Automatic cache purge on deletion | Implementation/verification pending |

## 13. Next steps

1. Repeat the media E2E flow in staging before production deployment.
2. Implement or verify image replacement, deletion, and cache purging where needed.
3. Configure R2 usage monitoring and billing alerts.
4. When the permanent domain becomes available, connect its new image subdomain and update `MEDIA_R2_PUBLIC_BASE_URL`. Existing objects can remain in the same bucket.

---

**Scope:** This guide documents the Cloudflare dashboard setup, backend credentials, and the verified local E2E media flow. It does not replace staging or production validation.
