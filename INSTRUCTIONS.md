# PricePilot - Development Instructions

## Project Vision

PricePilot helps users discover the latest prices of products across multiple sellers and redirects users to the seller website.

The application is not an e-commerce marketplace.

The application does not process payments.

The application acts as a product search and comparison platform.

---

## Core Principles

* Clean Architecture
* SOLID Principles
* DTO-first API design
* PostgreSQL as source of truth
* React frontend separated from backend
* Mobile-first responsive design
* Production-ready code
* Comprehensive documentation

---

## Backend Rules

Layer Structure:

Controller
→ Service
→ Repository

Never place business logic in controllers.

Always use constructor injection.

Use DTOs for all request and response payloads.

Never expose entities directly.

Use global exception handling.

Use validation annotations.

Use pagination for list endpoints.

---

## Database Rules

Use PostgreSQL.

All entities must contain:

* id
* createdAt
* updatedAt

Use UUIDs where practical.

Index searchable fields.

Normalize data appropriately.

---

## Frontend Rules

Use:

* React
* TypeScript
* TailwindCSS
* ShadCN/UI
* Framer Motion

Design inspiration:

* Vercel
* Linear
* Stripe

Design goals:

* Fast
* Minimal
* Premium
* Modern
* Smooth animations

Use feature-based folder structure.

Use React Query for API calls.

Avoid prop drilling.

---

## Documentation Requirements

Every major feature must include:

* Purpose
* Architecture
* API documentation
* Database impact

Store documents inside /docs.

---

## Code Quality

* Follow clean code practices
* Prefer readability over cleverness
* Add meaningful comments only where needed
* Avoid code duplication
* Keep methods small and focused

---

## Future Expansion

Planned:

* User accounts
* Search history
* Price alerts
* Product tracking
* Redis caching
* External affiliate integrations
* AI-assisted product recommendations