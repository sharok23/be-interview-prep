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

- **Packages (by layer, under `com.project.api`):** `contract` (request/response records, `ApiError`), `model` (JPA entities), `enums`, `repository`, `service`, `controller`, `exception` (exceptions and `GlobalExceptionHandler`).
- **Errors (added in Q1, reused by later questions):** every error uses one JSON shape, `ApiError`: `status`, `error`, `message`, `path`, `timestamp` and, for invalid input, `fieldErrors`. `GlobalExceptionHandler` (`@RestControllerAdvice`) maps the cases:
  - 400: Bean Validation failures, invalid enum or date values in the body (each with a field message), malformed JSON, bad query or path parameters.
  - 404: `NotFoundException` and unknown URLs.
  - 405 and 415: Spring's own web exceptions, which carry their status.
  - 409: a concurrent update of the same row (optimistic locking).
  - 500: anything else, logged, with no details leaked.
- **Persistence:** an H2 in-memory database through Spring Data JPA. `ddl-auto=create-drop` and `open-in-view=false`.
- **Security (from Q3):** every `/api/**` endpoint except register and login needs a JWT. Data belongs to its owner, and ADMIN sees everything.

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

**Endpoints**

| Method | Path | Success | Errors |
|--------|------|---------|--------|
| POST | `/api/tasks` | 201 + `Location` | 400 |
| GET | `/api/tasks?status=TODO` | 200, sorted by id | 400 (unknown status) |
| GET | `/api/tasks/{id}` | 200 | 400 (non-numeric id), 404 |
| PUT | `/api/tasks/{id}` | 200 | 400, 404, 409 (concurrent edit) |
| DELETE | `/api/tasks/{id}` | 204 | 404 |

Request body: `{"title": "...", "description": "...", "status": "TODO|IN_PROGRESS|DONE", "dueDate": "2026-12-31"}`.

**Flow:** `TaskController` (validates with `@Valid`) → `TaskService` (`@Transactional`, rules and defaults) → `TaskRepository` (Spring Data JPA) → H2 `task` table. The `Task` entity is never returned directly; `TaskResponse.from` maps it.

**Since Q3:** every task endpoint needs a Bearer token, and each task belongs to the user who created it (see Q3).

**Decisions**
- **Status:** `TaskStatus` is an enum stored as text (`EnumType.STRING`), so reordering the enum can't corrupt existing rows. It is optional. Create defaults to `TODO`, and PUT keeps the current status when it is omitted.
- **Validation:**
  - `@NotBlank @Size(max = 100)` on the title, and `@Size(max = 1000)` on the description, so an oversized value is a 400 rather than a database error.
  - `@FutureOrPresent` on the due date, so today is allowed. The rule also applies on PUT: an overdue task needs a new due date when it is updated.
  - Input is trimmed in the request record's compact constructor, so validation sees the trimmed value.
- **Field-level errors:** Bean Validation errors are collected per field. An invalid enum or date fails in Jackson before validation runs, so the handler reads the field name from Jackson's exception path. The message lists the allowed values without echoing the input.
- **`createdAt`** is set by the server and never read from the request.
- **PUT replaces the whole task.** It is simpler than PATCH and enough for the spec.
- **Concurrent edits:** `@Version` (optimistic locking) makes two overlapping updates of the same task end in one success and one 409, instead of the last write silently winning.
  - Alternative: a pessimistic lock, which holds row locks and is unnecessary for rare conflicts.
  - When two DELETEs overlap, the second returns 204 or 409 depending on timing. Either is fine because DELETE is idempotent.
  - Known limitation: `version` is not exposed to clients, so the lock only catches requests that overlap in time. Making it catch "read, then write back much later" would need the version, or ETag/If-Match, in the API.
- **Error record name:** `ApiError` rather than `ErrorResponse`, to avoid clashing with Spring's `org.springframework.web.ErrorResponse`. Spring's own exceptions (404 unknown URL, 405, 415) implement that interface, and the catch-all handler uses it to return their status in our format.

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

**Endpoints**

| Method | Path | Success | Errors |
|--------|------|---------|--------|
| POST | `/api/urls` `{url, expiresAt?}` | 201 new link, 200 existing link | 400 |
| GET | `/r/{code}` | 302 to the original URL | 404 unknown, 410 expired |
| GET | `/api/urls/{code}/stats` | 200 `{code, originalUrl, visits, createdAt, expiresAt}` | 404 |

