# AuraPay

AuraPay is a private payment integration project intended initially for the owner's AuraVision AI website only.

## Scope and safety

- Initial use case: collect payments for AuraVision AI's own products/subscriptions through an authorized payment provider.
- AuraPay will own the checkout experience, internal order records, payment status workflow, and integration API.
- AuraPay will not store card numbers, CVV, UPI PINs, banking passwords, or other payment credentials.
- Do not process, pool, hold, or settle money for third-party merchants.
- Do not enable live payments until provider onboarding, merchant approval, production database, webhook verification, and end-to-end tests are complete.
- Test mode and simulated transactions must be clearly labeled and must never grant real paid access unless a trusted provider confirms a captured payment.

## Repository status

This repository starts as a clean foundation. AuraVision AI is a separate Spring Boot application and already contains a Razorpay checkout/billing implementation and a webhook endpoint. Before integrating AuraPay, inspect that implementation and design a minimal, backward-compatible API contract. Do not replace or disable the existing checkout without a tested migration plan.

## Planned phases

1. **Foundation:** define architecture, configuration, order/payment states, and security requirements.
2. **Test-only prototype:** create orders, show checkout UI, simulate success/failure/cancellation, and persist test transactions.
3. **AuraVision AI integration:** authenticate server-to-server requests; create orders from server-validated prices; map verified provider events to the correct AuraVision AI account.
4. **Provider sandbox:** integrate an authorized provider's test environment; verify signatures and webhooks on the server; make webhook processing idempotent.
5. **Production readiness:** use a persistent PostgreSQL database, HTTPS, managed secrets, access controls, audit logs, reconciliation, backups, refund/cancellation handling, and monitoring.
6. **Live payments:** enable only after the provider approves the merchant account and all sandbox/security checks pass.

## Non-negotiable payment rules

- Never trust a browser redirect or client-submitted payment status as proof of payment.
- Never grant credits or subscriptions until a verified provider event or server-side provider lookup confirms the correct order, amount, currency, and captured/paid status.
- Verify webhook signatures against the exact raw request body.
- Make webhook processing idempotent so repeated events cannot grant duplicate credits.
- Keep provider secrets in deployment environment variables or a secrets manager; never commit them to Git.
- Keep test and live credentials separate.
- Store amounts as integer minor units (for example, paise), with explicit currency.
- Log transaction identifiers and state changes, not sensitive payment credentials.

## Initial architecture proposal

- Backend: Java 17 + Spring Boot, matching AuraVision AI's current backend stack.
- Database: PostgreSQL for durable orders and payment events.
- API: authenticated server-to-server order creation plus a verified provider webhook endpoint.
- Checkout: AuraPay-branded hosted checkout that redirects to or embeds the provider's approved checkout flow.
- Provider: choose only after confirming merchant eligibility, supported payment methods, fees, settlement terms, and current onboarding requirements.

## Before live mode

Review the current RBI Payment Aggregator Directions and obtain qualified compliance advice if the business model changes to collecting or settling funds for other merchants. This repository is not itself authorization to operate a payment aggregator.
