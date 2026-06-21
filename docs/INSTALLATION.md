# PricePilot Local Installation Guide

This guide explains how to install and run PricePilot on your local development machine step-by-step, without Docker. If you wish to deploy or run using containers, see the [Deployment Guide](DEPLOYMENT.md).

---

## Prerequisites

Ensure you have the following installed on your machine:
1. **Java Development Kit (JDK) 25**: Download from [Eclipse Temurin (Adoptium)](https://adoptium.net/) or Oracle. Verify using `java -version`.
2. **Node.js (version 20 or higher)**: Verify using `node -v` and `npm -v`.
3. **PostgreSQL (version 15 or higher)**: Make sure it's running locally on port `5432`.
4. **Git** (for version control).

---

## 1. Database Setup

1. Open your PostgreSQL terminal (pgAdmin, psql, or similar client).
2. Create a new database named `pricepilot`:
   ```sql
   CREATE DATABASE pricepilot;
   ```
3. By default, the application connects using the username `postgres` and password `postgres`. If your local setup is different, update the database configuration in the application properties (see below).

---

## 2. Backend Installation (Spring Boot)

1. Navigate to the backend directory:
   ```bash
   cd backend
   ```
2. Configure application properties:
   Check `src/main/resources/application-dev.properties`. If your postgres username/password differs from `postgres`, update these properties:
   ```properties
   spring.datasource.username=your_postgres_username
   spring.datasource.password=your_postgres_password
   ```
3. Compile and build the project using the Maven Wrapper:
   * **Windows:**
     ```cmd
     mvnw.cmd clean install
     ```
   * **macOS / Linux:**
     ```bash
     chmod +x mvnw
     ./mvnw clean install
     ```
4. Run the Spring Boot application:
   * **Windows:**
     ```cmd
     mvnw.cmd spring-boot:run
     ```
   * **macOS / Linux:**
     ```bash
     ./mvnw spring-boot:run
     ```
5. Verify the backend is running by opening:
   `http://localhost:8080/api/v1/health`
   You should see:
   ```json
   { "status": "UP" }
   ```

---

## 3. Frontend Installation (React + Vite)

1. Navigate to the frontend directory:
   ```bash
   cd ../frontend
   ```
2. Install the package dependencies:
   ```bash
   npm install
   ```
3. Start the Vite development server:
   ```bash
   npm run dev
   ```
4. Access the frontend by opening your web browser and going to:
   `http://localhost:5173/`

---

## Environment Customizations
If you want to configure Vite to call a different backend API url, create a `.env.local` file inside the `frontend` directory:
```env
VITE_API_BASE_URL=http://localhost:8080/api/v1
```
Vite will automatically detect this variable on the next launch.