**Flow:** `ShortUrlController` → `ShortUrlService` → `ShortUrlRepository` → H2 `short_url` table. `ShortCodeGenerator` produces the codes.

**Since Q3:** creating links and reading stats need a Bearer token, and links belong to their creator. The `/r/{code}` redirect stays public (see Q3).

**Decisions**
- **Codes:** 7 random Base62 characters (`[0-9A-Za-z]`, URL-safe) from `SecureRandom`. That gives 62^7 ≈ 3.5 trillion combinations, and a unique constraint on `code` guarantees uniqueness.
  - On the rare collision, the insert fails and is retried with a new code (up to 5 attempts).
  - Alternative: Base62 of the database id, which is predictable and leaks the number of links.
- **Same URL twice:** the same URL with the same `expiresAt` returns the existing link with 200 instead of 201, so one long URL has one link and one set of stats. A different expiry is a different link.
  - Alternative: a new code every time, which allows per-campaign stats but stores duplicates.
- **Dedupe is safe under concurrency:** `dedupeKey` is the SHA-256 of `url|expiresAt`, with a unique constraint.
  - Two simultaneous requests for the same URL can both find no existing row. The database lets only one insert succeed. The loser catches `DataIntegrityViolationException`, re-reads by `dedupeKey` and returns the winner's link.
  - A test fires 50 simultaneous shorten requests and asserts exactly one link is created.
  - `shorten` deliberately has no `@Transactional`. Each `saveAndFlush` runs in its own repository transaction, so a failed insert rolls back on its own and the retry can read the row the winner committed. Inside one service-level transaction, the first violation would mark the whole transaction rollback-only.
  - A plain unique constraint on `(original_url, expires_at)` would not work, because NULL expiries are never equal in SQL and the URL column is 2048 characters long.
- **URL normalisation:** the scheme and host are lowercased (`HTTPS://Example.com/x` equals `https://example.com/x`). The path stays case-sensitive because servers treat it that way. `expiresAt` is truncated to milliseconds so a resubmitted request matches the stored value.
- **URL validation:** only `http`/`https` URLs with a well-formed host (dot-separated labels, optional port up to 65535) and RFC 3986 characters are accepted, up to 2048 characters. This rejects `ftp:`, `javascript:`, spaces and `http://.`.
- **Visit counting:** a single atomic `UPDATE short_url SET visits = visits + 1 WHERE code = ?` per redirect. The database serialises the increments, so concurrent visits are never lost; a test fires 50 simultaneous visits.
  - Alternative: read the count, add one and save. Two visitors would read the same value and one increment would be lost.
- **302, not 301:** browsers cache a 301 and skip the server on later visits, so those visits would never be counted.
- **Expired vs unknown:** an expired code returns 410 Gone (it existed, but is no longer valid) and is not counted. An unknown code returns 404. Stats stay viewable after expiry.

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

**Endpoints**

| Method | Path | Access | Success | Errors |
|--------|------|--------|---------|--------|
| POST | `/api/auth/register` `{username, password}` | public | 201 `{id, username, role, createdAt}` | 400, 409 (username taken) |
| POST | `/api/auth/login` `{username, password}` | public | 200 `{accessToken, tokenType: "Bearer", expiresAt}` | 400, 401 |
| GET | `/api/users/me` | USER, ADMIN | 200 own profile | 401 |
| GET | `/api/admin/users` | ADMIN | 200 all users | 401, 403 |
| all `/api/tasks/**`, `/api/urls/**` | | USER, ADMIN | as in Q1/Q2 | 401 |
| GET | `/r/{code}` | public | 302 | 404, 410 |

**Flow:**
1. **Login:** `AuthController` → `AuthService` checks the password against the BCrypt hash, then `TokenService` signs a JWT.
2. **Every later request:** Spring Security's `BearerTokenAuthenticationFilter` reads `Authorization: Bearer …`, and `JwtDecoder` verifies the signature and expiry.
3. **Roles:** the `roles` claim becomes `ROLE_USER` or `ROLE_ADMIN`, and URL rules in `SecurityConfig` decide access.
4. **Controllers:** they receive the `Jwt` and turn it into a `CurrentUser`.

