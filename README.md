# Zanata

Zanata is a web-based system for translators to translate documentation and
software online using a web browser. It supports gettext PO, XLIFF, Java
properties, glossary CSV/XLSX and several other formats, and provides a Maven
plugin and a command-line client for uploading source and downloading
translations.

This branch runs on a modernised stack:

| Layer          | Tech                                                |
| -------------- | --------------------------------------------------- |
| Runtime        | Java 21, Spring Boot 4.0.x                          |
| Persistence    | Hibernate ORM 7.2, Hibernate Search 8.x, PostgreSQL |
| UI             | Vaadin 25 (Aura theme), Line Awesome icons          |
| Build          | Maven 3.9+ (multi-module reactor)                   |
| Client         | Standalone CLI + Maven plugin (`zanata-cli`)        |

The legacy WildFly / JSF / CDI / GWT / React stack has been retired during
the migration; see `docs/migration-status.md` for what was dropped and what
remains.

Zanata is Free software, licensed under the [LGPL][].

[LGPL]: http://www.gnu.org/licenses/lgpl-2.1.html


## Developers: building from source

### Prerequisites

- JDK 21 (Eclipse Temurin or any other modern build)
- Maven 3.9+
- PostgreSQL 14+ (16 used in the docker-compose dev environment)
- A POSIX shell (bash or zsh) — only required if you use the
  optional `./build` helper

### Build everything

```bash
mvn -DskipTests install
```

This walks the root reactor: `build-tools`, `parent`, `api`, `common`,
`client`, `server/zanata-server-spring`.

### Run the server locally

#### Option A — Docker Compose (recommended)

Brings up Postgres + the Spring Boot server in one shot:

```bash
docker compose -f server/docker/docker-compose.yml up --build
```

UI: <http://localhost:8080/>. Admin login is seeded as `admin` / `admin`.

#### Option B — Maven on the host

Start a Postgres reachable at `localhost:5432` with database `zanata` and
user `postgres` / password `1234` (override via `ZANATA_DB_URL`,
`ZANATA_DB_USER`, `ZANATA_DB_PASSWORD`), then:

```bash
mvn -pl server/zanata-server-spring spring-boot:run
```

The server seeds three accounts on first boot — `admin/admin`, `dev/dev`,
`vistall/1234` — each with a deterministic API key the CLI smoke test uses.

### Run the CLI against the new server

```bash
# build the CLI uber-assembly
mvn -pl client/zanata-cli -DskipTests package

# point it at the running server
CLI=client/zanata-cli/target/appassembler/bin/zanata-cli
$CLI version --url http://localhost:8080/ --user-config /dev/null \
  --username admin --key b6d7044e9ee3b720c81dba7e3ea53d56
```

`list-remote`, `stats`, `push`, `pull`, `put-project`, `put-version`,
`glossary-push`, `glossary-pull` all hit the bridge controller defined in
`server/zanata-server-spring/src/main/java/org/zanata/spring/web/rest/ZanataCliBridgeController.java`.


## Module layout

```
api/zanata-common-api         — JAX-RS interfaces + DTOs (shared)
common/zanata-adapter-{po,xliff,properties,glossary}
common/zanata-common-util
client/{stub-server,zanata-rest-client,zanata-client-commands,
        zanata-cli,zanata-maven-plugin}
server/zanata-liquibase       — Liquibase migrations
server/zanata-model           — JPA entities (Hibernate 7)
server/zanata-model-test      — entity test fixtures
server/security-common        — auth / role primitives
server/zanata-server-spring   — Spring Boot app (REST + Vaadin UI)
server/docker                 — Dockerfile + docker-compose
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). PRs welcome.
