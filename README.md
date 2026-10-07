# be-interview-prep

Five Spring Boot features built for the backend interview prep assignment. Each one is shipped as its own pull request. The full spec and the design decisions are in [docs/assignment.md](docs/assignment.md).

**Stack:** Java 21, Spring Boot 3.5, Maven (wrapper included), Spring Data JPA, H2 (in-memory), JUnit 5 and MockMvc.

## Run

```bash
./mvnw spring-boot:run
```

- The app starts on `http://localhost:8080`. Opening it in a browser redirects to the interactive API docs (Swagger UI) at `/swagger-ui.html`. The raw OpenAPI spec is at `/v3/api-docs`.
- In Swagger UI: call `POST /api/auth/login`, copy the `accessToken`, click **Authorize** and paste it. Every "Try it out" request then sends the token.
- Optional environment variables (nothing secret is stored in the code):

  | Variable | Purpose |
  |---|---|
  | `JWT_SECRET` | JWT signing key, at least 32 bytes. If unset, a random key is used and tokens don't survive a restart. |
  | `ADMIN_USERNAME`, `ADMIN_PASSWORD` | Creates an ADMIN account on startup. Registration always creates USER accounts. |

- Every `/api/**` endpoint except register and login needs a token:

  ```bash
  curl -X POST localhost:8080/api/auth/register -H "Content-Type: application/json" \
    -d '{"username":"alice","password":"Str0ng-pass"}'
  curl -X POST localhost:8080/api/auth/login -H "Content-Type: application/json" \
    -d '{"username":"alice","password":"Str0ng-pass"}'
  curl localhost:8080/api/users/me -H "Authorization: Bearer <accessToken from login>"
  ```

## Test

```bash
./mvnw clean test
```

## Questions

| # | Question | PR link |
|---|----------|---------|
| 1 | Task Manager API | [#7](https://github.com/sharok23/be-interview-prep/pull/7) |
| 2 | URL Shortener | [#8](https://github.com/sharok23/be-interview-prep/pull/8) |
| 3 | Authentication & Roles | [#9](https://github.com/sharok23/be-interview-prep/pull/9) |
| 4 | Product Catalog | [#10](https://github.com/sharok23/be-interview-prep/pull/10) |
| 5 | Order Service | |

**Video:**