**Decisions**
- **Stateless JWT** (`SessionCreationPolicy.STATELESS`, CSRF off since no cookies are used). This works for web and mobile clients.
  - Alternatives: server sessions, which the spec rules out; opaque tokens, which need a token-store lookup on every request.
- **Spring's OAuth2 resource server** (Nimbus, HS256) instead of a hand-written filter. Spring verifies the signature and expiry itself.
  - The JWT carries `sub` (username), `roles`, `iat` and `exp`.
- **Exactly 15 minutes:** `JwtTimestampValidator(Duration.ZERO)` removes Spring's default 60-second clock-skew allowance.
- **Signing key without hard-coded secrets:** the key comes from `JWT_SECRET`, at least 32 bytes, otherwise startup fails.
  - If it is unset (local runs, tests), a random key is generated with a warning. Tokens then don't survive a restart.
  - The admin account is seeded only from the `ADMIN_USERNAME`/`ADMIN_PASSWORD` environment variables. Registration always gives `USER`.
- **Passwords:**
  - BCrypt hashes, never returned in any response.
  - Validated to 8–72 characters *and* at most 72 bytes, because BCrypt rejects longer input.
  - Login answers "Invalid username or password" for both unknown users and wrong passwords. For unknown users it still runs a BCrypt match against a dummy hash, so the response time doesn't reveal which usernames exist.
- **Usernames:** lowercased, so `Alice` and `alice` are the same account. Duplicates return 409.
  - Like Q2, `register` relies on the unique constraint (`saveAndFlush`, catch the violation) so two simultaneous registrations can't both succeed.
- **JSON 401/403:** `SecurityErrorHandler` implements `AuthenticationEntryPoint` (401) and `AccessDeniedHandler` (403) and writes the shared `ApiError`. Errors from the security filters never reach `@RestControllerAdvice`, so they need their own handler.
- **Stale tokens on public endpoints:** a custom `BearerTokenResolver` ignores the `Authorization` header on register, login and `/r/**`. Otherwise a client that keeps sending an expired token would get 401 and could never log in again.
- **Tasks and short URLs belong to users (all features are connected):**
  - `Task` and `ShortUrl` have an `owner` (`@ManyToOne`, lazy). Users only see and change their own data; anyone else's returns 404, not 403, so ids of other users' data aren't confirmed.
  - ADMIN sees everything. `TaskResponse` includes `owner`.
  - The Q2 dedupe key includes the owner, so two users shortening the same URL get separate links and stats.
  - `/r/{code}` stays public, because people clicking a short link have no token.
  - `@EntityGraph(attributePaths = "owner")` loads the owner in the same query, which avoids N+1 selects when listing.
- **A token for a deleted user** returns 401 ("Account no longer exists") instead of a 500.
- **Known limitation:** a token can't be revoked before it expires. The optional refresh-token and logout work would add that.

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

**Endpoints** (all need a Bearer token)

| Method | Path | Access | Success | Errors |
|--------|------|--------|---------|--------|
| GET | `/api/products?category&minPrice&maxPrice&inStock&q&page&size&sort` | USER, ADMIN | 200 page | 400 |
| GET | `/api/products/{id}` | USER, ADMIN | 200 (cached) | 404 |
| POST | `/api/products` | ADMIN | 201 + `Location` | 400, 403 |
| PUT | `/api/products/{id}` | ADMIN | 200 (evicts the cache) | 400, 403, 404, 409 |
| DELETE | `/api/products/{id}` | ADMIN | 204 (evicts the cache) | 403, 404 |

**Page response:** `{content, page, size, totalElements, totalPages}`. Defaults are `page=0`, `size=20` and sort by `id`.
- `sort=price,desc` sorts one field; repeat it (`sort=category&sort=price,desc`) for several.
- Sortable fields: `id`, `name`, `category`, `price`, `stock`, `rating`, `createdAt`. `id` is always added as the last tiebreaker so paging is stable.

**Flow:**
- **Search:** `ProductController` → `ProductService.search` → `ProductSpecifications` builds one WHERE clause → `ProductRepository.findAll(spec, pageable)`, which runs a SELECT plus a COUNT.
- **Lookup:** `ProductService.get` checks the Caffeine `products` cache first; on a miss it calls `findById` and stores the `ProductResponse`.

