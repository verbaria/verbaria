Guide to translation build scripts
==================================

These wrapper scripts in `etc/scripts/` show one CI workflow for shipping
documentation translations through a Verbaria server. They drive the
`org.zanata:zanata-maven-plugin` goals (`push`, `pull`, etc.) alongside
whatever tool produces your source POT files.


Translation workflow
--------------------

1. Author edits the source documentation; the build produces fresh POT
   files.
2. At a content freeze, the **import** script pushes the new POT files
   to the Verbaria server (`zanata_import_source`).
3. Translators work in the Verbaria web UI.
4. **Draft builds** pull in-progress translations into `target/draft/`
   so reviewers can see how docs render (`zanata_draft_build`). Re-run
   after every author change.
5. Once translations are declared final, the **release build** pulls
   them into the source tree so the normal release pipeline picks them
   up (`zanata_export_translations`).


Build-machine configuration
---------------------------

Create `~/.config/verbaria.ini`:

```ini
[servers]
local.url      = http://localhost:8080/
local.username = your-username
local.key      = your-api-key
```

Generate the API key from the user profile page on the server.


The scripts
-----------

| Script                          | When                                    |
| ------------------------------- | --------------------------------------- |
| `zanata_import_all`             | Once, when first wiring up the project  |
| `zanata_import_source`          | After a content freeze                  |
| `zanata_draft_build`            | Nightly / on-demand reviewer previews   |
| `zanata_export_translations`    | Final release build                     |

All four live under `etc/scripts/`.


Project-level config
--------------------

The project's `verbaria.json` (beside `pom.xml`) tells the plugin which
server, project id, version, project-type and locale map to use. See the
`demo-*` directories in the maven-plugin module for working examples per
project-type.
