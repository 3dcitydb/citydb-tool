# Changelog

## [Unreleased]

### Added
- Added the `info` command to create a report and summary of the 3DCityDB contents. The report can be generated in JSON
  format and either written to a file or printed to `stdout` for further processing.

### Changed
- Updated and harmonized CLI options and their descriptions.
- Texture coordinates are now processed and represented as `float` values instead of `double`.

### Fixed
- Fixed support for time-based validity options in the JSON configuration.
- Fixed `count` methods in `SqlHelper` to produce correct SQL statements.
- Ensured operands are properly cast to `geometry` in `SpatialOperatorHelper` to prevent SQL exceptions.
- Resolved `MVStoreException: Chunk not found` errors when resolving references during imports.

## [1.0.0] - 2025-03-17

This is the initial release of citydb-tool.

[Unreleased]: https://github.com/3dcitydb/citydb-tool/compare/v1.0.0..HEAD
[1.0.0]: https://github.com/3dcitydb/citydb-tool/releases/tag/v1.0.0