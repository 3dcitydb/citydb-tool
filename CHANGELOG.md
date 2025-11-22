# Changelog

## [Unreleased]

### Added
- Added a Java API for querying and processing feature changes from the `feature_changelog` table and aggregating
  them into change regions.
- Added the placeholders `@file_path@`, `@file_name@`, `@content_path@`, and `@content_name@` for the `--lineage`
  option of the `import` command, allowing file and ZIP entry paths and names to be imported as feature metadata. [#70](https://github.com/3dcitydb/citydb-tool/issues/70)
- Added the `--creation-date` option to the `import command` to overwrite creation dates from the input files. [#71](https://github.com/3dcitydb/citydb-tool/pull/71)
- Added `indexMode` to the JSON configuration of the `import` and `delete` commands. [#67](https://github.com/3dcitydb/citydb-tool/issues/67)
- Added methods to `PostgresqlAdapter` for retrieving the installed PostGIS and SFCGAL versions from the connected
  database.

### Changed
- Unified JSON output of the `connect` command with other commands. No JSON is generated in case of an error anymore.
- Password prompting is only supported if a console is available. Passwords are no longer read from `stdin`.
- Renamed the JSON schema `connection-status.schema.json` to `connection-info.schema.json`.

### Fixed
- The CLI option `--import-mode` overrode the JSON configuration even when not explicitly set on the command line.
- Fixed an NPE in the `info` command when changelog tracking is enabled.
- The `last_modification_date` now defaults to the current import time and is no longer tied to `creation_date`.

## [1.1.0] - 2025-08-24

### Added
- Added the `connect` command to test connections to a 3DCityDB instance. This command supports optional JSON output,
  which can be written to `stdout` or a file. [#64](https://github.com/3dcitydb/citydb-tool/pull/64)
- Introduced the `info` command for generating a summary report of 3DCityDB contents. Reports can be produced in JSON
  format and written to `stdout` or a file for further processing. [#61](https://github.com/3dcitydb/citydb-tool/pull/61)
- Added support for XSLT/XPath 2.0 and 3.0 to the `citygml import` and `citygml export` commands.
- Added the `--output` option to the `index status` command, enabling the index status list to be written as JSON
  to `stdout` or a file.

### Changed
- Updated and harmonized CLI options and their descriptions.
- Texture coordinates are now processed and represented as `float` values instead of `double`.
- Replaced the hard dependency on Apache Log4j with SLF4J as the logging abstraction for all API modules. [#62](https://github.com/3dcitydb/citydb-tool/pull/62)

### Fixed
- Fixed support for time-based validity options in the JSON configuration.
- Fixed `count` methods in `SqlHelper` to generate correct SQL statements.
- Ensured operands are properly cast to `geometry` in `SpatialOperatorHelper` to prevent SQL exceptions.
- Resolved `MVStoreException: Chunk not found` errors when resolving references during imports.

## [1.0.0] - 2025-03-17

This is the initial release of citydb-tool.

[Unreleased]: https://github.com/3dcitydb/citydb-tool/compare/v1.1.0..HEAD
[1.1.0]: https://github.com/3dcitydb/citydb-tool/releases/tag/v1.1.0
[1.0.0]: https://github.com/3dcitydb/citydb-tool/releases/tag/v1.0.0