# Verbaria

(Formerly Zanata) — a web-based system for translators to translate
documentation and software online using a web browser. Supports gettext PO,
XLIFF, Java properties, glossary CSV/XLSX and several other formats, and
ships a Maven plugin plus a standalone CLI for pushing source and pulling
translations.

Licensed under the [LGPL][].

[LGPL]: http://www.gnu.org/licenses/lgpl-2.1.html


## Developers — build from source

### Prerequisites

- JDK 21 (Eclipse Temurin or equivalent)
- Maven 3.9+
- PostgreSQL 14+ (only when running the server)

### Build everything

```bash
mvn -DskipTests install
```

Walks the root reactor: `build-tools`, `parent`, `api`, `common`, `client`,
`server/zanata-server-spring`.

There is also a thin shell wrapper `./build` with common shortcuts
(`./build --quick`, `./build --client`, `./build --server --run`). It just
calls `mvn` under the hood.

### Run the server locally

The server is a Spring Boot app and needs a running PostgreSQL.

Suggestions (pick whichever fits your setup):

- **Maven** — `mvn -pl server/zanata-server-spring spring-boot:run`
  against an existing Postgres. DB env vars (defaults shown):

  | env var              | default                                                                 |
  | -------------------- | ----------------------------------------------------------------------- |
  | `ZANATA_DB_URL`      | `jdbc:postgresql://localhost:5432/zanata?options=-c%20TimeZone%3DUTC`   |
  | `ZANATA_DB_USER`     | `postgres`                                                              |
  | `ZANATA_DB_PASSWORD` | `1234`                                                                  |

- **Executable jar** — `mvn -pl server/zanata-server-spring package` then
  `java -jar server/zanata-server-spring/target/*.jar`, with the same env
  vars.

- **Docker Compose** — `docker compose -f server/docker/docker-compose.yml
  up --build` bundles Postgres + the server in one shot.

UI: <http://localhost:8080/>. On first boot the server seeds three accounts
(`admin/admin`, `dev/dev`, `vistall/1234`) each with a deterministic API
key the CLI smoke test uses.

### Run the CLI against the server

```bash
mvn -pl client/zanata-cli -DskipTests package

CLI=client/zanata-cli/target/appassembler/bin/verbaria
$CLI --version
$CLI list-remote --url http://localhost:8080/ \
                 --username admin --key b6d7044e9ee3b720c81dba7e3ea53d56
```

Goals: `version`, `list-remote`, `stats`, `init`, `push`, `pull`,
`put-project`, `put-version`, `put-user`, `glossary-push`, `glossary-pull`,
`glossary-delete`.

All of them hit the bridge controller at
`server/zanata-server-spring/src/main/java/org/zanata/spring/web/rest/ZanataCliBridgeController.java`.


## Module layout

```
api/zanata-common-api         — Shared DTOs + JSON API path constants
common/zanata-adapter-{po,xliff,properties,glossary}
common/zanata-common-util
client/stub-server            — Spring Boot test stub of the server
client/zanata-rest-client     — Spring RestClient wrappers
client/zanata-client-commands — push/pull/glossary/etc. command logic
client/zanata-cli             — Spring Boot CLI (binary: `verbaria`)
client/zanata-maven-plugin    — Maven plugin wrapping the same commands
server/zanata-liquibase       — Liquibase migrations
server/zanata-model           — JPA entities (Hibernate 7)
server/zanata-model-test      — entity test fixtures
server/security-common        — auth / role primitives
server/zanata-server-spring   — Spring Boot app (REST + Vaadin UI)
server/docker                 — Dockerfile + docker-compose (optional)
```


## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). PRs welcome.
