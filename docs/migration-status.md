# JDK 21 Migration Status

This document captures the in-progress migration of the Zanata platform from
JDK 8 / Java EE 7 / WildFly 10 to JDK 21 / Jakarta EE 10 / WildFly 30+.

## How to use

Run the helper script from the repository root:

```bash
./run.sh status        # show which modules build vs are blocked
./run.sh build         # build all working modules into the local M2 repo
./run.sh build-client  # build only the client side
./run.sh cli help      # run the migrated CLI (verified working on JDK 21)
```

Requirements: JDK 21 (Temurin recommended) and Maven 3.9+ on the `PATH`.

## What works (21 modules)

These modules compile and install on JDK 21:

- `:zanata-common-api`
- `:zanata-common-util`
- `:zanata-adapter-po`
- `:zanata-adapter-properties`
- `:zanata-adapter-xliff`
- `:zanata-adapter-glossary`
- `:stub-server` (Jetty 11, jakarta.servlet namespace)
- `:zanata-rest-client` (RESTEasy 6, `ResteasyClientBuilderImpl`)
- `:zanata-client-commands`
- `:zanata-cli` (verified runnable via `./run.sh cli`)
- `:zanata-maven-plugin` (maven-plugin-plugin 3.15.1)
- `:security-common`
- `:zanata-liquibase`
- `:zanata-model` (Hibernate 6 entities; search annotations stripped)
- `:zanata-model-test`

…plus the parent / aggregator poms (`build-tools`, `parent`, `api`, `common`,
`client`, `server`).

## What's blocked, and why

### `:services` (the biggest server module)

Several independent blockers:

1. **Hibernate Search 5 → 7** — the old `FieldBridge`, `TwoWayStringBridge`,
   `LuceneOptions`, `Filter`, `Discriminator`, `EntityIndexingInterceptor` API
   is gone. Replacement is `ValueBridge<V,F>` / `TypeBridge<E>`. ~10 bridge
   classes in `org.zanata.hibernate.search` need a real rewrite. The classes
   themselves have been moved to `docs/migration-disabled/hsearch5-bridges/`
   to unblock zanata-model.
2. **Hibernate 6 Criteria API** — `org.hibernate.criterion.*` is removed.
   ~40 DAOs use `DetachedCriteria`, `Restrictions`, `Projections`. Migration
   to JPA Criteria API needed.
3. **DeltaSpike exception-control** — 15 classes in
   `org.zanata.exception.handler.*` use `@Handles`, `ExceptionHandler`,
   `ExceptionEvent`. DeltaSpike 2.0 still has this module but it does not work
   with CDI 4 / Jakarta in all configurations. Replacement: CDI `@Observes`
   on a custom exception event, or plain JAX-RS `ExceptionMapper`.
4. **~30 Kotlin files** under `server/services` need to be ported to Java
   (per project decision). Files cover SAML auth, email rendering, GraphQL,
   async tasks, TM merge, etc.
5. **Custom Hibernate `UserType` rewrites** — the `org.zanata.model.type.*`
   custom types (`LocaleIdType`, `EntityTypeType`, `WebhookType`, etc.) were
   stripped to make zanata-model compile. To restore type-safe DB mapping
   they need to be re-implemented as Hibernate 6 `UserType<E>` against the
   new `BasicJavaType` / `JdbcType` SPI. Sources are preserved in
   `docs/migration-disabled/hibernate5-custom-types/`.

### `:gwt-shared`, `:gwt-test`, `:gwt-editor`

- `de.novanic.gwteventservice:eventservice-rpc:jar:1.5.0` is not in Maven
  Central. The project needs to be re-pinned to a published version, or the
  dependency must be vendored.
- GWT 2.12.2 was bumped down to 2.11.0 (last release available on Central).
- GWT-RPC serialization on Hibernate 6 entities is unverified.

### `:zanata-war`, `:zanata-test-war`, `:functional-test`

Transitive: depend on `:services` and the GWT modules.

### `:zanata-frontend`

Independent of the JVM build but blocked by:
- React 16.4 → 18/19
- Redux 3.5 → Redux Toolkit
- Webpack 4 → 5 (or Vite)
- `redux-api-middleware` is effectively dead — replace with RTK Query
- Enzyme is dead — replace with React Testing Library
- TypeScript 3 → 5
- `awesome-typescript-loader` dead — use `ts-loader`

### `zanata-validators` (re-enable later)

The annotation classes (`@Unique`, `@Url`, `@Slug`, `@EmailDomain`,
`@ZanataEmail`) are kept in `org.zanata.model.validator`. Their validator
implementations have been **stubbed to always return `true`** because the
originals used Hibernate 5 `DetachedCriteria` / `Restrictions`. The
implementations need to be re-ported to JPA Criteria. Originals preserved
in `docs/migration-disabled/validator/`.

### `OkapiUtil.countWords`

