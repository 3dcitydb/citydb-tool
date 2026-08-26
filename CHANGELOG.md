# Changelog

## [Unreleased]

### Changed
- Renamed the `--plugins` option to `--extensions` to cover all types of citydb-tool extensions.
- Improved export performance for datasets with implicit geometries by reusing them across top-level features by
  default.
- Optimized preprocessing of implicit geometries during CityGML/CityJSON imports, improving performance and reducing
  memory usage.
- Improved performance when resolving global appearances while reading and importing CityGML datasets.
- **Breaking**: Changed the `TOP_LEVEL_FEATURE` mode of the `ImplicitGeometryScope` reader option to return independent
  copies of implicit geometry objects instead of shared objects, allowing each feature to be processed independently.
- **Breaking**: Enhanced the Plugin API to support subcommands and feature processors for the `import` and
  `export` commands.
- **Breaking**: Made several changes to the data model in `org.citydb.model`, including:
  - Removed the `Shareable` base class and restored parent references for implicit geometries.
  - Refactored the `PropertyMap` API for setting and accessing feature properties.
  - Replaced string-based implicit geometry references with `ImplicitGeometryReference`.

### Added
- Added `ImplicitGeometryScope` export option to control how implicit geometries are shared between top-level
  features when exporting data from the database.
  - `GLOBAL` (default): Implicit geometries are exported once and reused across all top-level features.
  - `TOP_LEVEL_FEATURE`: Implicit geometries are exported for each top-level feature and reused only within that
    feature. This mode produces self-contained features, simplifying parallel processing but substantially increasing
    export time. This reflects the previous default behavior.
- Added `--skip-empty-tiles` option to the `export` command to delete empty tile files during tiled exports.
- Added support for configuring metadata properties in output files through `metadataOptions` in the JSON configuration.
- Added CityGML write options to the JSON configuration, allowing XML prefixes and schema locations to be customized in
  output files.
- Added support for deep copies of all model classes.
- Added a state-preserving `prepass` to `FeatureReader`, avoiding repeated processing of global appearances and
  implicit geometries.
- Added a JSON schema for plugin metadata.

### Fixed
- Fixed an issue where duplicate property entries in the database could cause subsequent feature properties to be
  missing from exports.
- Fixed CityGML xAL 3.0 address output to use explicit element types following the CityGML 3.0 specification
  and examples.
