# PricePilot Local Installation Guide

This guide explains how to install and run the complete PricePilot platform (Database, Redis, FastAPI AI Service, Backend, and Frontend) on your local development machine step-by-step, without Docker.

> [!NOTE]
> If you wish to deploy or run using containers (recommended), see the [Deployment Guide](DEPLOYMENT.md).

---

## Prerequisites

Ensure you have the following installed on your machine:
1. **Java Development Kit (JDK) 21 LTS**: Download from [Eclipse Temurin (Adoptium)](https://adoptium.net/) or Oracle. Verify using `java -version`.
2. **Node.js (version 20 or higher)**: Verify using `node -v` and `npm -v`.
3. **PostgreSQL (version 15 or higher)**: Make sure it's running locally on port `5432`.
4. **Redis**: Make sure it's running locally on port `6379`.
5. **Python 3.11**: Required to run the FastAPI AI service. Verify using `python --version`.
6. **Git** (for version control).

---

## 1. Database Setup

1. Open your PostgreSQL terminal (pgAdmin, psql, or similar client).
2. Create a new database named `pricepilot`:
   ```sql
   CREATE DATABASE pricepilot;
   ```
3. By default, the application connects using the username `postgres` and password `postgres`. If your local setup is different, update the database configuration in the application properties (see below).
4. **Database Migration (Flyway):** You do not need to run any manual DDL scripts or import SQL files. When you start the Spring Boot application, it will scan `src/main/resources/db/migration` and automatically run Flyway migrations (starting with version `V1.0__init.sql`) to configure all schemas, tables, keys, and indexes.

---

## 2. Redis Setup

Ensure Redis is installed and running locally on port `6379`.
* **Windows:** You can run Redis via WSL2 or use the native port of Redis.
* **macOS / Linux:** Start Redis using your system package manager:
  ```bash
  brew services start redis # macOS
  sudo systemctl start redis # Linux
  ```

---

## 3. FastAPI AI Service Installation

The AI service handles model inference and recommendations.

1. Navigate to the `pricepilot-ai` directory:
   ```bash
   cd pricepilot-ai
   ```
2. Create and activate a Python virtual environment:
   * **Windows:**
     ```cmd
     python -m venv venv
     venv\Scripts\activate
     ```
   * **macOS / Linux:**
     ```bash
     python3 -m venv venv
     source venv/bin/activate
     ```
3. Install dependencies:
   ```bash
   pip install -r requirements.txt
   ```
4. Set the `PYTHONPATH` environment variable so Python can find `pricepilot_ml` and the FastAPI app:
   * **Windows (Command Prompt):**
     ```cmd
     set PYTHONPATH=..;..:\pricepilot-ai
     ```
   * **Windows (PowerShell):**
     ```powershell
     $env:PYTHONPATH="..;..:\pricepilot-ai"
     ```
   * **macOS / Linux:**
     ```bash
     export PYTHONPATH=../:../pricepilot-ai
     ```
5. Run the FastAPI application using Uvicorn:
   ```bash
   uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload
   ```
6. Verify the AI service is running by opening:
   `http://localhost:8000/health`
   You should see a JSON response:
   ```json
   {
     "status": "UP"
   }
   ```

---

## 4. Backend Installation (Spring Boot)

1. Navigate to the backend directory:
   ```bash
   cd ../backend
   ```
2. Configure application properties:
   Check `src/main/resources/application-dev.properties`. If your postgres username/password or Redis/AI URLs differ from the defaults, update these properties:
   ```properties
   spring.datasource.username=your_postgres_username
   spring.datasource.password=your_postgres_password
   spring.data.redis.host=localhost
   pricepilot.ai.url=http://localhost:8000
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

## 5. Frontend Installation (React + Vite)

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
