# PricePilot

## Overview

PricePilot is a modern product price comparison platform that helps users discover the best available prices for products across multiple sellers.

Users can:

* Search products
* Compare seller prices
* View discounts and offers
* Sort by best deals
* Redirect to seller websites
* Track product pricing (future)
* Receive price alerts (future)

The platform is not a marketplace and does not process payments.

Its primary objective is to act as a search and comparison engine for consumer products.

---

## Tech Stack

### Backend

* Java 21
* Spring Boot 3
* Spring Data JPA
* Hibernate
* PostgreSQL
* Maven

### Frontend

* React
* Vite
* TypeScript
* TailwindCSS
* ShadCN UI
* Framer Motion

### Future Technologies

* Redis
* Docker
* AWS
* Vector Databases
* AI Recommendation Engine

---

## Project Goals

### Technical Goals

* Learn production-grade Spring Boot development
* Implement layered architecture
* Apply JPA relationships correctly
* Build scalable REST APIs
* Practice PostgreSQL optimization
* Build a modern React frontend

### Product Goals

* Fast search experience
* Clean and minimal UI
* Reliable product comparison
* Seller redirection
* Extensible architecture

---

## Core Features

### MVP

* Product search
* Product listing
* Seller listing
* Price comparison
* Discount calculation
* Seller redirection

### Future Features

* Authentication
* User profiles
* Search history
* Saved products
* Price alerts
* Affiliate integrations
* AI-powered recommendations
* Product trend analysis

---

## Local Development

### Backend

```bash
cd backend

./mvnw spring-boot:run
```

### Frontend

```bash
cd frontend

npm install
npm run dev
```

---

## Repository Structure

pricepilot/

├── backend/
├── frontend/
├── docs/
├── README.md
├── ROADMAP.md
├── ARCHITECTURE.md
└── INSTRUCTIONS.md

---

## Learning Objectives

This project serves as a practical backend engineering project focused on:

* REST APIs
* Spring Boot internals
* Database design
* System design principles
* Full-stack integration
* Production readiness

---

## Status

Current Stage:

* **Phase 1 — Foundation Setup**: Complete
* Next up: **Phase 2 — Product Domain & CRUD APIs**