# Migration Status

The migration from the original Zanata stack
(JDK 8 / Java EE 7 / WildFly 10 / GWT / React 16) to the modern stack
(JDK 21 / Spring Boot 4 / Vaadin 25 / Hibernate 7) is complete enough that
the platform builds and runs out of the box. This file records what changed,
what was retired, and what's still on the wish list.

## Active modules (post-cutover)

```
api/zanata-common-api         — JAX-RS interfaces + DTOs (shared between CLI + server)
common/zanata-adapter-{po,xliff,properties,glossary}
common/zanata-common-util
client/stub-server
client/zanata-rest-client
client/zanata-client-commands
client/zanata-cli
client/zanata-maven-plugin
server/zanata-liquibase
server/zanata-model
server/zanata-model-test
server/security-common
server/zanata-server-spring   ← the new Spring Boot app (REST + Vaadin UI)
```

`mvn -DskipTests install` walks the whole reactor and finishes cleanly.

## Retired

| Removed                          | Replacement / reason                                                |
| -------------------------------- | ------------------------------------------------------------------- |
| `server/gwt-editor`              | GWT editor UI; superseded by Vaadin editor in `zanata-server-spring`. |
| `server/gwt-shared`              | GWT-RPC DTOs; never used outside gwt-editor.                        |
| `server/gwt-test`                | GWT test harness.                                                   |
| `server/zanata-frontend`         | React 16 + Webpack 4 + Node 8 admin UI; replaced by Vaadin views.   |
| `server/services`                | DeltaSpike / Hibernate 5 / JSF service layer; functionality ported piecewise into Spring controllers + repositories. |
| `server/zanata-war`              | WildFly WAR; replaced by executable Spring Boot JAR.                |
| `server/zanata-test-war`         | Arquillian fixture WAR.                                             |
| `server/functional-test`         | Cargo + WildFly + MySQL + Selenium 3 suite; future plan is Playwright + TestContainers Postgres under `zanata-server-spring/src/test`. |
| `server/zanata-model/src/main/java-disabled/` | 23 H5 UserTypes (replaced by `@Enumerated(EnumType.STRING)`), 15 HS5 bridges (entity HS annotations commented out), 74 scaffolding stubs, OkapiUtil + JPACopier. |
| `server/docker/Dockerfile.zanata`, `conf/`, `common/`, `run*.sh` | WildFly 10 base image + WAR overlay + MySQL module config; replaced by a single multi-stage Dockerfile that produces a Spring Boot JAR image. |
| `run.sh`                         | Old per-module reactor walker that ended in `wildfly:run`. Replaced by `./build --server --run`. |
| GWT plugin + `wildfly8` / `jbosseap6` Maven profiles | Dead build paths once the modules above were gone.                  |

## What still needs work

- **Hibernate Search 7 indexing.** Live entities no longer have any
  Search annotations (the HS5 ones were commented out and the bridges
  removed). `SearchAdminService.reindexAll()` runs but is effectively a
  no-op. The UI's global search bar relies on repository `LIKE` queries
  instead. Wiring `@Indexed` + `@FullTextField` / `@KeywordField` /
  per-locale analyzers on `HProject`, `HTextFlow`, `HTextFlowTarget`,
  `HGlossaryEntry`, `HGlossaryTerm` is the headline future-work item.
- **`copyTrans`.** The CLI bridge returns `501 Not Implemented` for
  `POST /rest/copytrans/proj/{slug}/iter/{iter}/doc/{docId}`; the real
  translation-memory copy algorithm hasn't been ported.
- **Translation Memory REST endpoints.** `/rest/tm` is not implemented.
- **PO header / gettext extension round-trip.** `push --push-type=both`
  followed by `pull` produces a working file with correct `msgstr`
  values, but the CLI emits "No PO header in document named X" and
  "Missing POT entry for text-flow ID …" warnings because the bridge
  doesn't currently persist the gettext extension blob (PO header /
  comments / references) attached to source documents and translation
  resources. The translated strings themselves are correct.
- **Functional / end-to-end UI tests.** Need to rewrite on top of
  Playwright + TestContainers; the old Selenium suite was tied to JSF
  selectors that no longer exist.
- **Server-side paging on Vaadin grids beyond the first page.** A few
  admin grids still pull every row.

## Quick start

```bash
# build
mvn -DskipTests install

# run server + Postgres
docker compose -f server/docker/docker-compose.yml up --build

# verified end-to-end against the bridge (admin / API key above)
CLI=client/zanata-cli/target/appassembler/bin/zanata-cli
$CLI version       --url http://localhost:8080/ --user-config /dev/null \
                   --username admin --key b6d7044e9ee3b720c81dba7e3ea53d56
$CLI list-remote   ...
$CLI stats         ...
$CLI put-project   ...   # creates a project from scratch
$CLI put-version   ...
$CLI push --push-type both   # source + translations (gettext layout: po/{locale}.po)
$CLI pull                    # translations round-trip into the local tree
```

Seeded test accounts (deterministic API keys, dev environment only):

| Username | Password | API key                                |
| -------- | -------- | -------------------------------------- |
| admin    | admin    | `b6d7044e9ee3b720c81dba7e3ea53d56`     |
| dev      | dev      | `11111111111111111111111111111111`     |
| vistall  | 1234     | `22222222222222222222222222222222`     |