- Fixed reference points of implicit geometries not being transformed into the target reference system during exports.
  [#83](https://github.com/3dcitydb/citydb-tool/issues/83)
- Fixed incorrect envelope computation for implicit geometries during database imports.
- Fixed resolution of cross-LoD references for implicit geometries.
- Fixed the `mimeType` property not being written in CityGML exports for implicit geometries using library objects.
- Fixed input files not being closed properly during imports, preventing resource leaks.
- Fixed incorrect entry names when creating ZIP input files with the `InputFiles` helper.
- Fixed the LoD filter not being reset between feature exports.

## [1.3.2] - 2026-05-31

### Fixed
- Fixed a regression that prevented textures assigned to implicit geometries through global appearances from
  being imported.

## [1.3.1] - 2026-05-08

### Fixed
- Fixed the import of implicit geometries when import filters are applied.
- Fixed the import of implicit geometries and global appearances when `--import-mode` is set to `terminate`.
- Fixed an issue in the database report where properties of generic feature types were incorrectly counted as
  generic attributes.
- Fixed a concurrency issue that could lead to a `NullPointerException` when resolving global and cross-LoD geometry
  references during imports.

## [1.3.0] - 2026-03-09

### Added
- Added `ImplicitGeometryScope` reader option to control how implicit geometries are shared between top-level
  features when reading CityGML/CityJSON files.
  - `GLOBAL` (default): implicit geometries are parsed once and reused across all top-level features.
  - `TOP_LEVEL_FEATURE`: implicit geometries are resolved per top-level feature and reused only within that
    feature. This mode produces self-contained features, simplifying parallel processing. A new `ReferenceResolver`
    helper class provides convenient access to referenced objects. With this mode, the resulting top-level feature
    structure matches that of the database export. [#73](https://github.com/3dcitydb/citydb-tool/pull/73)

### Changed
- Improved error handling when writing ZIP output files.
- Improved import performance for features having many appearances.
- Improved import performance for features with deeply nested sub-features.
- Removed `TextureImageProperty` and `Reference` from the data model.
- Reworked `DatabaseAdapter` interface to simplify support for databases beyond PostgreSQL/PostGIS.

### Fixed
- Fixed an issue where the file name was not correctly retrieved from texture images provided as URLs.
- Fixed reading and importing duplicate sub-features within the same top-level feature.
- Fixed local references to library objects in CityGML exports.
- Fixed missing sub-features referenced from multiple parent features in CityGML exports.
- Fixed CityJSON export to avoid using newline-delimited `CityJSONSeq` for CityJSON version `1.0`.
- Fixed loading of database and I/O adapters as external plugins.

## [1.2.0] - 2025-12-06

### Added
- Added a Java API for querying and processing feature changes from the `feature_changelog` table and aggregating
  them into change regions.
- Added the placeholders `@file_path@`, `@file_name@`, `@content_path@`, and `@content_name@` for the `--lineage`
  option of the `import` command, allowing file and ZIP entry paths and names to be imported as feature metadata. [#70](https://github.com/3dcitydb/citydb-tool/issues/70)
- Added the `--creation-date` option to the `import` command to overwrite creation dates from the input files. [#71](https://github.com/3dcitydb/citydb-tool/pull/71)
- Added `indexMode` to the JSON configuration of the `import` and `delete` commands. [#67](https://github.com/3dcitydb/citydb-tool/issues/67)
- Added the `--compact` option to the `info` command to generate a more concise database report.
- Added methods to `PostgresqlAdapter` for retrieving the installed PostGIS and SFCGAL versions from the connected
  database.

### Changed
- Unified JSON output of the `connect` command with other commands. No JSON is generated in case of an error anymore.
- Password prompting is now only supported if a console is available. Passwords are no longer read from `stdin`.
- Generic attributes are now grouped by feature types in the database report generated by the `info` command.
- Renamed the JSON schema `connection-status.schema.json` to `connection-info.schema.json`.
- Updated `ModelObjectWriter` to support thread-safe writing of model objects to a single file or stream.
- Updated `ModelObjectReader` to support reading model objects into a list or consuming them in chunks.
- Removed the `FeatureCollection` class due to changes in `ModelObjectWriter` and `ModelObjectReader`.

### Fixed
- Fixed 2D spatial queries on `geometry_data` so they no longer fail when encountering `PolyhedralSurface` geometries.
- The CLI option `--import-mode` no longer overrides the JSON configuration even when not explicitly set on the
  command line.
- Fixed an NPE in the `info` command when changelog tracking is enabled.
- The `last_modification_date` now defaults to the current import time and is no longer tied to `creation_date`.
- CLI options for appearance or LoDs in the `export` command no longer overwrite a query in the JSON configuration.
- Fixed start scripts to respect the 8191-character command line limit on Windows.

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

[Unreleased]: https://github.com/3dcitydb/citydb-tool/compare/v1.3.2..HEAD
[1.3.2]: https://github.com/3dcitydb/citydb-tool/releases/tag/v1.3.2
[1.3.1]: https://github.com/3dcitydb/citydb-tool/releases/tag/v1.3.1
[1.3.0]: https://github.com/3dcitydb/citydb-tool/releases/tag/v1.3.0
[1.2.0]: https://github.com/3dcitydb/citydb-tool/releases/tag/v1.2.0
[1.1.0]: https://github.com/3dcitydb/citydb-tool/releases/tag/v1.1.0
[1.0.0]: https://github.com/3dcitydb/citydb-tool/releases/tag/v1.0.0