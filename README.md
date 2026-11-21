# Utility Billing and Customer Management System

Phase 0 through Phase 7 implementation: architecture, authentication, role-based business modules, notifications and scheduled processing, tests, security hardening, and deployment guidance.

## Architecture

- Frontend: React 18, Vite, React Router 6, Axios, React Context, Tailwind CSS, Recharts, React Hook Form, Yup, React Hot Toast.
- Backend: Java 17, Spring Boot 3.3, Spring Web, Spring Data JPA, Spring Security 6, JWT, Bean Validation, Lombok and MapStruct dependencies, OpenAPI.
- Database: MySQL 8.4 and Flyway.
- Run: Docker Compose starts MySQL, API, and Nginx-hosted React app.

Layering is feature-oriented: controllers call services, which use repositories or JDBC for persistence; shared response envelopes and security configuration live in common/config packages. Utility billing, customer payments, support tickets, and role-scoped APIs are implemented.

## Repository layout

~~~text
.
├── .env.example
├── docker-compose.yml
├── README.md
├── backend
│   ├── Dockerfile
│   ├── pom.xml
│   └── src
│       ├── main/java/com/utility/billing (admin, auth, common, config, customer, jobs, notification, operator, support)
│       ├── main/resources (application.yml, db/migration V1-V7)
│       └── test/java/com/utility/billing (auth and operator unit tests)
│           ├── application.yml
│           └── db/migration
│               ├── V1__schema.sql
│               ├── V2__seed.sql
│               ├── V3__admin_assignments.sql
│               ├── V4__operator_billing_and_lpg.sql
│               ├── V5__customer_payments_and_requests.sql
│               ├── V6__support_tickets_and_sla.sql
│               └── V7__notifications_and_scheduled_jobs.sql
└── frontend
    ├── Dockerfile
    ├── index.html
    ├── nginx.conf
    └── src
        ├── api
        ├── assets
        ├── components/common
        ├── hooks
        ├── layouts
        ├── pages
        │   ├── auth
        │   ├── admin
        │   ├── operator
        │   ├── support
        │   └── customer
        ├── routes
        ├── store
        ├── utils
        └── styles.css
~~~

## ER diagram

