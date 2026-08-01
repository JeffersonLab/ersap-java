// Spock configuration for the `test` task.
//
// build.gradle points `spock.configuration` at this file, and Spock aborts test
// discovery when the configured script is missing, so it must exist even when
// there is nothing to configure. Unit tests run with the defaults; the
// integration filtering is done by the JUnit Platform `excludeTags` in
// build.gradle.
runner {
}
