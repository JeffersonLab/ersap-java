# ERSAP JAVA Binding

 An ***E***nvironment for ***R***ealtime ***S***treaming ***A***cquisition and ***P***rocessing,
 designed to process unbounded streams of continuous data at scale over distributed heterogeneous resources.

### ERSAP is modular
ERSAP presents a micro-services architecture for data-stream analytics. One can think of ERSAP as a software LEGO system to design and deploy scientific data processing applications without writing a single line of code. It is a data-in-motion platform to build streaming scientific-data analytics applications.
### ERSAP is resilient
ERSAP application stays responsive in the face of a failure. Resilience is achieved by service replication, failure containment, isolation and delegation. Failures are contained within each service, isolating services from each other and thereby ensuring that parts of the system can fail and recover without compromising the system as a whole.
### ERSAP is elastic
ERSAP system stays amenable under varying workloads. It reacts to changes in the input data-stream rate by increasing or decreasing the resource allocation to process an input stream. ERSAP orchestrator implements predictive, as well as reactive scaling algorithms by providing relevant live performance measures. We achieve elasticity in a cost-effective way on commodity hardware and software platforms.
### ERSAP is reactive
ERSAP uses asynchronous message-passing to establish boundaries between services that ensure loose coupling, isolation, location transparency, and provides means to delegate errors as messages. Employing explicit message-passing enables load balancing and overall data-flow, i.e. application algorithm control and orchestration.

## Build notes

ERSAP requires the Java 14 JDK.
Prefer [AdoptOpenJDK](https://adoptopenjdk.net/) for Java binaries,
and a Java version manager to install and switch JDKs.

[SDKMAN!], [Jabba] or [JEnv] can be used to manage multiple Java versions.
See this [StackOverflow answer](https://stackoverflow.com/a/52524114) for more details
(the answer is for macOS but they work with any Unix system).

[SDKMAN!]: https://sdkman.io/
[Jabba]: https://github.com/shyiko/jabba
[JEnv]: https://www.jenv.be/

With [SDKMAN!]:

``` console
$ sdk list java
$ sdk install java 14.0.1.hs-adpt       # there may be a newer version listed above
$ sdk use java 14.0.1.hs-adpt
```

With [Jabba]:

``` console
$ jabba ls-remote
$ jabba install adopt@1.14.0-1          # there may be a newer version listed above
$ jabba use adopt@1.14.0-1
```

To install [AdoptOpenJDK 14] system wide,
use the [AdoptOpenJDK DEB repo] for Ubuntu,
or [Homebrew](https://brew.sh/) with the [AdoptOpenJDK TAP] for macOS.

[AdoptOpenJDK 14]: https://adoptopenjdk.net/releases.html?variant=openjdk14&jvmVariant=hotspot
[AdoptOpenJDK DEB repo]: https://adoptopenjdk.net/installation.html#linux-pkg-deb
[AdoptOpenJDK TAP]: https://github.com/AdoptOpenJDK/homebrew-openjdk


### Installation
git clone https://github.com/JeffersonLab/ersap-java.git


To build ERSAP use the provided [Gradle](https://gradle.org/) wrapper.
It will download the required Gradle version and all the ERSAP dependencies.

    $ ./gradlew

To install the ERSAP artifact to the local Maven repository:

    $ ./gradlew publishToMavenLocal

To deploy the binary distribution to `$ERSAP_HOME`:

    $ ./gradlew deploy


### Importing the project into your IDE

Gradle can generate the required configuration files to import the ERSAP
project into [Eclipse](https://eclipse.org/ide/) and
[IntelliJ IDEA](https://www.jetbrains.com/idea/):

    $ ./gradlew cleanEclipse eclipse

    $ ./gradlew cleanIdea idea

See also the [Eclipse Buildship plugin](http://www.vogella.com/tutorials/EclipseGradle/article.html)
and the [Intellij IDEA Gradle Help](https://www.jetbrains.com/help/idea/2016.2/gradle.html).


## Docker & observability

Container- and metrics-related docs are split across a few files at the
root and under `docker/` — this section is a map of what each one is for.

| Doc | Purpose |
|---|---|
| [`docker/README.md`](docker/README.md) | How to build and run the ERSAP Docker image, and how to run the Prometheus + Grafana monitoring stack (`docker compose up`). Start here for anything Docker-related. |
| [`README-PROMETHEUS.md`](README-PROMETHEUS.md) | Full three-node deployment guide: two ERSAP pipeline nodes reporting metrics to a Monitor Front End, exported to Prometheus, visualized in Grafana. |
| [`ObservabilityTest.md`](ObservabilityTest.md) | A smaller two-node walkthrough of the same reporting path, using `TestMonitor` directly instead of Prometheus/Grafana — useful for verifying metrics flow without standing up the full stack. |
| [`docker/observability/Grafana_Dashboard_Config.md`](docker/observability/Grafana_Dashboard_Config.md) | What the Prometheus/Grafana config files actually do, and how to edit the Grafana dashboard itself. |

**Quick start — build and run the ERSAP image:**

```console
$ docker build -t ersap-java -f docker/Dockerfile .
$ docker run --rm -it ersap-java j_dpe --host 0.0.0.0 --port 7771 --session mydemo
```

**Quick start — run the monitoring stack** (Prometheus + Grafana, assumes a
`PrometheusExporter` is already running somewhere — see
`README-PROMETHEUS.md`):

```console
$ cd docker/observability
$ docker compose up -d
$ open http://localhost:3000   # Grafana, admin / changeme
```

See [`docker/README.md`](docker/README.md) for the full explanation and
more run options.

For assistance send email to [sro@jlab.org](mailto:sro@jlab.org).
