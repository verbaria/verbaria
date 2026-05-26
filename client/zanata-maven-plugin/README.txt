Verbaria Maven plugin
=====================

Maven plugin for pushing and pulling translation files to/from a Verbaria
(formerly Zanata) server.

Plugin coordinates:   org.zanata:zanata-maven-plugin:5.0-SNAPSHOT
Goal prefix:          zanata


Goals
-----

  init             create a verbaria.json in the current directory
  push / pull      upload source / download translations
  push-module      multi-module variants of push / pull
  pull-module
  list-remote      list projects/versions on the server
  stats            translation statistics for a project version
  put-project      create/update a project
  put-version      create/update a project version
  put-user         create/update a user
  glossary-push    upload a CSV or PO glossary
  glossary-pull    download a glossary
  glossary-delete  delete glossary entries

Per-goal help:

  mvn zanata:help -Ddetail=true -Dgoal=push


Adding the plugin to a project
------------------------------

  <build>
    <plugins>
      <plugin>
        <groupId>org.zanata</groupId>
        <artifactId>zanata-maven-plugin</artifactId>
        <version>5.0-SNAPSHOT</version>
      </plugin>
    </plugins>
  </build>

Optional shortcut — register the goal-prefix in ~/.m2/settings.xml so you
can write `mvn zanata:push` instead of the full GA:

  <settings>
    <pluginGroups>
      <pluginGroup>org.zanata</pluginGroup>
    </pluginGroups>
  </settings>


Configuration files
-------------------

  verbaria.json (per-project, beside pom.xml)
    Server URL, project id, version, project-type, locale map, optional
    file-mapping rules.

  ~/.config/verbaria.ini (per-user; credentials)
    [servers]
    local.url      = http://localhost:8080/
    local.username = your-username
    local.key      = your-api-key

Generate the API key from the user profile page on the server.

Working examples per project-type live in the sibling `demo-*` directories.


Running a server
----------------

The server is a Spring Boot app; it needs PostgreSQL. See the root
README for the available run options (running via Maven is one of them,
container-based is another).