**Decisions**
- **Filters as JPA Specifications.** Each filter (category, minPrice, maxPrice, inStock, q) is a small `Specification`. Missing filters are `null` and `Specification.allOf` skips them, so any combination becomes a single query.
  - Alternative: a repository method per combination, which grows to 2^5 methods.
  - Category matching is case-insensitive, and `inStock=true` means `stock > 0` (`false` means no filter).
  - `q` is a case-insensitive "contains" search on the name, with LIKE wildcards escaped so `%` matches a literal `%`.
- **Paging:**
  - A page size over 100 is **clamped** to 100 rather than rejected, so clients never break. The response shows the real size.
  - `size < 1`, a negative page, a page whose offset would overflow, `minPrice > maxPrice` and unknown sort fields or directions return 400 with `fieldErrors`.
  - Sort fields are whitelisted, so clients can't sort by internal columns.
- **Caching single-product lookups (Caffeine via Spring Cache):**
  - `@Cacheable` stores the immutable `ProductResponse` record, not the JPA entity, so callers can't modify cached state and lazy loading never runs outside a transaction.
  - **Never stale:**
    1. `TransactionAwareCacheManagerProxy` delays each evict until after the database commit. Otherwise a reader could re-cache the old row between the evict and the commit.
    2. Update and delete *evict* instead of writing the new value. With `@CachePut`, two concurrent updates could write their values in the wrong order after commit.
    3. `@Cacheable(sync = true)` loads each key exclusively. A slow read that loaded the old row can't write it back after an update's evict, because the evict waits for the load to finish. `ProductCacheTest.slowLookupRacingAnUpdateNeverLeavesStaleDataInTheCache` reproduces this race, and it fails without `sync = true`.
  - Limits: at most 10,000 entries, and a 10-minute expiry as a safety net. A miss for an unknown id is not cached (the exception skips the cache).
  - **How we know it works:** `ProductCacheTest` spies on the repository and checks that 5 lookups make exactly 1 `findById` call and produce 4 Caffeine cache hits (`recordStats`). Manually: run with `--logging.level.org.hibernate.SQL=debug` and call `GET /api/products/1` twice; only the first call logs a SELECT.
  - Alternative: Hibernate's second-level cache. It caches entities, is harder to reason about and must be configured per entity. For multiple instances, a shared Redis cache would replace Caffeine (an optional extra).
- **Writes are ADMIN-only.** Products are a shared catalog, so there's no owner. Any logged-in user can browse, and a USER gets 403 on POST, PUT or DELETE. `@Version` turns concurrent admin edits into 409.
- **Seeding:** `ProductSeeder` inserts 100 products on startup, generated from a fixed `Random(42)` seed so the data is the same every run, and skips seeding if products already exist.
- **Indexes:** on `price` (range filter and sort) and `category`. Because the category match is case-insensitive (`lower(category)`), a plain index on `category` can't serve it; a production database would use a functional index on `lower(category)` created by a migration.

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

**Endpoints** (all need a Bearer token)

| Method | Path | Success | Errors |
|--------|------|---------|--------|
| POST | `/api/orders` + header `Idempotency-Key` + `{items: [{productId, quantity}]}` | 201 new order; 200 retry returning the original order | 400 (missing/invalid key or body), 404 product, 409 insufficient stock or key reused for a different order |
| GET | `/api/orders/{id}` | 200 | 400, 404 |
| POST | `/api/orders/{id}/cancel` | 200 (also when already cancelled) | 400, 404 |

Response: `{id, status, items: [{productId, productName, quantity, unitPrice}], total, createdAt, owner}`.

**Flow:**
1. `OrderController` → `OrderService.place`.
2. **Replay check:** an existing order for (user, key) is returned as-is.
3. **One transaction** (`TransactionTemplate`): for each product in id order, run `reserveStock`; snapshot name and price into `OrderItem`s; `saveAndFlush` the `PurchaseOrder`.
4. **After commit:** the product cache entries are evicted.

