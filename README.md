# AuraPay

AuraPay is a standalone checkout and payment-management application being built for the owner's AuraVision AI website first.

## Initial goal

A customer chooses an AuraVision AI credit pack, AuraPay creates an order, the customer pays using a merchant QR/payment flow, AuraPay verifies the payment through a trusted bank or authorised payment-network integration, and AuraVision AI adds the purchased credits once.

**This repository does not yet process real payments.** The current code is a development foundation only.

## Current implementation

- Java 17 + Spring Boot 3.5.x Maven project.
- `GET /api/health`: reports that AuraPay is running and real payments are disabled.
- `GET /api/credit-packs`: returns a starter demo catalogue. Amounts are integer INR paise.
- Basic Spring Boot tests for these endpoints.
- `.gitignore` excludes local environment files and build output.

## Important payment boundary

A QR image by itself cannot reliably tell the website that a particular order was paid. Automatic crediting requires a trusted transaction confirmation source, such as an approved bank/merchant integration or authorised payment-network partner with transaction-status notifications or an API.

AuraPay will own its checkout interface, order lifecycle, transaction ledger, and credit-grant coordination. It will not pretend to move money directly without payment-network access, and it will not treat screenshots, client redirects, or user-entered UPI reference numbers alone as proof of payment.

## Safety rules

- Real payments are disabled until a suitable bank/payment-network integration is approved and implemented.
- Never store card numbers, CVV, UPI PINs, banking passwords, or other payment credentials.
- Never trust a browser redirect or client-submitted payment status as proof of payment.
- Grant credits only after trusted server-side verification confirms the correct order, amount, currency, and successful transaction.
- Credit grants must be idempotent so duplicate notifications cannot add credits twice.
- Keep secrets in deployment environment variables or a secrets manager; never commit them to Git.
- Store monetary amounts as integer minor units (paise) with an explicit currency.
- Do not collect, pool, hold, or settle money for third-party merchants.
- Do not enable live mode until merchant onboarding, security review, reconciliation, refund handling, and end-to-end tests are complete.

## Run locally on Windows

Requirements: JDK 17 and Maven installed.

From the repository folder, run:

```powershell
mvn spring-boot:run
```

Then open:

- `http://localhost:8080/api/health`
- `http://localhost:8080/api/credit-packs`

To run tests:

```powershell
mvn test
```

## Roadmap

1. **Foundation (current):** Spring Boot app, health endpoint, demo credit-pack catalogue, and tests.
2. **Order workflow:** create unique pending orders with server-controlled prices and authenticated user association.
3. **Development checkout:** show an order and a clearly labelled simulated payment flow. Simulation must never be mistaken for a real payment.
4. **QR payment discovery:** obtain bank guidance on merchant QR, dynamic QR support, transaction references, status API/webhooks, onboarding, and fees.
5. **Verified transaction ledger:** persist orders/events, validate trusted payment confirmations, handle duplicates and refunds, and reconcile with bank records.
6. **AuraVision AI integration:** authenticate server-to-server requests and credit the correct user only after verified payment.
7. **Live readiness:** security review, HTTPS, persistent database, secret management, monitoring, backups, reconciliation, and approved live credentials.

## Regulatory note

Using AuraPay to accept payment for the owner's own products is different from operating a payment aggregator for other merchants. The actual obligations depend on the payment flow and business model. Obtain qualified guidance and the required bank/partner approvals before live transactions. If AuraPay later collects or settles funds for third-party merchants, separately review RBI authorisation and compliance requirements.


## Local AI image-generation test lab (isolated, not for deployment)

This experimental page uses a **local ComfyUI server** on your Windows PC. It is separate from AuraVision AI and does not use payment endpoints, credits, Cloudflare, or any hosted image API. The controller is disabled unless the dedicated Spring profile is enabled.

### Requirements

- Python/ComfyUI installed locally with NVIDIA GPU support.
- A Stable Diffusion 1.5 checkpoint installed in `ComfyUI/models/checkpoints`.
- JDK 17 and Maven.

### Run on Windows

1. Start ComfyUI locally, listening only on loopback, at `http://127.0.0.1:8188`. For your GTX 1650 and 8 GB RAM, start with ComfyUI's low-VRAM mode and use a 512×512 image.
2. In the AuraPay repository folder, start the test-only profile:

   ```powershell
   mvn spring-boot:run "-Dspring-boot.run.arguments=--spring.profiles.active=local-image-lab"
   ```

3. Open `http://localhost:8080/local-image-lab.html`.

The profile enables `/api/local-image-lab/*` and uses the checkpoint name in `application-local-image-lab.properties`. Change that name to exactly match a checkpoint installed locally. The first generation may be slow.

**Important:** do not deploy this branch/profile to a public service. The local image lab is intended for a developer's own PC and should remain disabled on Render. This branch is a testing branch and is not the default branch.
