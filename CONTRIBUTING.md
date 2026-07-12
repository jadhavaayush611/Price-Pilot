# Contributing to PricePilot

First off, thank you for considering contributing to PricePilot! It's people like you who make open-source projects such an amazing place to learn, inspire, and create.

We want to make contributing to PricePilot as easy and transparent as possible, whether it's:
- Reporting a bug
- Discussing the current state of the code
- Submitting a fix
- Proposing new features
- Improving documentation

---

## Code of Conduct

By participating in this project, you agree to abide by our [Code of Conduct](CODE_OF_CONDUCT.md). Please report any unacceptable behavior to the project maintainers.

---

## How Can I Contribute?

### 1. Reporting Bugs
* **Check existing issues** to make sure the bug hasn't already been reported.
* Use the **Bug Report Template** when creating a new issue.
* Describe the **reproduction steps**, **expected behavior**, and **actual behavior**.
* Attach screenshots or logs if applicable.

### 2. Suggesting Enhancements
* Open a discussion or create a **Feature Request** issue to propose changes.
* Provide a clear description of the problem your enhancement solves and its benefits.

### 3. Submitting Pull Requests
We follow a standard fork-and-pull workflow:
1. **Fork** the repository to your own GitHub account.
2. **Clone** your fork locally:
   ```bash
   git clone https://github.com/your-username/Price-Pilot.git
   cd Price-Pilot
   ```
3. Create a **feature branch** named descriptively (e.g., `feature/analytics-caching` or `fix/jwt-auth-expiration`):
   ```bash
   git checkout -b feature/your-feature-name
   ```
4. Implement your changes, adhering to code style guidelines and writing tests.
5. Verify that all tests pass locally.
6. Commit your changes using **Conventional Commits** formatting (see below).
7. Push to your fork:
   ```bash
   git push origin feature/your-feature-name
   ```
8. Open a **Pull Request** against the `master` branch of the main repository.

---

## Development Guidelines

PricePilot is composed of three main microservices:
1. **Java Backend** (Spring Boot 4.1.0, Java 21)
2. **React Frontend** (React 19, Vite 8, Tailwind CSS v4)
3. **AI Service** (FastAPI, Python 3.11+)

### Environment Prerequisites
- **Java SE Development Kit (JDK) 21**
- **Node.js** (v18+ or v20+ recommended)
- **Python** (v3.10 or v3.11)
- **Docker & Docker Compose** (optional, but recommended)

### Coding Standards
* **Java**: Follow standard Java coding conventions. Indent with 4 spaces. Ensure all files end with a newline.
* **Python**: Follow [PEP 8](https://peps.python.org/pep-0008/) style guidelines. Use `black` or `ruff` for formatting.
* **React / TypeScript**: Indent with 2 spaces. Run `npm run lint` before committing.

### Commit Message Format
We use [Conventional Commits](https://www.conventionalcommits.org/) format. This ensures clean, readable repository history:
* `feat`: A new feature (e.g., `feat(api): add watchlist trigger endpoint`)
* `fix`: A bug fix (e.g., `fix(auth): sanitize JWT token from error logs`)
* `docs`: Documentation updates (e.g., `docs(readme): update setup instructions`)
* `style`: Code formatting changes (formatting, missing semi-colons, no logic change)
* `refactor`: Refactoring code (neither fixes a bug nor adds a feature)
* `test`: Adding missing tests or correcting existing tests
* `chore`: Build process, dependency updates, or auxiliary tool updates

---

## Running Tests

Before submitting a Pull Request, please verify your changes do not break existing functionality.

### Backend Tests
Navigate to `/backend` and run:
```bash
./mvnw clean test
```

### Python SDK & AI Service Tests
Navigate to `/pricepilot-python-sdk` and run:
```bash
pytest tests/
```

Navigate to `/pricepilot-ai` and run:
```bash
pytest tests/
```

### Frontend Tests
Navigate to `/frontend` and run:
```bash
npm run test
```

---

## Licensing
By contributing to PricePilot, you agree that your contributions will be licensed under the project's [MIT License](LICENSE).
