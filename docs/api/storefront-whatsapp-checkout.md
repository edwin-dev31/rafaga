# Storefront Checkout & Pre-WhatsApp Flow (Frontend Guide)

This guide documents the expected frontend workflow for storefront purchases that finish on WhatsApp, including optional customer contact capture and the public order receipt link.

---

## 1. Recommended Pre-WhatsApp Flow

1. **Build the guest cart**
   - `GET /api/storefront/carts/current`
   - `PUT /api/storefront/carts/current/items`
   - `DELETE /api/storefront/carts/current/items/{itemId}`
   - Cart ownership is bound to the `HttpOnly` cookie `rafaga_guest_session` and scoped to the store resolved from the `Host` header.

2. **Show a pre-WhatsApp modal/popup before redirecting**
   - When the shopper clicks **"Comprar por WhatsApp"**, open a lightweight form modal **before** calling checkout / opening WhatsApp.
   - Ask for:
     - **Name (`fullName`)** — **minimum required field** in the frontend modal.
     - **Phone (`phone`)** — optional (recommended for WhatsApp follow-up).
     - **Email (`email`)** — optional.
   - Do **not** assume the customer will manually type their name or phone inside the WhatsApp chat.

3. **Execute checkout in the backend**
   - Call `POST /api/storefront/carts/current/checkout` with an **optional** JSON body carrying the popup fields:
     ```json
     {
       "fullName": "Ana Pérez",
       "phone": "3001234567",
       "email": "ana@example.com"
     }
     ```
   - The backend atomically:
     - If `fullName` is provided, creates or reuses a tenant-scoped customer record (`customers`) and links it to the order (`customerId`). If omitted/blank, leaves `customerId = null` without error.
     - Marks the cart as `CHECKED_OUT`.
     - Creates a single idempotent order (`PENDING`) and reserves stock for every line.
     - Generates a 256-bit unguessable `orderPublicToken`.
   - Response includes `orderId` and `orderPublicToken` (if stock is insufficient, returns `409 Conflict` and keeps the cart `ACTIVE`).

4. **Build the WhatsApp message on the frontend**
   - Compose the pre-filled WhatsApp link (`https://wa.me/<storePhone>?text=...`) using the form data + the shareable receipt URL:
     ```text
     Hola, soy Ana Pérez, mi número es 3001234567.
     Acabo de hacer este pedido: https://<store-domain>/orden/<orderPublicToken>
     ```
   - Both the shopper and the store manager can open that link to inspect the exact items, quantities, prices, and current order status.

5. **Render the public order receipt page**
   - The frontend route `/orden/:publicToken` fetches:
     ```http
     GET /api/storefront/orders/{publicToken}
     ```
   - Returns a **read-only, non-PII** receipt (`storeName`, `orderId`, `reference`, `status`, `subtotal`, `total`, `items[]`, timestamps) with `Cache-Control: no-store, private` and `X-Robots-Tag: noindex, nofollow`.
   - The token is cryptographically random (256-bit) and scoped to the tenant resolved by `Host`, so customers cannot guess or view other shoppers' orders.

---

## 2. Graceful Degradation (If a Frontend Skips the Pre-WhatsApp Form)

- **No backend error on checkout:** `POST /api/storefront/carts/current/checkout` does **not** fail if the body is omitted or `fullName` is blank. The order is still created as a guest order (`customerId = null`) with its `orderId` and `orderPublicToken`.
- **Expected UI/feature behavior when customer data is absent:**
  - Do **not** fabricate placeholder names/emails or throw runtime errors.
  - Features that require customer identity (e.g., customer directory lookup, per-customer order history, or personalized WhatsApp greeting) must explicitly indicate that **the capability is unavailable because no customer data was provided** (or fall back to a generic WhatsApp message containing only the order reference and receipt link).
