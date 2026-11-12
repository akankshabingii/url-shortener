
# URL Shortener

A Spring Boot service that turns long URLs into short, unguessable links, redirects visitors quickly using a Redis cache, and tracks click counts. Includes a small web UI, per-IP rate limiting, link expiry, and custom aliases.

## Features

- Shorten any `http://` or `https://` URL into a 7-character code
- Optional **custom alias** (3–30 chars: letters, digits, `_`, `-`) and optional **expiry** (1–3650 days)
- Fast redirects: lookups are cached in Redis (20 min TTL) and fall back to MySQL if Redis is unavailable
- **Click statistics** per link, counted in memory and written to MySQL in batches
- **Rate limiting** per client IP, backed by Redis (10 creates/min, 120 reads/min by default)
- Consistent JSON error responses (400, 404, 405, 409, 410, 429, 500)
- Schema managed with Flyway; Hibernate only validates it
- Minimal static frontend served from the same app
- Dockerised with Docker Compose (app + MySQL + Redis)

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language / runtime | Java 21 |
| Framework | Spring Boot 3.3 (Web, Data JPA, Validation, Cache, Data Redis) |
| Database | MySQL 8.4 |
| Cache / rate limiting | Redis 7 |
| Migrations | Flyway |
| Build | Maven (wrapper included) |
| Containers | Docker, Docker Compose |
| Tests | JUnit 5, Spring Boot Test, Mockito |

## How It Works

**Creating a link**
1. `POST /api/urls` validates the URL (scheme, length, valid host, and not pointing back at this service).
2. If a custom alias is given, it is checked against a reserved list (`api`, `admin`, `actuator`, ...) and for uniqueness. Otherwise a random 7-character Base62 code is generated with `SecureRandom` (about 3.5 trillion combinations, so codes can't be guessed in sequence). A collision triggers a retry, up to 5 times.
3. The mapping is saved; a unique index on `short_code` is the final safety net against races (returns `409`).

**Opening a link**
1. `GET /{code}` looks the code up in Redis; on a miss it reads MySQL and caches the result.
2. Expiry is checked on every request, so a cached link still expires on time (`410 Gone`).
3. The click is counted in memory and flushed to MySQL every 5 seconds as one batched `UPDATE`. A failed flush keeps the counts and retries.
4. The server replies `302 Found` with the original URL in `Location`.

**Failure behaviour**
- Redis down: cache reads and writes are treated as misses, redirects still work from MySQL.
- Redis down: the rate limiter fails open (requests are allowed) and logs a warning.
- Unclean shutdown: up to one flush interval of click counts can be lost. A normal shutdown flushes first.

## Getting Started

### Prerequisites

- Docker and Docker Compose (easiest), **or**
- Java 21, a local MySQL 8, and a local Redis for running without Docker

### 1. Configure environment variables

```bash
cp .env.example .env
```

Edit `.env` and set your own values:

| Variable | Used for |
|----------|----------|
| `DB_USERNAME` | MySQL application user |
| `DB_PASSWORD` | MySQL application user password |
| `MYSQL_ROOT_PASSWORD` | MySQL root password (Compose) |
| `APP_BASE_URL` | Public base URL used to build short links, e.g. `http://localhost:8080` |

`.env` is git-ignored. Never commit real credentials.

### 2. Run with Docker Compose

```bash
docker compose up --build
```

Open <http://localhost:8080> for the UI. Compose starts the app with the `prod` profile, waits for MySQL to be healthy, and connects to Redis internally (Redis is not exposed to the host).

### Run locally without Docker

The default `dev` profile expects MySQL on `localhost:3306` (user `root`, database `url_shortener` is created automatically) and Redis on `localhost:6379`.

```bash
export DB_PASSWORD=your-mysql-root-password
./mvnw spring-boot:run
```

### Run the tests

```bash
./mvnw test
```

## API

Base URL: `http://localhost:8080`

### Create a short URL

`POST /api/urls`

```json
{
  "url": "https://example.com/some/very/long/path",
  "customAlias": "my-link",
  "expiresInDays": 30
}
```

`customAlias` and `expiresInDays` are optional.

**201 Created**

```json
{
  "shortCode": "my-link",
  "shortUrl": "http://localhost:8080/my-link",
  "originalUrl": "https://example.com/some/very/long/path",
  "expiresAt": "2026-12-12T10:00:00Z"
}
```

Example:

```bash
curl -X POST http://localhost:8080/api/urls \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com"}'
```

### Redirect

`GET /{shortCode}` returns `302 Found` with a `Location` header.

### Link statistics

`GET /api/urls/{shortCode}/stats`

```json
{
  "shortCode": "my-link",
  "originalUrl": "https://example.com/some/very/long/path",
  "clickCount": 42,
  "createdAt": "2026-11-12T10:00:00Z",
  "expiresAt": "2026-12-12T10:00:00Z"
}
```

`clickCount` includes clicks that haven't been flushed to the database yet.

### Errors

All errors use the same shape:

```json
{ "timestamp": "2026-11-12T10:00:00Z", "status": 404, "error": "Short URL not found" }
```

| Status | Meaning |
|--------|---------|
| 400 | Invalid URL, alias, expiry, malformed JSON, or reserved alias |
| 404 | Unknown short code |
| 409 | Alias or code already taken |
| 410 | Link has expired |
| 429 | Rate limit exceeded (see `Retry-After` header) |

## Configuration

Key settings in `application.properties`:

| Property | Default | Meaning |
|----------|---------|---------|
| `app.rate-limit.enabled` | `true` | Turn rate limiting on or off |
| `app.rate-limit.create-per-minute` | `10` | Creates per IP per minute |
| `app.rate-limit.read-per-minute` | `120` | Redirects and stats per IP per minute |
| `app.click-flush-ms` | `5000` | How often pending clicks are written to MySQL |
| `spring.cache.redis.time-to-live` | `20m` | Redirect cache TTL |

## Project Structure

```
src/main/java/com/akanksha/url_shortener/
├── controller/    # UrlController (create, stats), RedirectController
├── service/       # UrlService, LinkLookup (cached), ClickCounter (batched)
├── repository/    # UrlMappingRepository (Spring Data JPA)
├── entity/        # UrlMapping
├── dto/           # ShortenRequest, ShortenResponse, StatsResponse
├── ratelimit/     # Redis-backed per-IP rate limit filter
├── exception/     # Custom exceptions + GlobalExceptionHandler
├── config/        # Caching/scheduling, Clock
└── util/          # Base62
src/main/resources/
├── db/migration/  # Flyway migrations
└── static/        # index.html, script.js, style.css
```

## Deployment Notes

- Use the `prod` profile (`SPRING_PROFILES_ACTIVE=prod`) and provide `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `APP_BASE_URL`, and the Redis host and port (`SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT`).
- `APP_BASE_URL` must be your public URL, because it is used to build the short links.
- The `prod` profile enables `server.forward-headers-strategy=framework` so rate limiting sees the real client IP behind a reverse proxy. Only run it behind a proxy you trust.

## Possible Improvements

- Authentication and per-user link management
- Make the frontend's "My Links" and "Stats" sections functional
- Persist click counts in Redis so a crash can't lose them
- Integration tests against real MySQL/Redis (Testcontainers)

## Author

[akankshabingii](https://github.com/akankshabingii)