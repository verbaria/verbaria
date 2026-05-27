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

- JDK 25 (Eclipse Temurin or equivalent)
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

  | env var                | default              |
  | ---------------------- | -------------------- |
  | `VERBARIA_PORT`        | `8080`               |
  | `VERBARIA_TIMEZONE`    | `UTC`                |
  | `VERBARIA_DB_HOST`     | `localhost`          |
  | `VERBARIA_DB_PORT`     | `5432`               |
  | `VERBARIA_DB_NAME`     | `verbaria`           |
  | `VERBARIA_DB_USER`     | `postgres`           |
  | `VERBARIA_DB_PASSWORD` | `1234`               |
  | `VERBARIA_DB_OPTIONS`  | *(empty)* — JDBC query string fragment, e.g. `options=-c%20TimeZone%3DUTC` |
  | `VERBARIA_ADMIN_LOGIN`    | *(empty)* — admin username to bootstrap on first boot |
  | `VERBARIA_ADMIN_PASSWORD` | *(empty)* — admin password to bootstrap on first boot |

  Setting both `VERBARIA_ADMIN_LOGIN` and `VERBARIA_ADMIN_PASSWORD`
  creates an account with `admin` + `user` roles on startup, but only
  when the `production` Spring profile is active (the Docker image
  enables it by default via `SPRING_PROFILES_ACTIVE=production`) AND
  no account with that username already exists. Leaving either var
  empty disables the bootstrap (no-op). Existing admin passwords are
  never overwritten by this mechanism.

- **Executable jar** — `mvn -pl server/zanata-server-spring package` then
  `java -jar server/zanata-server-spring/target/*.jar`, with the same env
  vars.

- **Docker** — build and run the image directly. Point
  `VERBARIA_DB_HOST` (and the rest of the `VERBARIA_DB_*` vars) at a
  Postgres reachable from inside the container — typically a service
  name on the same Docker network, a k8s service, or an external host:

  ```bash
  docker build -t verbaria-server .

  docker run --rm -p 8080:8080 \
    -e VERBARIA_DB_HOST=<your-postgres-host> \
    -e VERBARIA_DB_NAME=verbaria \
    -e VERBARIA_DB_USER=postgres \
    -e VERBARIA_DB_PASSWORD=<password> \
    verbaria-server
  ```

  Defaults are listed in the env var table above; override any of
  `VERBARIA_PORT`, `VERBARIA_TIMEZONE`, `VERBARIA_DB_*` via `-e` flags.

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
Dockerfile                    — container build (optional)
```


## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). PRs welcome.
