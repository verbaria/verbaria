# Disabled sources

Source files needing a Hibernate-Search-7 / Hibernate-6 port are kept inside
their module under `src/main/java-disabled/` (Maven excludes `java-disabled` by
default — only `src/main/java` is compiled). They remain visible in the repo so
re-enabling them is just a matter of moving them back into `src/main/java/`
and porting the API calls.

| Module | Disabled path | Contents |
|--------|---------------|----------|
| zanata-model | `src/main/java-disabled/org/zanata/hibernate/search/` | Hibernate Search 5 bridges, filters, interceptors. Port to Hibernate Search 7 `ValueBridge<V,F>` / `TypeBridge<E>`. |
| zanata-model | `src/main/java-disabled/org/zanata/model/type/` | Custom Hibernate 5 `UserType` wrappers (`LocaleIdType`, `EntityTypeType`, etc.). Port to Hibernate 6 `UserType<E>` API. |
| zanata-model | `src/main/java-disabled/org/zanata/util/{OkapiUtil,JPACopier}.java` | Use removed Okapi tokenization API / `HibernateProxyHelper`. |
| zanata-model | `src/main/java-disabled/stubs/` | Throwaway Hibernate Search 5 / Lucene 5 stub APIs I created during the restore attempt. Delete when the real ports land. |

`org.zanata.model.validator.*Validator` classes have been **stubbed** in
`src/main/java/` (always return `true`) so callers compile. Originals are in
git history.
