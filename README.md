# RiskLens: Credit Risk Assessment

> A full-stack web app that scores loan applications with an explainable machine-learning model trained on 32,000 real loans, and tracks your portfolio's risk distribution.

![Java](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-18-61DAFB?logo=react&logoColor=black)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)
![Tailwind CSS](https://img.shields.io/badge/Tailwind%20CSS-3-06B6D4?logo=tailwindcss&logoColor=white)
![scikit-learn](https://img.shields.io/badge/scikit--learn-1.x-F7931E?logo=scikitlearn&logoColor=white)
![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)
[![CI](https://github.com/IbragimAliyev/credit-risk-web-app/actions/workflows/ci.yml/badge.svg)](https://github.com/IbragimAliyev/credit-risk-web-app/actions/workflows/ci.yml)

---

## Features

- **JWT authentication**: register and log in; stateless, BCrypt-hashed passwords.
- **ML risk scoring**: a logistic regression model trained on the [Kaggle Credit Risk Dataset](https://www.kaggle.com/datasets/laotse/credit-risk-dataset) estimates each applicant's **probability of default** (test AUC **0.816**, well calibrated), then labels it **LOW / MEDIUM / HIGH**.
- **Explainable results**: the score splits exactly into per-factor contributions, so you can see *which* factors raised or lowered the risk.
- **Python → Java model export**: trained in scikit-learn, served natively in Java. A parity test proves both give identical probabilities.
- **Per-user data isolation**: users only ever see their own applications.
- **Dashboard**: KPI tiles, a color-coded result card, a history table, and a Chart.js bar chart of the risk distribution.
- **Input validation** with field-level error messages from the API.
- **Versioned schema migrations** with Flyway; Hibernate only validates the schema.
- **Tested against real PostgreSQL** with Testcontainers, plus CI on every push.
- **Docker Compose** for PostgreSQL, with an optional full-stack profile.

![RiskLens dashboard](docs/screenshots/dashboard.png)

## Architecture

```
┌──────────────────────┐   HTTPS/JSON + Bearer JWT   ┌──────────────────────────┐   JPA   ┌────────────┐
│  React 18 + Vite     │ ──────────────────────────▶ │  Spring Boot 3 REST API  │ ──────▶ │ PostgreSQL │
│  Tailwind, Chart.js  │ ◀────────────────────────── │  Security · JWT · JPA    │         └────────────┘
│  axios (localhost:3000)                            │  (localhost:8080)        │
└──────────────────────┘                             └──────────────────────────┘
```

**Backend** (`backend/`), packaged by feature:

| Package | Responsibility |
|---|---|
| `auth` | `/auth/register`, `/auth/login`, issues JWTs |
| `security` | JWT creation/validation, request filter, user lookup |
| `application` | `LoanApplication` entity, repository, service, REST controller |
| `risk` | `RiskModel` loads the exported model and scores applicants; `/api/model` reports its metrics |
| `config` | Security filter chain, CORS, typed app properties |
| `exception` | Global JSON error handling (400 / 401 / 404 / 409) |

**Frontend** (`frontend/`): `AuthContext` keeps the JWT in `localStorage`. An axios interceptor attaches it to every request and logs the user out on a 401.

**ML** (`ml/`): Python scripts that download the data, train and evaluate the model, and export it as JSON into the backend's resources.

### Risk model

```
 ml/train.py (scikit-learn)  ──exports──▶  credit_risk_model.json  ──loaded by──▶  RiskModel.java  ──▶  API
```

- **Inputs:** age, annual income, loan amount, loan purpose, years employed, credit history length, home ownership, previous default.
- **Output:** the probability that the applicant defaults. Below 15% is `LOW`, 15–40% is `MEDIUM`, and 40% or more is `HIGH`.
- **Performance on 6,482 held-out loans:** AUC 0.816, Gini 0.63. Predicted and observed default rates match per band: LOW 6.5% vs 8.0%, MEDIUM 25.3% vs 21.6%, HIGH 61.4% vs 62.6%.
- **Why logistic regression rather than gradient boosting (AUC 0.905)?** Every score splits exactly into per-factor contributions, which lending needs in order to explain decisions.

The full model card in **[ml/README.md](ml/README.md)** covers the data cleaning, feature choices (including what was deliberately left out and why), metrics, charts and limitations.

| ROC curve | Calibration |
|---|---|
| ![ROC curve](docs/model/roc_curve.png) | ![Calibration](docs/model/calibration.png) |

## Prerequisites

| Tool | Version | Needed for |
|---|---|---|
| Java JDK | 17+ | Backend (without Docker) |
| Maven | 3.9+ | Backend (without Docker) |
| Node.js | 18+ (20/22 recommended) | Frontend (without Docker) |
| Docker + Docker Compose | recent | Database and/or full stack |
| PostgreSQL | 14+ | Only if you don't use Docker |
| Python | 3.10+ | Only to retrain the model (a trained model is already included) |

## Setup & Run

### 1. Clone and configure

```bash
git clone https://github.com/IbragimAliyev/credit-risk-web-app.git
cd credit-risk-web-app
cp .env.example .env      # then set a strong JWT_SECRET (≥ 32 chars)
```

The root `.env` is read by Docker Compose, the backend (via `spring.config.import`) and the frontend (Vite `envDir`). Every variable has a sensible default for local development.

### Option A: Full stack with Docker

```bash
docker compose --profile full up -d --build
```

| Service | URL |
|---|---|
| Frontend | http://localhost:3000 |
| Backend API | http://localhost:8080 |
| PostgreSQL | localhost:5432 |

Stop everything with `docker compose --profile full down` (add `-v` to delete the database volume).

### Option B: Database in Docker, app run locally (recommended for development)

```bash
# 1. Start PostgreSQL only
docker compose up -d

# 2. Start the backend (new terminal)
cd backend
mvn spring-boot:run

# 3. Start the frontend (new terminal)
cd frontend
npm install
npm run dev
```

Open **http://localhost:3000**, create an account and submit your first application.

### Option C: Without Docker

1. Install PostgreSQL and create a database and user:
   ```sql
   CREATE USER creditrisk WITH PASSWORD 'creditrisk';
   CREATE DATABASE creditrisk OWNER creditrisk;
   ```
2. Set `DB_URL`, `POSTGRES_USER` and `POSTGRES_PASSWORD` in `.env` if they differ from the defaults.
3. Run the backend and frontend exactly as in Option B, steps 2–3.

The schema is created and upgraded automatically on startup by **Flyway**, using the migrations in `backend/src/main/resources/db/migration`. To change the schema, add a new file such as `V2__add_column.sql`. Never edit a migration that has already been applied.

### Running tests

```bash
cd backend
mvn test
```

This runs:
- **Model tests**: Java must reproduce the Python-trained model's probabilities exactly, and risk must move in sensible directions (e.g. a previous default raises it).
- **API integration tests**: the full register → login → score → list flow, run twice: on in-memory **H2** (always), and on a real **PostgreSQL** started in Docker via **Testcontainers**. The PostgreSQL run is skipped automatically if Docker isn't installed; it always runs in CI.

**Continuous integration:** GitHub Actions ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)) runs the backend tests and builds the frontend on every push to `main` and on every pull request.

## Deployment

The repo includes a [Render](https://render.com) Blueprint ([`render.yaml`](render.yaml)) that deploys the database, API and frontend on the free tier in a few clicks. Step-by-step instructions are in **[docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)**.

## Environment variables

| Variable | Default | Description |
|---|---|---|
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | `creditrisk` | Database credentials |
| `DB_URL` | `jdbc:postgresql://localhost:5432/creditrisk` | JDBC URL used by the backend |
| `DATABASE_URL` | — | Alternative to `DB_URL` in `postgresql://user:pass@host:port/db` form, as hosting platforms provide it. Ignored if `DB_URL` is set |
| `PORT` | `8080` | Backend HTTP port (hosting platforms set this automatically) |
| `JWT_SECRET` | dev-only value | HMAC signing key, **≥ 32 characters**. Always override outside local dev |
| `JWT_EXPIRATION_MS` | `86400000` (24h) | Token lifetime |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Comma-separated allowed origins |
| `VITE_API_BASE_URL` | `http://localhost:8080` | API base URL used by the frontend |

## API Endpoints

All `/api/**` endpoints require `Authorization: Bearer <token>`.

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `POST` | `/auth/register` | — | Create an account. Body: `{ name, email, password }`. Returns `{ token, user }` |
| `POST` | `/auth/login` | — | Log in. Body: `{ email, password }`. Returns `{ token, user }` |
| `POST` | `/api/applications` | ✅ | Create and score a loan application |
| `GET` | `/api/model` | ✅ | Model version, test metrics (AUC, Gini, KS) and risk-band thresholds |
| `GET` | `/api/applications` | ✅ | List the current user's applications (newest first) |
| `GET` | `/api/applications/{id}` | ✅ | Get one application (404 if it isn't yours) |

<details>
<summary><b>Example: create an application</b></summary>

```http
POST /api/applications
Authorization: Bearer eyJhbGciOi...
Content-Type: application/json

{
  "applicantName": "Bob Smith",
  "age": 34,
  "income": 50000,
  "loanAmount": 20000,
  "loanIntent": "DEBTCONSOLIDATION",
  "employmentYears": 3,
  "creditHistoryYears": 6,
  "homeOwnership": "RENT",
  "previousDefault": false
}
```

```json
{
  "id": 1,
  "applicantName": "Bob Smith",
  "age": 34,
  "income": 50000,
  "loanAmount": 20000,
  "employmentYears": 3,
  "creditHistoryYears": 6,
  "previousDefault": false,
  "homeOwnership": "RENT",
  "loanIntent": "DEBTCONSOLIDATION",
  "defaultProbability": 0.71639,
  "riskScore": 72,
  "riskLabel": "HIGH",
  "breakdown": [
    { "factor": "Loan-to-income",        "contribution": 1.84,   "effect": "INCREASES_RISK", "detail": "Loan is 40% of annual income" },
    { "factor": "Home ownership",        "contribution": 0.511,  "effect": "INCREASES_RISK", "detail": "Rents" },
    { "factor": "Loan purpose",          "contribution": 0.427,  "effect": "INCREASES_RISK", "detail": "Debt consolidation" },
    { "factor": "Previous default",      "contribution": -0.193, "effect": "DECREASES_RISK", "detail": "No default on file" },
    { "factor": "Income",                "contribution": 0.095,  "effect": "INCREASES_RISK", "detail": "$50,000 per year" },
    { "factor": "Employment length",     "contribution": 0.01,   "effect": "INCREASES_RISK", "detail": "3 years employed" },
    { "factor": "Age",                   "contribution": -0.009, "effect": "DECREASES_RISK", "detail": "34 years old" },
    { "factor": "Credit history length", "contribution": 0.0,    "effect": "INCREASES_RISK", "detail": "6 years of credit history" }
  ],
  "modelVersion": "lr-2026-11-20",
  "createdAt": "2026-11-20T10:15:30.123Z"
}
```

`contribution` is the factor's effect on the log-odds of default compared with an average applicant. The breakdown is sorted by impact.

**Validation rules:** age 18–100 · income and loan amount > 0 · employment and credit history 0–60 years · `homeOwnership` is one of `RENT`, `MORTGAGE`, `OWN`, `OTHER` · `loanIntent` is one of `DEBTCONSOLIDATION`, `EDUCATION`, `HOMEIMPROVEMENT`, `MEDICAL`, `PERSONAL`, `VENTURE` · `previousDefault` is required.

Errors use one consistent shape:

```json
{ "status": 400, "message": "Validation failed", "errors": { "age": "must be greater than or equal to 18" }, "timestamp": "..." }
```
</details>

## Project structure

```
.
├── backend/                 Spring Boot API
│   ├── src/main/java/com/creditrisk/
│   │   ├── application/     Loan application entity, service, controller
│   │   ├── auth/            Register / login
│   │   ├── config/          Security, CORS, properties
│   │   ├── exception/       Global error handling
│   │   ├── risk/            Scoring engine
│   │   ├── security/        JWT service + filter
│   │   └── user/            User entity + repository
│   ├── src/main/resources/db/migration/   Flyway SQL migrations
│   ├── src/main/resources/model/          Exported model (JSON)
│   └── Dockerfile
├── frontend/                React + Vite + Tailwind SPA
│   ├── src/{api,components,context,pages,utils}
│   └── Dockerfile
├── ml/                      Data download, training, evaluation (Python)
├── docs/                    Deployment guide, model charts, screenshots
├── .github/workflows/       CI pipeline
├── docker-compose.yml
├── render.yaml              Render deployment Blueprint
├── .env.example
└── LICENSE
```

## Screenshots

**Sign in / register**

![Login page](docs/screenshots/login.png)

| Risk result with score breakdown | Portfolio risk distribution |
|---|---|
| ![Result card](docs/screenshots/result.png) | ![Distribution chart](docs/screenshots/chart.png) |

## License

Distributed under the **MIT License**. See [`LICENSE`](LICENSE) for details.
