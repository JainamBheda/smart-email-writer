# Smart Email Writer API

Spring Boot 3.4 / Java 21 REST API that calls the Gemini `generateContent` endpoint.

## Run

```powershell
Copy-Item .env.example .env
# set GEMINI_API_KEY in .env
.\mvnw.cmd spring-boot:run
```

`.\mvnw.cmd test` runs the test suite. Health check: `GET http://localhost:8080/api/email/health`.

## Endpoints

- `POST /api/email/generate` — `{"emailContent":"...","tone":"friendly"}` → reply text
- `GET /api/email/health` → `{"status":"UP",...}`

Errors are JSON: `{"message":"...","detail":"...","timestamp":"..."}`.

Configuration is documented in `.env.example` and in the root `README.md`.
