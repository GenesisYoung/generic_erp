# Generic ERP — Backend

The backend service of the **Generic ERP System**, designed and developed by Genesis Young. Built with Spring Boot, it exposes a RESTful JSON API consumed by the Vue 3 frontend ([Generic-Front](../Generic-Front)). It currently provides authentication, authorization (RBAC), user management, and permission-gated navigation menus. Additional ERP business modules (inventory, purchasing, HR, etc.) will be layered on top of this foundation.

## Tech Stack

| Category | Technology | Version |
|---|---|---|
| Language | Java | 17 |
| Framework | Spring Boot | 4.0.5 |
| Security | Spring Security + JWT (JJWT 0.12.6) | Boot-managed |
| Password Hashing | Argon2 (BouncyCastle) | 1.78.1 |
| ORM | Spring Data JPA / Hibernate | Boot-managed |
| Database | MySQL (prod) / H2 (test) | — |
| Messaging (planned) | Apache Kafka | Boot-managed |
| Env Vars | Spring Dotenv | 5.1.0 |
| Build | Maven (wrapper included) | — |

## Quick Start

### 1. Configure environment variables

Create a `.env` file at the project root (loaded by Spring Dotenv), or export them as system environment variables:

```env
DB_URL=jdbc:mysql://localhost:3306/erp
DB_USERNAME=root
DB_PASSWORD=root
DB_DRIVER_CLASS_NAME=com.mysql.cj.jdbc.Driver
ACCESS_TIMEOUT=900000            # access token TTL in ms (15 min)
REFRESH_TIMEOUT=604800000        # refresh token TTL in ms (7 days)
AUTHENTICATION_SECRET_KEY_ACCESS=<hmac-secret-for-access-tokens>
AUTHENTICATION_SECRET_KEY_REFRESH=<hmac-secret-for-refresh-tokens>
AUTH_COOKIE_SECURE=false         # local HTTP only; keep true in production
CORS_ALLOWED_ORIGINS=http://localhost:5173
LANGUAGE=EN                      # default language (EN / CN)
```

### 2. Start MySQL

```bash
docker run -d --name erp-mysql \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=erp \
  -p 3306:3306 mysql:latest
```

Hibernate is configured with `ddl-auto: update`, so tables are created/updated automatically on startup.

### 3. Run

```bash
./mvnw spring-boot:run
# API available at http://localhost:8080
```

### Tests

```bash
./mvnw test
```

Tests run against an in-memory H2 database via the `test` Spring profile (`application-test.yml`) — no environment variables required.

### Production Build

```bash
./mvnw clean package -DskipTests
java -jar target/generic_erp-*.jar
```

## Architecture

Classic three-tier layered architecture with a stateless JWT security filter in front:

```
Request → JwtAuthenticationFilter → Controller → Service → Repository (JPA) → MySQL
```

**Security flow:**

1. Client logs in via `POST /api/auth/login`; the Argon2-hashed password is verified.
2. On success, `JWTUtil` issues an access token and a refresh token (TTLs configurable via env vars).
3. Every subsequent request carries `Authorization: Bearer <access token>`; `JwtAuthenticationFilter` validates it and populates the Spring Security context.
4. Expired access tokens are renewed via `POST /api/auth/refresh/access`; refresh tokens can themselves be rotated via `POST /api/auth/refresh/refresh`. Refresh tokens are persisted (`UserRefreshToken`) for validation and rotation.
5. Consecutive failed logins lock the account; login events are audit-logged.

## API Overview

| Area | Base Path | Endpoints |
|---|---|---|
| Authentication | `/api/auth` | `POST /login`, `POST /refresh/access`, `POST /refresh/refresh` |
| User (self) | `/api/users` | `GET /fetch/sidebar/menu` — sidebar items for the authenticated user |
| User management | `/api/root` | `GET /user/fetch`, `GET /user/detail`, `/users`, `DELETE /delete/{id}` |
| Menu management | `/api/manager/menu` | `GET /fetch`, `GET /fetch/valid`, `POST /save`, `POST /delete` |
| Permissions (admin) | `/api/admin/permissions` | `GET /fetch`, `POST /save`, `POST /delete`, `POST /deleteRoot` |
| Permission queries | `/api/permission` | `POST /fetch`, `GET /menu/{navId}/users`, `GET /menu/permission`, `GET /menu/registration` |
| Permission assignment | `/api/permission/manipulate` | `POST /user`, `POST /permission`, `POST /registration` |

All responses are wrapped in a consistent envelope (`BasicResponse<T>` / paginated `BasicPageResponse<T>`).

## Project Structure

```
src/main/java/com/gsgd/generic_erp/
├── GenericErpApplication.java     # Spring Boot entry point
├── configuration/security/        # JWT util, auth filter, security config, Argon2 auth impl
├── controller/
│   ├── auth/                      # /api/auth, /api/users
│   └── admin/                     # user / menu / permission management endpoints
├── service/                       # business logic (auth, admin)
├── repository/                    # Spring Data JPA repositories (auth, admin)
├── entity/auth/                   # JPA entities: User, Role, Permission, NavigationMenu, join tables…
├── dto/                           # data transfer objects (entities are never exposed directly)
├── spec/                          # JPA Specifications for dynamic filtering
├── view/                          # database view mappings
├── enums/                         # Language (EN / CN) i18n enums
└── util/                          # BasicResponse, pagination helpers, global variables
```

## Data Model (RBAC)

Users are assigned roles (`UserRole`); roles carry permissions (`RolePermission`); permissions map to fine-grained actions (`ActionPermission`). Navigation menu visibility is controlled per user (`UserNavMenu`) and driven by the same permission model. Supporting entities cover departments, extended user profiles, login audit logs, and persisted refresh tokens.

## Conventions

- New ERP modules follow the `entity/<module>/`, `controller/<module>/` package pattern.
- DTOs for all data leaving the service layer — never expose raw entities.
- Lombok `@Data`, `@Builder`, `@RequiredArgsConstructor` are the approved annotations.
- `ddl-auto: update` is for development only; introduce Flyway/Liquibase before shared production use.

## Further Reading

See [DEVELOPMENT.md](DEVELOPMENT.md) for the full development process document, module completion status, and roadmap.
