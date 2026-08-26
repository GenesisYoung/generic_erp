# Generic ERP — Development Process Document

> **Last Updated:** June 17, 2026  
> **Stage:** Early-to-Mid Development (v1.0.1-SNAPSHOT)

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Technology Stack](#2-technology-stack)
3. [Architecture](#3-architecture)
4. [Project Structure](#4-project-structure)
5. [Implemented Functionalities](#5-implemented-functionalities)
6. [Environment & Configuration](#6-environment--configuration)
7. [Development Workflow](#7-development-workflow)
8. [Current Stage & Roadmap](#8-current-stage--roadmap)

---

## 1. Project Overview

`generic_erp` is the **backend service** of the Generic ERP system. It is built with Spring Boot and exposes a RESTful JSON API consumed by the Vue 3 frontend (`generic-front`). At this stage the service is responsible for authentication, authorization, and user/navigation data. Additional ERP business modules (inventory, purchasing, HR, etc.) will be layered on top of this foundation.

---

## 2. Technology Stack

| Category | Technology | Version |
|---|---|---|
| Language | Java | 17 |
| Framework | Spring Boot | 4.0.5 |
| Security | Spring Security | (Boot-managed) |
| ORM | Spring Data JPA / Hibernate | (Boot-managed) |
| Database (prod) | MySQL | latest |
| Database (test) | H2 (in-memory) | (Boot-managed) |
| Authentication | JWT via JJWT | 0.12.6 |
| Password Hashing | Argon2 via BouncyCastle | 1.78.1 |
| OAuth2 | Spring OAuth2 Client | (Boot-managed) |
| Build Tool | Maven | (wrapper included) |
| Boilerplate Reduction | Lombok | (Boot-managed) |
| Monitoring | Spring Boot Actuator | (Boot-managed) |
| Messaging (planned) | Apache Kafka | (Boot-managed) |
| Env Vars | Spring Dotenv | 5.1.0 |

---

## 3. Architecture

The backend follows a classic **three-tier layered architecture**:

```
HTTP Request
    │
    ▼
┌─────────────────────────────────┐
│  JWT Auth Filter                │  ← validates Bearer token on every request
└────────────────┬────────────────┘
                 │
    ▼
┌─────────────────────────────────┐
│  Controller Layer               │  ← REST endpoints, request/response mapping
└────────────────┬────────────────┘
                 │
    ▼
┌─────────────────────────────────┐
│  Service Layer                  │  ← business logic, token management
└────────────────┬────────────────┘
                 │
    ▼
┌─────────────────────────────────┐
│  Repository Layer (JPA)         │  ← database access via Spring Data interfaces
└────────────────┬────────────────┘
                 │
    ▼
┌─────────────────────────────────┐
│  MySQL Database                 │  ← persistent storage
└─────────────────────────────────┘
```

### Security Flow

1. Client sends `POST /api/auth/login` with credentials.
2. `AuthenticationImpl` verifies the Argon2-hashed password.
3. On success, `JWTUtil` issues an **access token** (15 min) and a **refresh token** (7 days).
4. All subsequent requests carry the access token in the `Authorization: Bearer` header.
5. `JwtAuthenticationFilter` validates the token and populates the Spring Security context.
6. When the access token expires, the client calls `POST /api/auth/refresh/access` with the refresh token to get a new access token.

---

## 4. Project Structure

```
generic_erp/
├── pom.xml                                      # Maven build & dependency definition
├── mvnw / mvnw.cmd                              # Maven wrapper scripts
└── src/
    ├── main/
    │   ├── java/com/gsgd/generic_erp/
    │   │   ├── GenericErpApplication.java       # Spring Boot entry point
    │   │   ├── configuration/
    │   │   │   ├── AuthenticationService.java   # Auth service interface
    │   │   │   └── security/
    │   │   │       ├── JWTUtil.java             # Token generation & validation
    │   │   │       ├── JwtAuthenticationFilter.java
    │   │   │       ├── UserDetail.java          # Spring Security principal wrapper
    │   │   │       ├── WebSecurityConfiurer.java # Security filter chain & CORS config
    │   │   │       └── impl/
    │   │   │           ├── AuthenticationImpl.java
    │   │   │           └── CustomizedUserDetailServiceImpl.java
    │   │   ├── controller/auth/
    │   │   │   ├── AuthenticationController.java # /api/auth/* endpoints
    │   │   │   └── UserController.java           # /api/users/* endpoints
    │   │   ├── entity/auth/                     # JPA entities (mapped to DB tables)
    │   │   │   ├── User.java
    │   │   │   ├── Role.java
    │   │   │   ├── Permission.java
    │   │   │   ├── Action.java
    │   │   │   ├── Department.java
    │   │   │   ├── NavigationMenu.java
    │   │   │   ├── UserInfo.java
    │   │   │   ├── UserRole.java
    │   │   │   ├── UserDepartment.java
    │   │   │   ├── UserNavMenu.java
    │   │   │   ├── UserLoginLog.java
    │   │   │   ├── UserRefreshToken.java
    │   │   │   ├── ActionPermission.java
    │   │   │   └── RolePermission.java
    │   │   ├── service/auth/
    │   │   │   ├── AuthenticationService.java   # Auth business logic
    │   │   │   └── UserService.java             # User-related business logic
    │   │   ├── repository/auth/                 # Spring Data JPA repositories
    │   │   │   ├── UserRepository.java
    │   │   │   ├── RoleRepository.java
    │   │   │   ├── PermissionRepository.java
    │   │   │   ├── ActionRepository.java
    │   │   │   ├── DepartmentRepository.java
    │   │   │   ├── NavigationMenuRepository.java
    │   │   │   ├── UserRoleRepository.java
    │   │   │   ├── UserDepartmentRepository.java
    │   │   │   ├── UserNavMenuRepository.java
    │   │   │   ├── UserInfoRepository.java
    │   │   │   ├── ActionPermissionRepository.java
    │   │   │   └── RolePermissionRepository.java
    │   │   ├── dto/
    │   │   │   ├── UserDTO.java
    │   │   │   └── UserNavMenuDTO.java
    │   │   └── util/
    │   │       └── BasicResponse.java           # Generic API response envelope
    │   └── resources/
    │       └── application.yml                  # App & DB configuration
    └── test/
        └── resources/
            └── application-test.yml             # H2 test profile config
```

---

## 5. Implemented Functionalities

### 5.1 Authentication (`/api/auth`)

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/auth/login` | Authenticate with username + password. Returns JWT access token, refresh token, and user details. |
| `GET` | `/api/auth/expiration/remaining` | Returns remaining validity time (ms) for the current access token. |
| `POST` | `/api/auth/refresh/access` | Exchange a valid refresh token for a new access token. |
| `POST` | `/api/auth/refresh/refresh` | Rotate the refresh token itself. |

**Token Specifications:**
- Access token TTL: configurable via `ACCESS_TIMEOUT` env var (default 15 minutes)
- Refresh token TTL: configurable via `REFRESH_TIMEOUT` env var (default 7 days)
- Signing algorithm: HMAC-SHA (secret key via `AUTHENTICATION_SECRET_KEY` env var)
- Password hashing: Argon2

### 5.2 User (`/api/users`)

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/users/fetch/sidebar/menu` | Returns the navigation menu items assigned to the authenticated user. Used by the frontend sidebar. |

### 5.3 Security & RBAC

- **Role-Based Access Control**: Users are assigned one or more `Role` records. Each role carries a set of `Permission` entries.
- **Permission → Action mapping**: Fine-grained `Action` records are attached to permissions via `ActionPermission`.
- **Account locking**: Consecutive failed login attempts lock the user account.
- **User status**: Accounts can be set active or disabled.
- **CORS**: Pattern-based origin validation; configured in `WebSecurityConfiurer`.
- **Stateless sessions**: No server-side session; every request is authenticated via the JWT filter.

### 5.4 Data Model (Entities)

| Entity | Purpose |
|---|---|
| `User` | Core user credentials and status |
| `UserInfo` | Extended profile information |
| `Role` | Named role (e.g., ACCOUNTANT, HR) |
| `Permission` | Granular permission record |
| `Action` | Specific action allowed by a permission |
| `Department` | Organisational unit |
| `NavigationMenu` | Frontend sidebar menu item, supports i18n keys |
| `UserRole` | Many-to-many join: User ↔ Role |
| `UserDepartment` | Many-to-many join: User ↔ Department |
| `UserNavMenu` | Sidebar items visible to a specific user |
| `UserLoginLog` | Audit log of login events |
| `UserRefreshToken` | Persisted refresh tokens for validation & rotation |
| `ActionPermission` | Join: Action ↔ Permission |
| `RolePermission` | Join: Role ↔ Permission |

---

## 6. Environment & Configuration

### Required Environment Variables

| Variable | Description |
|---|---|
| `DB_URL` | JDBC connection URL (e.g. `jdbc:mysql://localhost:3306/erp`) |
| `DB_USERNAME` | Database username |
| `DB_PASSWORD` | Database password |
| `DB_DRIVER_CLASS_NAME` | JDBC driver (e.g. `com.mysql.cj.jdbc.Driver`) |
| `ACCESS_TIMEOUT` | Access token TTL in milliseconds |
| `REFRESH_TIMEOUT` | Refresh token TTL in milliseconds |
| `AUTHENTICATION_SECRET_KEY` | JWT HMAC signing secret (falls back to test key if absent) |

Set these in a `.env` file at project root (loaded by Spring Dotenv) or as system environment variables.

### Hibernate Schema Management

`ddl-auto: update` — Hibernate automatically applies incremental DDL changes on startup. **Do not use in a shared production environment without a migration strategy (e.g., Flyway/Liquibase).**

### Test Profile

Activate with `--spring.profiles.active=test`. Uses an H2 in-memory database with `create-drop` DDL; no environment variables required.

---

## 7. Development Workflow

### Running Locally

```bash
# 1. Copy and fill in environment variables
cp .env.example .env   # create this file if it doesn't exist

# 2. Start a local MySQL instance (Docker example)
docker run -d --name erp-mysql \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=erp \
  -p 3306:3306 mysql:latest

# 3. Run the application
./mvnw spring-boot:run

# The API is available at http://localhost:8080
```

### Running Tests

```bash
./mvnw test
```

Tests run against the H2 in-memory database via the `test` Spring profile.

### Building a Production JAR

```bash
./mvnw clean package -DskipTests
java -jar target/generic_erp-1.0.1-SNAPSHOT.jar
```

### Branch Strategy

- `main` — stable, reviewed code
- `claude/amazing-mayer-v3gn5l` — current active development branch
- Feature branches: branch off `main`, open a PR back to `main`

### Code Conventions

- Entities live under `entity/<module>/`; new ERP modules should follow this package pattern.
- All API responses are wrapped in `BasicResponse<T>` for consistent envelope.
- DTOs are used for all data leaving the service layer — never expose raw entities.
- Lombok `@Data`, `@Builder`, `@RequiredArgsConstructor` are the approved annotations.

---

## 8. Current Stage & Roadmap

### Current Stage: Core Authentication & Authorization Complete

**Commit Timeline:**

| Date | Milestone |
|---|---|
| April 22, 2026 | Initial project setup |
| June 3–5, 2026 | Security module scaffolding |
| June 5–8, 2026 | JWT auth, RBAC, token refresh complete |
| June 9, 2026 | NavigationMenu entity + sidebar endpoint |
| June 17, 2026 | Configuration refinements |

### Module Completion Status

| Module | Status | Notes |
|---|---|---|
| Authentication (login, token refresh) | Done (~90%) | All core flows implemented |
| Security / RBAC framework | Done (~80%) | Entities & filter complete; admin endpoints pending |
| User management endpoints | In Progress (~40%) | Sidebar menu endpoint done; CRUD endpoints pending |
| Department management | Not started | Entity defined, no endpoints |
| Login audit log | Not started | Entity defined, no endpoints |
| ERP business modules (inventory, HR, etc.) | Not started | Framework ready |
| Automated tests | Not started | Test profile configured; no test cases written yet |
| Database migration tooling (Flyway/Liquibase) | Not started | Currently using Hibernate `update` |

### Immediate Next Steps

1. Implement user CRUD endpoints (`GET/POST/PUT/DELETE /api/users`).
2. Implement role & permission management endpoints.
3. Add login audit log endpoint.
4. Write unit and integration tests for the auth module.
5. Introduce Flyway for database migration management.
6. Begin the first ERP business module — the **Product (item master data)** module. Function & feature requirements are specified in [docs/modules/product-module-requirements.md](docs/modules/product-module-requirements.md); it is the master-data foundation the Inventory, Purchasing, and Sales modules build on.
