#!/usr/bin/env groovy
// Jenkins pipeline for the post-migration Zanata stack (Spring Boot 4 + Vaadin 25).
// The legacy stages (GWT compile, WildFly Cargo deploy, MySQL provisioning,
// Selenium functional tests, React frontend bundle) are gone — the modern
// build is just `mvn install` over the reactor plus an artifact archive.

@Library('zanata-pipeline-library@v0.4.2') _
import org.zanata.jenkins.Notifier
import groovy.transform.Field

@Field
def mainlineBranches = ['master', 'release', 'legacy']

properties([
  parameters([
    booleanParam(defaultValue: false, description: 'Run full Maven verify (slower, runs integration tests)', name: 'fullVerify'),
  ]),
  buildDiscarder(logRotator(numToKeepStr: '10', daysToKeepStr: '30')),
])

String getLockName() {
  return env.BRANCH_NAME in mainlineBranches ? env.JOB_NAME : "${env.JOB_NAME}-pr"
}

lock(resource: getLockName(), quantity: 1) {
  timestamps {
    node('master-node') {
      stage('Checkout') {
        checkout scm
      }

      stage('Build') {
        def goal = (params.fullVerify ?: false) ? 'verify' : 'install'
        sh """
          ./mvnw -B -V -e -ntp -Dstyle.color=never \\
            -DskipFuncTests \\
            -Dmaven.test.failure.ignore=true \\
            ${goal}
        """
      }

      stage('Archive') {
        junit allowEmptyResults: true, testResults: '**/target/surefire-reports/TEST-*.xml,**/target/failsafe-reports/TEST-*.xml'
        archiveArtifacts allowEmptyArchive: true, artifacts: [
          'server/zanata-server-spring/target/zanata-server-spring-*.jar',
          'client/zanata-cli/target/zanata-cli-*-dist.tar.gz',
          'client/zanata-cli/target/appassembler/**',
          'client/zanata-maven-plugin/target/zanata-maven-plugin-*.jar',
        ].join(',')
      }
    }
  }
}
