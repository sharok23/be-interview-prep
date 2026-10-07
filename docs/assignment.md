# Backend Interview Prep Assignment (Java / Spring Boot)

This document restates the assignment and records the planned design for each question. Each question's section is updated with the final decisions when its PR is merged.

## Overview

Build 5 Spring Boot features in one GitHub repository. Ship each one as its own pull request, then record one 2-minute video explaining all five. The code must be explainable without AI help.

- **Stack:** Java 17+ and Spring Boot 3.x. Build tool, database, libraries and test tools are our choice.
  - This repo uses Java 21, Spring Boot 3.5, Maven, Spring Data JPA and H2.
- **Time limit:** 2 hours for all 5 questions, including PRs and merges. Then 30 minutes to record and upload the video.
- **Suggested split:** Q1 and Q2 15 minutes each, Q3 and Q4 25 minutes each, Q5 40 minutes.
- **Assessed:** working code, clean structure, tests, Git/PR discipline, and above all understanding of what was built.
- **Interview:** be ready to explain every decision and the alternatives, and to make a short live change. Expect questions such as:
  - Walk me through what happens from the HTTP request to the database.
  - Why this approach? What alternatives were considered?
  - What breaks if two requests hit this endpoint at the same time?
  - What happens if this line is removed?
  - Change this behaviour live.

## Submission workflow

Each question is one branch, one PR and one merge, in order from Q1 to Q5.

1. Public repo `be-interview-prep`. On `main`, a README and a base Spring Boot project. Here these land through the `feature/setup` PR.
2. Each question gets a branch from the latest `main`:

   | # | Branch |
   |---|--------|
   | 1 | `feature/q1-task-api` |
   | 2 | `feature/q2-url-shortener` |
   | 3 | `feature/q3-auth` |
   | 4 | `feature/q4-product-catalog` |
   | 5 | `feature/q5-order-service` |

3. Commit in small, meaningful steps, such as `Add create task endpoint` or `Return field errors for invalid input`. Never use commits like `fix`, `changes` or `final`.
4. Open a PR into `main` using the template in `.github/pull_request_template.md`: Problem, Approach, Decisions & trade-offs, How to test. Review the diff before merging.
5. Squash-merge, then pull `main` before starting the next branch.
6. After the last merge, record the video, upload it to YouTube as Unlisted, and add the link to the README.

## Shared conventions

- **Package:** `com.mock.api.<feature>`, with a model/entity, request and response records, repository, service and controller.
- **Errors (added in Q1, reused by later questions):** every error uses one JSON shape, `ErrorResponse`: `status`, `error`, `message`, `path`, `timestamp` and `fieldErrors`. A `@RestControllerAdvice` maps the cases:
  - 400: validation, malformed JSON, type mismatch.
  - 404: not found.
  - 409: conflict.
  - 500: unexpected errors, without leaking details.
- **Persistence:** an H2 in-memory database through Spring Data JPA. `ddl-auto=create-drop` and `open-in-view=false`.

---

## Q1: Task Manager API

Build a REST API to create, view, update, delete and filter tasks.

**Requirements**
- A task has:
  - a title (required, max 100 characters)
  - a description
  - a status (To do / In progress / Done)
  - a due date (cannot be in the past)
  - a created date
- Create, list, get one, update and delete tasks. Filter the list by status.
- Reject invalid input with a clear message for each invalid field.
- All errors (invalid input, not found, unexpected) return one consistent JSON format with the right HTTP status.

**Acceptance criteria**
- Invalid input returns 400 with field-level messages. An unknown task returns 404.
- At least one automated test.

**Planned endpoints**

| Method | Path | Success | Errors |
|--------|------|---------|--------|
| POST | `/api/tasks` | 201 + `Location` | 400 |
| GET | `/api/tasks?status=TODO` | 200 | 400 (unknown status) |
| GET | `/api/tasks/{id}` | 200 | 404 |
| PUT | `/api/tasks/{id}` | 200 | 400, 404 |
| DELETE | `/api/tasks/{id}` | 204 | 404 |

**Planned decisions**
- `TaskStatus` is an enum: `TODO`, `IN_PROGRESS`, `DONE`. It defaults to `TODO` on create.
- Validation uses `@NotBlank @Size(max = 100)` on the title and `@FutureOrPresent` on the due date, so a due date of today is allowed.
- `createdAt` is set by the server and never read from the request.
- PUT replaces the whole task, which is simpler than PATCH and enough for the spec.

