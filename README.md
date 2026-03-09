# nile-e2e

Standalone end-to-end test suite for the [Nile](https://github.com/pheno-menon/nile) e-commerce application.

Tests run against the live Docker stack over HTTP using **TestNG** and **REST Assured**.

---

## Prerequisites

- Java 17+
- Maven 3.9+
- The Nile Docker stack running locally (see below)

---

## Running the Nile stack

From the root of the `nile` repository:

```bash
export JWT_SECRET=supersecretandsecurejwtsecurtykeywhichisatleast32characterslong
docker compose down -v
docker compose build --no-cache
docker compose up -d
```

Wait until `http://localhost:8080/api/products` returns 200 before running tests.

---

## Running the tests

### Full regression suite (default)
```bash
mvn verify
```

### Specific suite
```bash
mvn verify -Dsuite=smoke       # fast sanity check — auth + public reads
mvn verify -Dsuite=regression  # all test classes, sequential
mvn verify -Dsuite=security    # JWT and RBAC boundary tests only
```

### Against a remote environment
```bash
mvn verify -Dbase.url=http://localhost:8080
```

---

## Tech stack

- TestNG
- RestAssured
- GitHub Actions

---

## Note on admin tests

Admin-only endpoints (`/api/admin/**`) require a `ROLE_ADMIN` user. In the current setup, all registered users get `ROLE_USER` by default. Admin tests that create products via `/api/admin/products` will return **403** unless you manually promote a user in the database or add a seed script.

To promote a user via MySQL:

```sql
UPDATE users SET role = 'ROLE_ADMIN' WHERE email = 'your-admin@nile.com';
```
