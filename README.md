![Gradle build](https://img.shields.io/github/actions/workflow/status/3dcitydb/citydb-tool/build-citydb-tool.yml?logo=Gradle&logoColor=white&style=flat-square) [![Docker edge image build](https://img.shields.io/github/actions/workflow/status/3dcitydb/citydb-tool/docker-build-push-edge.yml?label=edge&logo=docker&style=flat-square)](https://github.com/users/3dcitydb/packages/container/package/citydb-tool)

# citydb-tool

3D City Database 5.0 CLI to import/export city model data and to run database operations

## License

The citydb-tool is licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).
See the `LICENSE` file for more details.

## Latest release

The latest stable release of the citydb-tool is `0.5-beta`.

## Contributing

* To file bugs found in the software create a GitHub issue.
* To contribute code for fixing filed issues create a pull request with the issue id.
* To propose a new feature create a GitHub issue and open a discussion.

## Using

Download and unzip the latest release or [build](https://github.com/3dcitydb/citydb-tool#building) the program from
source. Afterwards, open a shell environment and run the `citydb` script from the program folder to launch the
program.
Another option is to use the
[`citydb-tool` Docker image](https://github.com/3dcitydb/citydb-tool#docker).

To show the help message and all available commands of the citydb-tool, simply type the following:

    > citydb --help

This will print the following usage information:

```
Usage: citydb [-hV] [-L=<level>] [--log-file=<file>] [--pid-file=<file>]
              [@<filename>...] COMMAND
Command-line interface for the 3D City Database.
      [@<filename>...]      One or more argument files containing options.
  -L, --log-level=<level>   Log level: fatal, error, warn, info, debug, trace
                              (default: info).
      --log-file=<file>     Write log messages to this file.
      --pid-file=<file>     Create a file containing the process ID.
  -h, --help                Show this help message and exit.
  -V, --version             Print version information and exit.
Commands:
  help    Display help information about the specified command.
  import  Import data in a supported format.
  export  Export data in a supported format.
  delete  Delete features from the database.
  index   Perform index operations on the database.
```

To get help about a specific command of the citydb-tool, enter the following and replace `COMMAND` with the name of
the command you want to learn more about:

    > citydb help COMMAND

## System requirements

* Java 11 or higher

The citydb-tool can be run on any platform providing appropriate Java support.

## Docker

Currently, we offer an `edge` Docker image of the tool, which is built
from the latest commit to the `main` branch. The image supports the most
common architectures (`amd64`, `arm64`, `arm/v7`) and is available from
Dockerhub or Github packages.

    docker pull 3dcitydb/citydb-tool:edge
    docker pull ghcr.io/3dcitydb/citydb-tool:edge

### Synopsis

The Docker image exposes the commands of the `citydb-tool`, as described
in the [usage section](https://github.com/3dcitydb/citydb-tool#usage).
The environment variables listed below can be used to specify a
3DCityDB v5 connection. To exchange data with the container, mount a host folder to `/data` inside the container.

    docker run --rm --name citydb-tool [-i -t] \
        [-e CITYDB_HOST=the.host.de] \
        [-e CITYDB_PORT=5432] \
        [-e CITYDB_NAME=theDBName] \
        [-e CITYDB_SCHEMA=theCityDBSchemaName] \
        [-e CITYDB_USERNAME=theUsername] \
        [-e CITYDB_PASSWORD=theSecretPass] \
        [-v /my/data/:/data] \
    3dcitydb/citydb-tool[:TAG] COMMAND

## Building

The citydb-tool uses [Gradle](https://gradle.org/) as build system. To build the program from source, clone the
repository to your local machine and run the following command from the root of the repository.

    > gradlew installDist

The script automatically downloads all required dependencies for building and running the citydb-tool. So make sure you
are connected to the internet. The build process runs on all major operating systems and only requires a Java 11 JDK or
higher to run.

If the build was successful, you will find the citydb-tool package under `citydb-cli/build/install`.
