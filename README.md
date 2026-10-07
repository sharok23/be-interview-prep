# be-interview-prep

Five Spring Boot features built for the backend interview prep assignment. Each one is shipped as its own pull request. The full spec and the design decisions are in [docs/assignment.md](docs/assignment.md).

**Stack:** Java 21, Spring Boot 3.5, Maven (wrapper included), Spring Data JPA, H2 (in-memory), JUnit 5 and MockMvc.

## Run

```bash
./mvnw spring-boot:run
```

- The app starts on `http://localhost:8080`.
- From Q3 onwards, set the JWT signing key first. It must be at least 32 bytes:

  ```bash
  export JWT_SECRET=<random-32+-byte-string>
  ```

## Test

```bash
./mvnw clean test
```

## Questions

| # | Question | PR link |
|---|----------|---------|
| 1 | Task Manager API | [#7](https://github.com/sharok23/be-interview-prep/pull/7) |
| 2 | URL Shortener | |
| 3 | Authentication & Roles | |
| 4 | Product Catalog | |
| 5 | Order Service | |

**Video:**