~~~mermaid
erDiagram
  USERS ||--o| ADMIN_PROFILE : has
  USERS ||--o| OPERATOR_PROFILE : has
  USERS ||--o| SUPPORT_PROFILE : has
  USERS ||--o| CUSTOMER_PROFILE : has
  OPERATOR_PROFILE }o--o{ ZONE : assigned
  CUSTOMER_PROFILE ||--o{ CONNECTION : owns
  UTILITY_SERVICE ||--o{ CONNECTION : provides
  CONNECTION_TYPE ||--o{ CONNECTION : classifies
  ZONE ||--o{ CONNECTION : contains
  UTILITY_SERVICE ||--o{ TARIFF_SLAB : prices
  CONNECTION_TYPE ||--o{ TARIFF_SLAB : prices
  CONNECTION ||--o{ METER_READING : records
  CONNECTION ||--o{ BILL : billed
  METER_READING ||--o| BILL : calculates
  BILL ||--o{ BILL_ITEM : itemizes
  BILL ||--o{ PAYMENT : receives
  PAYMENT ||--o| RECEIPT : issues
  CUSTOMER_PROFILE ||--o{ CONNECTION_REQUEST : requests
  CUSTOMER_PROFILE ||--o{ TICKET : opens
  TICKET ||--o{ TICKET_COMMENT : discusses
  USERS ||--o{ REFRESH_TOKEN : owns
  USERS ||--o{ PASSWORD_RESET_TOKEN : owns
  USERS ||--o{ NOTIFICATION : receives
  USERS ||--o{ AUDIT_LOG : performs
~~~

## Portals and API

| Role | Login page | Portal home | Login endpoint |
|---|---|---|---|
| ADMIN | /admin/login | /admin | POST /api/auth/admin/login |
| OPERATOR | /operator/login | /operator | POST /api/auth/operator/login |
| HELP_SUPPORT | /support/login | /support | POST /api/auth/support/login |
| CUSTOMER | /customer/login | /customer | POST /api/auth/customer/login |

### Authentication API

All JSON responses use { success, message, data, errors, timestamp }. Login and refresh data contain accessToken, refreshToken, role, and email.

| Method | URL | Role | Request | Response |
|---|---|---|---|---|
| POST | /api/auth/admin/login | Public; ADMIN account only | {email,password} | Token pair |
| POST | /api/auth/operator/login | Public; OPERATOR account only | {email,password} | Token pair |
| POST | /api/auth/support/login | Public; HELP_SUPPORT account only | {email,password} | Token pair |
| POST | /api/auth/customer/login | Public; approved CUSTOMER account only | {email,password} | Token pair |
| POST | /api/auth/customer/register | Public | {email,password,fullName,phone} | Pending approval message |
| POST | /api/auth/refresh | Public; refresh token required | {refreshToken} | Rotated token pair |
| POST | /api/auth/logout | Public; refresh token required | {refreshToken} | Sign-out confirmation |
| POST | /api/auth/password/forgot | Public | {email} | Non-enumerating confirmation; email sent for active account |
| POST | /api/auth/password/reset | Public; reset token required | {token,newPassword} | Password update confirmation |

A valid account signing in through the wrong role portal receives HTTP 403 with “Invalid portal for this account”. Five failed password attempts lock an account for 30 minutes. Login requests are limited to 15 per source IP and portal per five-minute window. Refresh tokens are random opaque values; only their SHA-256 hashes are stored.

### Admin module API (Phase 2)

All routes below require an ADMIN access token. JSON responses use the API envelope except CSV/PDF downloads.

| Method | URL | Request / query | Purpose |
|---|---|---|---|
| GET | /api/admin/dashboard | — | Counts, daily/monthly/yearly revenue, overdue and pending bills, open tickets, service consumption and monthly revenue |
| GET | /api/admin/accounts | q, role, status, page, size, sort, direction | Searchable staff/customer directory with server pagination |
| POST | /api/admin/accounts | email, password, role, fullName, phone | Create OPERATOR, HELP_SUPPORT or approved CUSTOMER accounts |
| PUT | /api/admin/accounts/{id} | email, fullName, phone | Update an account |
| PATCH | /api/admin/accounts/{id}/status | status: ACTIVE, INACTIVE or LOCKED | Activate/deactivate, lock or unlock |
| POST | /api/admin/accounts/{id}/password | newPassword | Reset password and revoke refresh tokens |
| DELETE | /api/admin/accounts/{id} | — | Soft-delete an account and retain history |
| GET / PATCH | /api/admin/registrations, /api/admin/registrations/{id} | q/page/size; decision APPROVED or REJECTED | Review self-registered customers |
| GET / PATCH | /api/admin/connection-requests, /api/admin/connection-requests/{id} | q/status/page/size; decision APPROVED or REJECTED | Review connection requests |
| GET/POST/PUT | /api/admin/services and /api/admin/services/{id} | code, name, active | Manage utility services |
| GET/POST/PUT/DELETE | /api/admin/connection-types and /api/admin/connection-types/{id} | name | Manage connection types |
| GET/POST/PUT/DELETE | /api/admin/zones and /api/admin/zones/{id} | name, description | Manage zones and areas |
| GET/POST/PUT/DELETE | /api/admin/tariffs and /api/admin/tariffs/{id} | serviceId, connectionTypeId, rangeStart, rangeEnd, ratePerUnit, fixedCharge, taxPercent, lateFeePercent, dueDays, effectiveFrom, effectiveTo | Manage tariff slabs |
| GET | /api/admin/assignments | — | Operators, customers, zones and current assignments |
| PUT | /api/admin/assignments/operators/{id}/zones | ids: zone IDs | Replace an operator's zone assignments |
| PUT | /api/admin/assignments/operators/{id}/customers | ids: customer IDs | Replace direct customer assignments |
| GET | /api/admin/data/{bills,payments,readings,tickets,connectionRequests,audit} | q, status, role, page, size, sort, direction | Searchable, filterable and paginated records |
| GET | /api/admin/data/{resource}/export | format=csv or pdf; optional q/status/role | Export matching records |
| GET | /api/admin/reports | — | Revenue, service consumption, defaulters, operator performance and ticket resolution reports |

Administrative writes record the actor, action, entity, timestamp, client IP, and old/new values in audit_log. Customer registration approval activates the account; the account status endpoint cannot bypass that review. Pagination is zero-based, with a maximum page size of 100.


### Operator module API (Phase 3)

All routes require an OPERATOR access token. Results are scoped to customers directly assigned to the operator or connections in their assigned zones. JSON responses use the common API envelope; meter photos are returned as authenticated inline image responses.

| Method | URL | Request / query | Purpose |
|---|---|---|---|
| GET | /api/operator/customers | q, page, size, sort, direction | Search and page assigned customers and connections |
| GET | /api/operator/customers/{customerId}/connections | — | List connections for an assigned customer |
| GET | /api/operator/billing-connections | cycle=YYYY-MM | List all active, approved customer connections with a reading and no bill for the cycle |
| GET | /api/operator/readings/previous | connectionId | Load latest reading for an assigned connection |
| POST | /api/operator/readings | multipart: reading JSON and optional photo | Record cycle reading, consumption, reset/rollover, remarks and photo |
| GET | /api/operator/readings | q, cycle, page, size, sort, direction | Search readings |
| GET | /api/operator/readings/{id}/photo | — | Retrieve a reading photo after assignment check |
| POST | /api/operator/bills | connectionId, cycle, adjustments | Generate one tariff-based bill from any active, approved customer's recorded reading |
| POST | /api/operator/bills/bulk | cycle | Generate bills for all eligible customer readings in a cycle |
| GET | /api/operator/bills | q, status, page, size, sort, direction | Search all bills |
| PUT | /api/operator/bills/{id} | adjustments | Recalculate any unpaid bill for an active, approved customer |
| GET | /api/operator/lpg-bookings | q, status, page, size | Search LPG bookings |
| POST | /api/operator/lpg-bookings | connectionId, cylinderCount, subsidy, deliveryCharge | Create an LPG booking and its bill |
| PATCH | /api/operator/lpg-bookings/{id}/status | status | Advance booking through refill and delivery states |

Operator customer, meter-reading, and LPG access remains assignment-scoped; billing is available across all active, approved customer connections. Billing requires a meter reading for the selected cycle.

Meter readings are unique per connection and cycle. A lower reading is rejected unless a rollover with a meter maximum or a reset with remarks is recorded. Tariff slabs are applied in order; bill generation fails when configured slabs leave a gap or do not cover consumption. A bill can be recalculated only before payment. Reading images accept JPEG, PNG or WebP up to 5 MB and are stored in the configured upload directory (`UPLOAD_DIR`, persisted as a Compose volume).

### Customer module API (Phase 4)

All routes require an approved CUSTOMER access token and enforce ownership against the authenticated account. JSON responses use the common API envelope. Bill and receipt downloads return PDF files; multipart request documents accept PDF, JPEG or PNG up to 10 MB.

| Method | URL | Request / query | Purpose |
|---|---|---|---|
| GET | /api/customer/dashboard | — | Profile, connections, outstanding balance, recent payments, open tickets, consumption summary |
| GET | /api/customer/bills | q, status, page, size | Search and page customer bill history |
| GET | /api/customer/bills/{id} | — | Owned bill and line items |
| GET | /api/customer/bills/{id}/pdf | — | Download bill PDF |
| GET | /api/customer/readings | connectionId, cycle, page, size | View consumption history |
| GET | /api/customer/payments | page, size | View successful and declined mock payment attempts |
| POST | /api/customer/bills/{id}/payments | amount (optional), method=UPI/CARD/NETBANKING, simulateSuccess | Pay full balance or a partial amount through the mock gateway |
| GET | /api/customer/payments/{id}/receipt.pdf | — | Download receipt for an owned successful payment |
| GET | /api/customer/services | — | List active utilities for request form |
| GET | /api/customer/connection-requests | page, size | View request status and uploaded document availability |
| POST | /api/customer/connection-requests | multipart: request JSON and optional document | Request a new connection, disconnection, or meter change |
| GET | /api/customer/connection-requests/{id}/document | — | Download the customer’s own uploaded document |
| PUT | /api/customer/profile | phone | Update customer phone number |

Mock gateway payments never collect card or bank credentials. Successful attempts update the bill balance under a row lock and create a receipt record. Declined attempts remain in payment history without changing the balance. A successful partial payment changes the bill status to PARTIALLY_PAID until the remaining balance is paid.


### Support and customer ticket API (Phase 5)

Support endpoints require a HELP_SUPPORT token. Customer ticket endpoints require an approved CUSTOMER token and enforce ticket ownership. Public comments are visible in both portals; internal notes are visible only to support staff. Important ticket changes are recorded in `audit_log`.

| Method | URL | Role | Request / query | Purpose |
|---|---|---|---|---|
| GET | /api/support/dashboard | HELP_SUPPORT | — | Open, in-progress, resolved, assigned-to-me, and past-SLA counts |
| GET | /api/support/tickets | HELP_SUPPORT | q, service, status, priority, dateFrom, dateTo, mine, page, size | Filter and page queue, with urgent/oldest SLA cases first |
| GET | /api/support/tickets/{id} | HELP_SUPPORT | — | Ticket with public conversation and internal notes |
| POST | /api/support/tickets/{id}/assign-to-self | HELP_SUPPORT | — | Claim an active ticket |
| PATCH | /api/support/tickets/{id}/status | HELP_SUPPORT | status | Move ticket through OPEN, IN_PROGRESS, RESOLVED, CLOSED |
| POST | /api/support/tickets/{id}/comments | HELP_SUPPORT | body, internal | Add a public reply or internal note |
| POST | /api/support/tickets/{id}/escalate | HELP_SUPPORT | — | Escalate and raise priority to URGENT |
| GET | /api/support/customers/{id} | HELP_SUPPORT | — | Read-only profile, connections, recent bills and payment history |
| GET | /api/support/customer/tickets | CUSTOMER | page, size | List own tickets |
| POST | /api/support/customer/tickets | CUSTOMER | serviceId, category, subject, description | Open a support ticket with SLA due time |
| GET | /api/support/customer/tickets/{id} | CUSTOMER | — | View own ticket and public comments |
| POST | /api/support/customer/tickets/{id}/comments | CUSTOMER | body | Reply to an open or resolved ticket |
| POST | /api/support/customer/tickets/{id}/rating | CUSTOMER | rating: 1–5 | Rate a resolved ticket once |

No-supply and leakage tickets receive an 8-hour SLA and HIGH priority; other categories receive a 24-hour SLA and NORMAL priority. Support can move OPEN tickets to IN_PROGRESS or RESOLVED, IN_PROGRESS tickets to RESOLVED, and RESOLVED tickets to CLOSED. A resolved or closed ticket can be reopened within seven days of resolution; reopening resets its SLA deadline and assignment. Due-time reporting is available now; automatic SLA notifications and scheduled processing are part of Phase 6.


### Notifications and scheduled jobs API (Phase 6)

All authenticated roles can access their own notification inbox. Email delivery uses SMTP settings from the environment; the inbox record is persisted even if delivery fails. Event keys make generated bill, payment, ticket, and due-reminder notifications idempotent.

| Method | URL | Role | Request / query | Purpose |
|---|---|---|---|---|
| GET | /api/notifications | Any authenticated role | unreadOnly, page, size | List own notifications and unread count |
| PATCH | /api/notifications/{id}/read | Any authenticated role | — | Mark an owned notification read |
| POST | /api/notifications/read-all | Any authenticated role | — | Mark all own notifications read |
| GET | /api/admin/reports | ADMIN | — | Monthly revenue, service consumption, defaulters, operator performance and ticket resolution reports |
| GET | /api/admin/data/{resource}/export | ADMIN | format=csv or pdf; q/status/role | Export bills, payments, readings, tickets, requests or audit records |

Schedulers run on UTC time: overdue bills and late fees at 00:10 daily, reminders three days before the due date at 00:15 daily, expired refresh/reset-token cleanup at 02:30 daily, and failed email retry every ten minutes. Each bill receives at most one late-fee entry per UTC date. The late fee is calculated from its outstanding balance and the applicable tariff percentage, then the bill status becomes OVERDUE. SMTP cron settings and reminder lead days can be overridden with `OVERDUE_CRON`, `REMINDER_CRON`, `REMINDER_DAYS`, `TOKEN_PURGE_CRON`, and `EMAIL_RETRY_CRON`.
## Local development

### Docker Compose

1. Copy .env.example to .env.
2. Set JWT_SECRET to a random value of at least 32 bytes and set MySQL credentials.
3. From the repository root, run:

~~~powershell
docker compose up --build
~~~

4. Open the frontend at http://localhost:5173, API docs at http://localhost:8080/swagger-ui.html, health at http://localhost:8080/health, and the local email inbox at http://localhost:8025.

The default Compose profile is dev, which runs the BCrypt account/sample-data initializer. Set SPRING_PROFILES_ACTIVE=prod to disable development fixtures. Compose sends password recovery and notification emails to the local Mailpit inbox; no external mail credentials are needed. For a separately run backend, configure MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD, MAIL_FROM, and optionally FRONTEND_URL in the backend environment. Scheduled email reminders retry failed deliveries every ten minutes; notification inbox records remain available if SMTP is offline.

## Phase 7: tests, security and deployment

### Verification commands

Run from the repository root:

~~~powershell
Push-Location backend
mvn test
mvn -DskipTests package
Pop-Location

Push-Location frontend
npm run build
Pop-Location
~~~

The backend test sources cover tariff slab boundaries, taxes, arrears, late fees, uncovered consumption, LPG totals, rollover arithmetic, and portal-specific authentication failures. Run `mvn test` with access to Maven Central on a fresh machine. The deployment build skips tests intentionally; CI should run tests before building images.

### Production deployment

1. Create a dedicated deployment secret file outside source control. Generate distinct MySQL user, MySQL root, and JWT secrets; the JWT secret must contain at least 32 random bytes. Configure SMTP credentials, a public `FRONTEND_URL`, and the public frontend origin in `CORS_ORIGINS`.
2. Set `SPRING_PROFILES_ACTIVE=prod`, `SEED_ENABLED=false`, and strong database passwords. Compose binds MySQL, frontend, and API ports to loopback; put a TLS reverse proxy in front and expose only HTTPS.
3. Run `docker compose config` to verify interpolated settings, then `docker compose up --build -d`. Check `docker compose ps` and `http://127.0.0.1:8080/health`.
4. Back up MySQL and the `backend_uploads` volume, test restore procedures, monitor container logs and disk usage, and rotate secrets on a planned schedule. Apply migrations through Flyway as part of API startup after taking a database backup.

The bundled Compose file is a single-host deployment example, not a multi-instance production topology. Its login throttle is process-local and its in-memory counters reset on restart; use an edge rate limiter or shared store for horizontally scaled deployments. TLS termination, managed secrets, SMTP delivery, backup retention, and monitoring must be supplied by the deployment environment. Set `CORS_ORIGINS` to the exact public frontend origin, set the frontend image build argument `VITE_API_BASE_URL` to the public API URL, and update `connect-src` in `frontend/nginx.conf` to that API origin before building a public image.

### Run services separately

- MySQL 8.4: create database utility_billing and the user from .env.example, then migrations apply automatically when the API starts.
- API: from backend, run mvn spring-boot:run. Set DB_URL, DB_USER, DB_PASSWORD, and JWT_SECRET in the shell.
- Frontend: from frontend, run npm install then npm run dev. Set VITE_API_BASE_URL=http://localhost:8080 if the API uses a different origin.

## Development credentials and smoke calls

When dev profile fixture initialization is enabled, all seeded accounts use password Utility@123:

- Admin: admin@utility.com
- Operators: operator1@utility.com, operator2@utility.com
- Help Support: support1@utility.com, support2@utility.com
- Customers: customer1@utility.com through customer5@utility.com

Example PowerShell request:

~~~powershell
$body = @{ email = 'admin@utility.com'; password = 'Utility@123' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/auth/admin/login' -ContentType 'application/json' -Body $body
~~~

Try the operator portal with POST /api/auth/operator/login and operator1@utility.com. To verify role isolation, post the admin email to /api/auth/operator/login; it returns 403. A customer can register through POST /api/auth/customer/register; new registrations remain pending until an admin approval flow activates them.

## Phase 1 through 7 completion checklist

- [x] React 18/Vite portal shell with four distinct role login routes and role guards
- [x] Spring Boot 3 / Java 17 API, MySQL 8 schema, Flyway V1–V7 migrations
- [x] Separate role login endpoints with role mismatch rejection
- [x] BCrypt passwords, five-attempt lockout, 15-minute access JWT and rotating seven-day hashed refresh tokens
- [x] Customer registration pending approval and email-token password recovery
- [x] CORS configuration, common response envelope, validation handling, OpenAPI JWT scheme
- [x] Dockerfiles and Compose orchestration for MySQL, API and frontend
- [x] Phase 2 admin dashboard, account controls, registration approvals, configuration CRUD, assignments, reports, searchable records, CSV/PDF exports and audit logging
- [x] Phase 3 operator customer assignment scope, meter readings with photo evidence and rollover/reset support, tariff billing, bulk generation, unpaid bill recalculation, and LPG booking/refill/delivery tracking
- [x] Phase 4 customer dashboard, usage and bill history, bill PDF, mock full/partial payments, payment receipt PDF, and connection/disconnection/meter-change requests with document uploads
- [x] Phase 5 support queue, service/status/priority/date filters, SLA deadlines, assignment, audited status/comment/escalation changes, customer context, customer ticket submission/replies, seven-day reopen, and resolution ratings
- [x] Phase 6 UTC overdue/late-fee jobs, three-day bill reminders, notification inbox/email delivery with retries, expired token cleanup, and report/export coverage
- [x] Phase 7 billing/authentication tests, bounded login throttling, non-root containers, upload limits, security headers, and deployment guide


## Phase 5 smoke calls

Authenticate as a seeded support user and load the queue summary and the assigned-to-me view:

~~~powershell
$body = @{ email = 'support1@utility.com'; password = 'Utility@123' } | ConvertTo-Json
$session = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/auth/support/login' -ContentType 'application/json' -Body $body
$token = $session.data.accessToken
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/api/support/dashboard' -Headers @{ Authorization = "Bearer $token" }
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/api/support/tickets?service=ELECTRICITY&status=OPEN&page=0&size=20' -Headers @{ Authorization = "Bearer $token" }
~~~
## Phase 2 smoke calls

After starting the development stack, authenticate and call the protected admin dashboard and account list:

~~~powershell
$login = @{ email = 'admin@utility.com'; password = 'Utility@123' } | ConvertTo-Json
$session = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/auth/admin/login' -ContentType 'application/json' -Body $login
$token = $session.data.accessToken
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/api/admin/dashboard' -Headers @{ Authorization = "Bearer $token" }
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/api/admin/accounts?page=0&size=20' -Headers @{ Authorization = "Bearer $token" }
~~~




## Phase 6 smoke calls

Authenticate as a customer to inspect notifications, then use an admin token to view reports and export tickets:

~~~powershell
$body = @{ email = 'customer1@utility.com'; password = 'Utility@123' } | ConvertTo-Json
$session = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/auth/customer/login' -ContentType 'application/json' -Body $body
$token = $session.data.accessToken
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/api/notifications?unreadOnly=true&page=0&size=20' -Headers @{ Authorization = "Bearer $token" }

$body = @{ email = 'admin@utility.com'; password = 'Utility@123' } | ConvertTo-Json
$session = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/auth/admin/login' -ContentType 'application/json' -Body $body
$token = $session.data.accessToken
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/api/admin/reports' -Headers @{ Authorization = "Bearer $token" }
Invoke-WebRequest -Uri 'http://localhost:8080/api/admin/data/tickets/export?format=csv' -Headers @{ Authorization = "Bearer $token" } -OutFile tickets.csv
~~~