## Q2: URL Shortener

Build a service that turns long URLs into short links.

**Requirements**
- Submit a long URL, optionally with an expiry date, and get back a short code and a short URL.
- Visiting the short URL redirects to the original URL.
- Count every visit. A stats endpoint shows the original URL, the visit count and the created date.
- Short codes are at most 8 characters, unique and URL-safe.
- Reject invalid URLs. Handle unknown and expired codes with an appropriate status.

**Acceptance criteria**
- Shortening the same URL twice behaves the way we decided it should, and we can explain why.
- Visit counts stay accurate when many people open the same link at once.
- At least one automated test.

**Planned endpoints**

| Method | Path | Success | Errors |
|--------|------|---------|--------|
| POST | `/api/urls` `{url, expiresAt?}` | 201 `{code, shortUrl, ...}` | 400 |
| GET | `/r/{code}` | 302 to the original URL | 404 unknown, 410 expired |
| GET | `/api/urls/{code}/stats` | 200 `{originalUrl, visits, createdAt}` | 404 |

**Planned decisions**
- **Codes:** 7 random Base62 characters from `SecureRandom`, backed by a unique constraint and regenerated on collision.
  - Alternative: Base62 of the database id, which is predictable and leaks the volume of links.
- **Same URL twice:** return the existing link if it has not expired, so duplicate rows aren't created.
  - Alternative: a new code each time, which allows per-campaign stats.
- **Visit counting:** a single atomic `UPDATE url SET visits = visits + 1 WHERE code = ?`, so concurrent visits are never lost.
  - Alternative: read-modify-write, which loses updates under concurrency.
- **URL validation:** only `http` and `https` URLs with a host are accepted.

## Q3: Authentication & Roles

Secure an API so that only logged-in users can use it, and some endpoints are admin-only.

**Requirements**
- Users can register and log in. Store passwords securely.
- The API serves web and mobile clients, so authentication must not rely on server-side sessions.
- Login expires after 15 minutes.
- There are two roles, USER and ADMIN. Any logged-in user can view their own profile. Only an ADMIN can list all users.
- A request that isn't logged in returns 401. A logged-in user without the right role gets 403. Both return JSON, not an HTML error page.

**Acceptance criteria**
- A test proves that a USER cannot access the admin endpoint.
- No secrets are hard-coded in the source.

**Planned endpoints**

| Method | Path | Access | Success | Errors |
|--------|------|--------|---------|--------|
| POST | `/api/auth/register` | public | 201 | 400, 409 (username taken) |
| POST | `/api/auth/login` | public | 200 `{token, expiresAt}` | 401 |
| GET | `/api/users/me` | USER, ADMIN | 200 | 401 |
| GET | `/api/admin/users` | ADMIN | 200 | 401, 403 |

**Planned decisions**
- **Passwords:** hashed with BCrypt.
- **Tokens:** stateless JWT signed with HS256 using `spring-boot-starter-oauth2-resource-server` (Nimbus). Tokens expire after 15 minutes, and the session policy is `STATELESS`.
  - Alternative: server sessions, which the spec rules out.
- **Secret:** the signing key comes from the `JWT_SECRET` environment variable. Tests supply their own key.
- **Errors:** a JSON `AuthenticationEntryPoint` returns 401 and a JSON `AccessDeniedHandler` returns 403, both in the shared `ErrorResponse` shape.
- **Roles:** new users get `USER`. The ADMIN account comes from configuration rather than self-registration.
- **Open point:** whether Q1 and Q2 endpoints also require a login. This will be decided when Q3 starts.

## Q4: Product Catalog

Build a product listing API that stays fast as the catalog grows.

**Requirements**
- A product has a name, category, price, stock, rating and created date. Seed 100 products on startup.
- List products with pagination and sorting by any field. The response includes the total count and the number of pages.
- Optional filters that can be combined freely: category, price range, in-stock only, and name search.
- The page size is capped at 100.
- Single-product lookups happen far more often than products change. Make repeated lookups fast, but never return stale data after a product is updated or deleted.

**Acceptance criteria**
- Any combination of filters works in a single request.
- Repeated lookups of the same product don't query the database every time. Be able to show how we know.
- At least one automated test.

**Planned endpoints**

