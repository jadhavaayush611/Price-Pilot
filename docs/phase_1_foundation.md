# Phase 1 — Foundation Setup

This document outlines the foundation setup for the PricePilot product price comparison platform.

---

## 1. Purpose

The objective of Phase 1 is to initialize the full-stack architecture of PricePilot. This establishes:
- A clean, layered Spring Boot backend integrating Spring Data JPA, PostgreSQL, and Spring Security.
- An interactive React + TypeScript + Vite frontend configured with Tailwind CSS v4, routing, TanStack Query, and Axios.
- A health monitoring endpoint connecting both layers.

---

## 2. Backend Architecture

The backend follows a layered package structure under `com.pricepilot` to ensure strict separation of concerns:
- **`config`**: Application and security configurations.
- **`common`**: Reusable base classes and controllers.
- **`exception`**: Global error handling framework.
- **`product`**, **`seller`**, **`pricing`**, **`search`**: Main business domain packages.

### Database Impact & BaseEntity
- All entities inherit from `BaseEntity`, which implements JPA Auditing to automatically track timestamps.
- Searchable fields utilize UUIDs for primary keys (`GenerationType.UUID`) to support modern scaling.

**BaseEntity Fields:**
- `id` (`UUID`, primary key, non-updatable)
- `createdAt` (`LocalDateTime`, non-updatable)
- `updatedAt` (`LocalDateTime`)

### API Documentation

#### Health Check Endpoint
- **URL:** `GET /api/v1/health`
- **Authentication:** Publicly accessible (configured in `SecurityConfig`).
- **Response Format:**
  ```json
  {
    "status": "UP"
  }
  ```

---

## 3. Frontend Architecture

The frontend is built as a single-page application using React, TypeScript, and Vite. 

### Styling & Aesthetics
- Styled using Tailwind CSS v4 variables with a dark, premium, and minimal theme inspired by Vercel and Linear.
- Smooth transitions and interactive cards are built using Framer Motion.

### Folder Structure
- `src/components/`: Reusable components (e.g., global `Layout`).
- `src/pages/`: Interactive pages (`LandingPage`, `SearchPage`, `ProductPage`).
- `src/services/`: API layers (`apiService` configured with Axios).
- `src/types/`: TypeScript type declarations (`Product`, `Seller`, `ProductPrice`).
- `src/lib/`: Global utilities (e.g., `cn` utility helper).

### Dynamic Integration
- The global layout queries the backend `/api/v1/health` check endpoint and renders a real-time status indicator:
  - 🟢 **Engine Connected**: Local backend API is up.
  - 🔴 **Local Offline**: Backend API is unreachable.

---

## 4. Local Development

### Prerequisites
- **Java:** JDK 25
- **Node.js:** v18 or newer
- **Database:** PostgreSQL (running on port `5432` with a database named `pricepilot`)

### Running the Backend
1. Create a PostgreSQL database named `pricepilot`.
2. Update the password if needed in `backend/src/main/resources/application-dev.properties`.
3. In the `backend` directory, run:
   ```bash
   ./mvnw spring-boot:run
   ```

### Running the Frontend
1. In the `frontend` directory, run:
   ```bash
   npm install
   npm run dev
   ```