Stubbed to a whitespace word-count. Original used the
`net.sf.okapi.steps.tokenization` API which was reorganized in newer Okapi
versions. Restore by porting to the current tokenizer API.

## Build-infrastructure changes

- `.mvn/maven.config` — removed `--builder smart` (Takari dead)
- `.mvn/extensions.xml` — removed Takari smart-builder, aether-connector-okhttp, buildtime
- Maven wrapper 3.5.4 → 3.9.9
- JDK target 1.8 → 21 in `parent/pom.xml`
- Maven plugins bumped: compiler 3.13, surefire 3.5.2, jacoco 0.8.12, javadoc
  3.11.2, jar 3.4.2, source 3.3.1, war 3.4.0, etc.
- Removed legacy plugins entirely: animal-sniffer, findbugs, restrict-maven,
  duplicate-finder, gitdescribe, sortpom-google, gmaven-plugin
- `kotlin-maven-plugin` removed from `server/pom.xml`
- Plexus Eclipse compiler removed from `zanata-maven-plugin/pom.xml`

## Dependency upgrades (server pom)

| What | From | To |
|------|------|----|
| Hibernate ORM | 5.0.7 | 6.6.4.Final |
| Hibernate Search | 5.5.1 | 7.2.2.Final |
| Hibernate Validator | 5.2.4 | 8.0.2.Final |
| RESTEasy | 3.0.19 | 6.2.11 |
| Weld | 2.3.2 | 5.1.3 |
| Infinispan | 8.1 | 14.0.33 |
| DeltaSpike | 1.7 | 2.0.0 |
| Arquillian | 1.1 | 1.9.2 |
| Lucene | 5.3 | 9.12.0 |
| GWT | 2.8 | 2.11.0 |
| Groovy | 2.4 | 4.0.24 |
| Jetty | 9.2 | 11.0.24 (stub-server) |
| WildFly client | 2.0 | 30.0.1 |
| MySQL connector | `mysql:mysql-connector-java:5.1.26` | `com.mysql:mysql-connector-j:9.1.0` |
| Jackson | codehaus 1.9.13 | fasterxml 2.18.2 |
| JAXB / Activation / WS / Mail | javax 2.x | jakarta 2.x / 4.x |
| Guava | 18.0 | 33.4.0-jre |
| Liquibase | 3.0.7 | 4.29.2 |
| slf4j | 1.7 | 2.0.16 |

## Source-code transformations applied

- ~3,145 `javax.* → jakarta.*` imports across 1,006 files (Jakarta EE 9
  namespace flip). JSR-305 nullability annotations (`Nullable`, `Nonnull`,
  `CheckForNull`, etc.) kept in `javax.annotation` since they were never part
  of the Jakarta rename.
- ~347 library-package renames across 220 files:
  - DeltaSpike `@Transactional` → `jakarta.transaction.Transactional` (102x)
  - DeltaSpike lifecycle `Initialized`/`Destroyed` → CDI standard
  - Codehaus Jackson 1.x → FasterXML Jackson 2.x
  - JBoss-spec `javax.*` wrappers → Jakarta direct
- 1 Kotlin file ported: `HLocale.kt → HLocale.java`. 84 remain.
- Hibernate Search annotations stripped from 14 model entities.
- `@TypeDef` / `@TypeDefs` annotations removed (Hibernate 6 doesn't have them).
- ResteasyClientBuilder usages converted to `ResteasyClientBuilderImpl` (which
  is the concrete impl after RESTEasy 4+).

## Helper scripts (kept in /tmp during this work, copied here for reference)

- `/tmp/migrate_poms.py` — pom GAV coordinate renames
- `/tmp/jakarta_migrate.py` — `javax.* → jakarta.*` in source files
- `/tmp/source_renames.py` — DeltaSpike, Jackson, JBoss-spec renames in source
- `/tmp/fix_jsr305.py` — revert JSR-305 over-applied renames
- `/tmp/cleanup_poms.py` — strip stax-api, async-http-servlet; add
  `<type>pom</type>` to groovy-all references
- `/tmp/fix_hib_validator.py` — `org.hibernate` → `org.hibernate.validator`
- `/tmp/fix_more_poms.py` — Hibernate Search + Hibernate ORM groupId moves
- `/tmp/remove_gmaven.py` — strip dead gmaven-plugin blocks
- `/tmp/strip_hsearch.py` — strip Hibernate Search annotations from entities
- `/tmp/strip_typedef.py` — strip `@TypeDef` annotations, rewrite `@Type`

## Verifying the CLI runs

```bash
$ ./run.sh cli help
Usage: zanata-cli [[help | init | list-remote | pull | push | put-project | put-user | put-version | stats | glossary-delete | glossary-push | glossary-pull]] ...
```

The CLI compiles to `client/zanata-cli/target/appassembler/bin/zanata-cli`
and runs on `java -version`-reported `Temurin-21.0.10`.
