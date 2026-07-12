# PricePilot Product & Engineering Roadmap

This document outlines the high-level roadmap and future engineering plans for PricePilot.

---

## 🗺️ Project Milestones

### Phase 1: Architecture & API Core Foundation (Completed)
- [x] High-performance relational database design (PostgreSQL) with Flyway migration schemas.
- [x] Standard CRUD endpoints for Products, Sellers, and Price Points.
- [x] Search capabilities with multi-faceted filtering.
- [x] Dockerization of all core components (DB, Backend, Frontend).

### Phase 2: User Security & Interactive Features (Completed)
- [x] JWT-based stateless authentication flow with real-time account status checks.
- [x] Saved Products (Favorites) management.
- [x] Price Watchlists with target threshold validation (BOLA/IDOR protected).
- [x] High-fidelity React 19 Frontend with modern aesthetics and micro-animations.

### Phase 3: Machine Learning & AI Microservices (Completed)
- [x] FastAPI AI Assistant Chatbot with conversation memory isolation and prompt injection guards.
- [x] Hybrid Recommendation Engine (Collaborative Filtering + Content-Based).
- [x] Type-safe Python SDK mapping the complete API namespace.
- [x] Redis caching layer with strict serialization whitelist protections.

### Phase 4: Release Hardening & Repository Polish (Active - v1.0.0-rc1)
- [x] Repository structure audit & removal of duplicate documentation.
- [x] Line-ending normalization across environments via `.gitattributes`.
- [x] Community standards integration (LICENSE, CoC, Contributing, Issue templates).
- [x] Comprehensive OpenAPI / Swagger specifications and API documentation.

---

## 🔮 Future Enhancements (Post v1.0)

### 1. Real-time Notification Dispatcher
- [ ] Implement an asynchronous notification gateway (RabbitMQ/Kafka) to decouple price drop evaluations from main APIs.
- [ ] Add support for SMTP Email notifications.
- [ ] Integrate browser Push Notifications using WebPush protocols.

### 2. Machine Learning Refinement
- [ ] Migrate candidate scoring from in-memory processing to pgvector database-assisted ranking for scalability.
- [ ] Introduce a half-life exponential time-decay algorithm for trending rankings.
- [ ] Add A/B testing frameworks to evaluate recommendation performance.

### 3. Price Scraping & Extraction Pipelines
- [ ] Create a distributed scraping cluster (Scrapy / Playwright) to automatically ingest and update pricing across retailers.
- [ ] Establish ETL batch workers to clean, standardize, and load pricing datasets.

### 4. Localization & Multi-Currency Support
- [ ] Add dynamic currency conversion support utilizing real-time exchange rate APIs.
- [ ] Add multilingual interfaces for the React Frontend.
