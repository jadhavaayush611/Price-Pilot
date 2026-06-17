# PricePilot Architecture

## System Vision

PricePilot follows a clean layered architecture designed for maintainability, scalability, and backend engineering best practices.

---

# High Level Architecture

User
│
▼
React Frontend
│
▼
Spring Boot REST API
│
▼
Service Layer
│
▼
Repository Layer
│
▼
PostgreSQL

---

# Backend Architecture

## Layer Structure

Controller
→ Service
→ Repository
→ Database

---

## Controller Layer

Responsibilities:

* Receive HTTP requests
* Validate request structure
* Return HTTP responses

Rules:

* No business logic
* No database access

---

## Service Layer

Responsibilities:

* Business logic
* Validation rules
* Product search logic
* Price comparison logic

Examples:

* Calculate discount percentages
* Aggregate seller results
* Sort product pricing

---

## Repository Layer

Responsibilities:

* Database interaction
* Query execution
* Pagination
* Search optimization

Technology:

* Spring Data JPA
* JPQL
* Native Queries (when required)

---

## Database Layer

Technology:

* PostgreSQL

Primary Goal:

* Source of truth

---

# Domain Model

## Product

Represents a searchable product.

Fields:

* id
* name
* brand
* description
* category
* imageUrl

---

## Seller

Represents a marketplace or vendor.

Fields:

* id
* name
* websiteUrl
* logoUrl

---

## ProductPrice

Represents seller-specific pricing.

Fields:

* id
* productId
* sellerId
* currentPrice
* originalPrice
* discountPercentage
* productUrl
* lastUpdated

---

# Relationships

Product

1 → Many ProductPrice

Seller

1 → Many ProductPrice

ProductPrice

Many → 1 Product

Many → 1 Seller

---

# Search Flow

User Search
↓
Search Controller
↓
Search Service
↓
Repository Query
↓
PostgreSQL
↓
DTO Mapping
↓
Frontend

---

# Frontend Architecture

## Design Principles

Inspired by:

* Vercel
* Linear
* Stripe

Characteristics:

* Minimal
* Fast
* Premium
* Responsive
* Motion-driven

---

## Structure

src/

components/
features/
pages/
services/
hooks/
types/
lib/

---

# Future Architecture

Phase 2

* JWT Authentication
* User Profiles
* Search History

Phase 3

* Redis Caching
* Price Alerts
* Background Jobs

Phase 4

* AI Recommendations
* Product Similarity Search
* Semantic Search

Phase 5

* Microservice Migration (Optional)

---

# Non Functional Goals

Performance

* Search response < 500ms

Scalability

* Horizontal scaling support

Maintainability

* Clear separation of concerns

Security

* JWT authentication
* Input validation

Reliability

* Global exception handling
* Logging
* Monitoring