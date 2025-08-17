# Changelog

## [Unreleased]

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

[Unreleased]: https://github.com/3dcitydb/citydb-tool/compare/v1.0.0..HEAD
[1.0.0]: https://github.com/3dcitydb/citydb-tool/releases/tag/v1.0.0