**Decisions**
- **No overselling: a conditional atomic update per item.**
  `UPDATE product SET stock = stock - :q, version = version + 1 WHERE id = :id AND stock >= :q`.
  - The database checks and decrements in one statement while holding the row lock, so two buyers can never both take the last unit, and stock can never go negative.
  - 0 rows updated means the product is missing (404) or short of stock (409 with "requested X, available Y").
  - Rejected alternatives:
    - **Read stock, check, save:** a classic race that oversells.
    - **`SELECT … FOR UPDATE` (pessimistic lock):** correct, but two round trips while holding the lock.
    - **`@Version` optimistic locking:** under a burst for one hot product most attempts fail and must retry. Both remain candidates for the optional "second approach".
- **All-or-nothing:** every reservation and the order insert run in one transaction. Any failure (one item short, unknown product) throws, and the rollback undoes the reservations already made for the other items.
  - Items are processed in product-id order (duplicate lines are merged), so two orders for the same products always lock rows in the same order and can't deadlock.
- **Retries via a required `Idempotency-Key` header** (the client sends a UUID per logical order and resends it on retry):
  - A unique constraint on `(owner_id, idempotency_key)` lets the database guarantee one order per key; keys are scoped per user.
  - The order stores a SHA-256 of the merged items. The same key with the same items returns **200 with the original order**; the same key with different items returns **409**, so a reused key can't silently return the wrong order.
  - A missing, blank or over-100-character key returns 400.
  - **Concurrent copies of one request:** each copy runs the transaction. Whichever copy commits first wins. A loser fails either on the unique key (`DataIntegrityViolationException`) or, when stock is tight, with "insufficient stock" because the winner took the last units. In both cases it re-runs the replay check and returns the winner's order. `OrderConcurrencyTest` covers both cases.
  - `place` has no `@Transactional` itself, for the same reason as Q2: the losing transaction must roll back before the replay check can read the winner's committed order.
- **Cancel is idempotent:** `UPDATE orders SET status = CANCELLED WHERE id = ? AND status = PLACED`.
  - Stock is returned only when exactly one row changed, so repeated or simultaneous cancels return stock once.
  - An already-cancelled order returns 200 unchanged.
- **Cache interaction with Q4:** every stock change evicts that product's cache entry. The transaction-aware cache applies the evict after commit, so product lookups never show stale stock.
- **The bulk updates bump `@Version`**, so an admin `PUT` that overlaps an order fails with 409 instead of overwriting the reserved stock.
- **Ownership** is the same as Q3: other users' orders return 404, and ADMIN sees all.
- `unitPrice` and `productName` are snapshotted on the order, so later product edits don't change past orders.
- The entity is `PurchaseOrder` because `ORDER` is a reserved SQL/JPQL word; the table is `orders`.
- **Tests (`OrderConcurrencyTest`):**
  - 50 threads released together by a `CountDownLatch` order 1 unit of a product with stock 10: exactly 10 succeed, 40 get 409, and the stock ends at 0.
  - 20 simultaneous copies of one key create exactly one order.
  - 10 copies racing for the last 2 units all receive the same order.
  - 20 simultaneous cancels return the stock once.

---

## If we finish early (optional)

- Q1: interactive API documentation. **Done:** springdoc Swagger UI at `/swagger-ui.html`, with `/` redirecting to it.
  - A global bearer-JWT security scheme gives an **Authorize** button; login, register and the `/r/{code}` redirect are marked public.
  - Paths, parameters, request and response schemas and validation rules are generated from the code.
  - Success codes that differ from 200 (201, the 200 retry or dedupe responses, the 302 redirect) are declared with `@ApiResponse`.
  - Error responses come from `@ResponseStatus` on each `GlobalExceptionHandler` method, so every operation documents the shared `ApiError` body.
  - Known limitation: every operation lists the same shared set of error codes, not exactly the ones it can return. The 401/403 responses from the security filter aren't listed per operation; the "Requires ADMIN role" notes and the Authorize instructions cover them.
  - Admin-only operations say "Requires ADMIN role".
  - `OpenApiDocsTest` asserts these status codes, so the docs can't silently drift. The hand-written `sort` parameter description is the one exception.
  - The UI and spec need no login: they fall under the security config's `anyRequest().permitAll()`, outside `/api/**`. `OpenApiDocsTest` checks the spec and the redirect.
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