| Method | Path | Success | Errors |
|--------|------|---------|--------|
| GET | `/api/products?page&size&sort=price,desc&category&minPrice&maxPrice&inStock&q` | 200 page | 400 (unknown sort field, bad range) |
| GET | `/api/products/{id}` | 200 (cached) | 404 |
| PUT | `/api/products/{id}` | 200 (refreshes the cache) | 400, 404 |
| DELETE | `/api/products/{id}` | 204 (evicts the cache) | 404 |

**Planned decisions**
- **Filters:** JPA `Specification`s, one per filter, combined with `and`, so any combination works in one query.
  - Alternative: one repository method per combination, which grows exponentially.
- **Paging:** sort fields come from a whitelist. Page sizes above 100 are clamped to 100. The response is a page DTO with `content`, `page`, `size`, `totalElements` and `totalPages`.
- **Caching:** Spring Cache with Caffeine. `@Cacheable("products")` on lookup, `@CachePut` on update, `@CacheEvict` on delete.
- **Proving the cache works:** a test counts repository calls (`@MockitoSpyBean`), and Hibernate statistics or SQL logging show a single SELECT.
- **Seeding:** an `ApplicationRunner` seeds 100 products.
- **Indexes:** on `category` and `price`, for the common filters.

## Q5: Order Service

Build an order API that stays correct under heavy, simultaneous use.

**Requirements**
- Products have limited stock. A customer places an order with one or more items.
- An order is all-or-nothing: either every item is reserved, or none is.
- Stock must never go negative or be oversold, even when many customers order the same product at the same moment.
- Clients may retry a request after a network failure. A retried request must not create a duplicate order. Design how a retry is recognised.
- Insufficient stock returns 409 with a clear message.
- Cancelling an order returns its stock.

**Acceptance criteria**
- An automated test fires 50 simultaneous orders for a product with stock 10. Exactly 10 succeed, and the stock ends at 0.
- Retrying the same request creates only one order.

**Planned endpoints**

| Method | Path | Success | Errors |
|--------|------|---------|--------|
| POST | `/api/orders` + header `Idempotency-Key` | 201 (a retry returns 200 with the same order) | 400, 404 product, 409 insufficient stock |
| GET | `/api/orders/{id}` | 200 | 404 |
| POST | `/api/orders/{id}/cancel` | 200 | 404, 409 (already cancelled) |

**Planned decisions**
- **Reserving stock:** one `@Transactional` method runs a conditional atomic update for each item:
  `UPDATE product SET stock = stock - :qty WHERE id = :id AND stock >= :qty`.
  - If an update affects 0 rows, the method throws a `ConflictException` and the whole transaction rolls back, which gives all-or-nothing.
  - Items are processed in product-id order to avoid deadlocks.
  - Alternatives: pessimistic `SELECT … FOR UPDATE`, or optimistic `@Version` with retries. Both are candidates for the optional second approach.
- **Retries:** the client sends an `Idempotency-Key` header, which has a unique constraint on the orders table. A retry with the same key returns the existing order. If two copies of a request race, the loser catches the constraint violation and returns the winner's order.
- **Cancelling:** sets the status to `CANCELLED` and adds the stock back in the same transaction.
- **Cache interaction:** stock changes evict the Q4 product cache, so product lookups never show stale stock.
- **Concurrency test:** 50 threads start together on a `CountDownLatch`. The test asserts 10 orders return 201, 40 return 409, and the final stock is 0.

---

## If we finish early (optional)

- Q1: interactive API documentation (for example springdoc Swagger UI).
- Q2: let users choose their own custom short code.
- Q3: let users stay logged in beyond 15 minutes without re-entering their password (refresh token), plus a logout that ends that.
- Q4: make the fast lookups work across several app instances (for example Redis).
- Q5: implement a second approach to the concurrency problem with a short comparison, and run the tests against a real database.

## Submission checklist

- [ ] Public repo `be-interview-prep` created, with a README
- [ ] 5 branches, 5 PRs, all merged into `main`
- [ ] Every PR uses the description template
- [ ] The README explains how to run the app and the tests
- [ ] All tests pass
- [ ] Code and PRs done within 2 hours; one video of at most 2 minutes uploaded to YouTube (Unlisted) within 30 minutes after
- [ ] The README has every PR link and the YouTube link
- [ ] Every file in the repo can be explained without AI help

### Video outline (max 2 minutes)

| Time | What to show |
|------|--------------|
| 0:00–0:10 | Quick tour of the repo and the merged PRs |
| 0:10–1:50 | About 20 seconds per question: what it does, and the most important decision |
| 1:50–2:00 | One thing to improve with more time |
