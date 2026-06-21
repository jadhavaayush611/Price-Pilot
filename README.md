# PricePilot

PricePilot is a modern, high-performance product price comparison search engine that compiles and standardizes pricing across multiple sellers, highlighting the best available discounts for consumer products.

---

## 🚀 Quick Start with Docker

You can launch the complete database, API, and React frontend with a single command:

```bash
docker compose up --build
```

* **Frontend UI:** `http://localhost/`
* **Backend API Health Check:** `http://localhost:8080/api/v1/health`

For more configuration details, check out the [Deployment Guide](docs/DEPLOYMENT.md).

---

## 📖 Project Documentation

Detailed guides are available to help you understand, run, or extend the project:

* 📐 **[Architecture Overview](ARCHITECTURE.md)**: Explore system design, database models, ER diagrams, and backend layering.
* 💾 **[Installation Guide](docs/INSTALLATION.md)**: Steps to install and run the project manually on your host machine (without Docker).
* 🚢 **[Deployment Guide](docs/DEPLOYMENT.md)**: Detailed instructions on Docker builds, container health checks, and port customizations.
* 🔌 **[API Documentation](docs/API_DOCUMENTATION.md)**: Full REST API specs for products, sellers, prices, search parameters, and exception handling.

---

## 🛠️ Tech Stack

### Backend
* **Java 25** (latest LTS standard)
* **Spring Boot 4.1.0** (REST Framework & MVC)
* **Spring Data JPA** (Data persistence layer)
* **Hibernate 7.x** (ORM dialect mapper)
* **PostgreSQL 15** (Source of truth)
* **Maven** (Dependency builder)

### Frontend
* **React 19**
* **Vite 8**
* **TypeScript**
* **Tailwind CSS v4** (Utility layout engine)
* **ShadCN UI** (Component structure)
* **Framer Motion** (Micro-interactions and transitions)

---

## 📁 Repository Structure

```
PricePilot/
├── backend/            # Spring Boot Maven application
│   ├── src/            # Java classes, properties, and tests
│   ├── Dockerfile      # Backend container instructions
│   └── pom.xml         # Maven project descriptors
├── frontend/           # React TypeScript application
│   ├── src/            # Components, pages, and services
│   ├── Dockerfile      # Frontend container instructions
│   ├── nginx.conf      # Routing rules for production static server
│   └── package.json    # Frontend dependency mappings
├── docs/               # System documentation
│   ├── API_DOCUMENTATION.md
│   ├── INSTALLATION.md
│   └── DEPLOYMENT.md
├── docker-compose.yml  # Local multi-container orchestration
├── .env                # Global configuration environment variables
├── ARCHITECTURE.md     # Architecture documentation
└── README.md           # Project overview
```

---

## 🌟 Core Features

* **Advanced Search Filters:** Filter by category, brand, and search keywords.
* **Smart Sorting:** Sort search results dynamically by lowest price or highest discount.
* **Best Deal Badging:** Identifies and tags the absolute best discount option automatically.
* **Direct Redirections:** Quick redirection paths to vendor websites.
* **Comprehensive API Validation:** Robust input validation and global exception handlers.
* **Containerized Health Checks:** Dedicated health check paths mapping server status.