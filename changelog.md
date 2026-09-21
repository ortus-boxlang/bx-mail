# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

* * *

## [Unreleased]

## [1.4.14] - 2026-09-21

### Added

- [BLMODULES-295](https://ortussolutions.atlassian.net/browse/BLMODULES-295) - Add MIME body part serialization so spooled multipart messages survive Java object serialization

### Fixed

- [BLMODULES-297](https://ortussolutions.atlassian.net/browse/BLMODULES-297) - Ensure `mailparam` attachments are deleted when `remove=true`
- [BLMODULES-299](https://ortussolutions.atlassian.net/browse/BLMODULES-299) - Add Content-ID support for inline images and encrypted emails

### Changed

- Bump `org.bouncycastle:bcjmail-jdk18on` from `1.85` to `1.86`

## [1.4.13] - 2026-09-08

### Fixed

- Fix struct duplication when building mail messages
- Prevent potential modification by reference in trace mode
- Add NPE protection and additional debug logging

### Changed

- Replace `logger.at[level]` calls with direct `logger.[level]` calls

## [1.4.12] - 2026-09-02

### Changed

- [BLMODULES-286](https://ortussolutions.atlassian.net/browse/BLMODULES-286) - Change default mimetype to `text/plain` from `text/html`

## [1.4.11] - 2026-08-20

### Added

- Add configurable spool start delay via the `spoolStartDelayMinutes` setting
- [BL-2559](https://ortussolutions.atlassian.net/browse/BL-2559) - Add support for multiple delimiters in `to`, `cc`, `bcc`, and `replyTo` address lists

### Fixed

- [BLMODULES-278](https://ortussolutions.atlassian.net/browse/BLMODULES-278) - Mark empty or unreadable spool cache entries as bounced and log them

## [1.4.10] - 2026-07-27

### Fixed

- [BL-2599](https://ortussolutions.atlassian.net/browse/BL-2599) - Normalize email addresses with parenthetical display names and internationalized (IDN) addresses

### Changed

- Bump `org.bouncycastle:bcjmail-jdk18on` from `1.84` to `1.85`
- Update Gradle wrapper and GitHub Actions/dependency versions

## [1.4.9] - 2026-04-24

### Fixed

- Fix class cast exceptions when spooling mail and add spool scheduler tests

## [1.4.8] - 2026-04-21

### Added

- Add support for semicolon delimiters in address lists

### Fixed

- [BLMODULES-182](https://ortussolutions.atlassian.net/browse/BLMODULES-182) - Fix header and file attachment detection for `mailparam`
- [BLMODULES-181](https://ortussolutions.atlassian.net/browse/BLMODULES-181) - Fix serialization for multipart messages
- [BLMODULES-162](https://ortussolutions.atlassian.net/browse/BLMODULES-162) - Ensure TLS is forced when the flag is `true`
- Ensure attributes are updated when the mime map contains the type

### Changed

- Bump `org.bouncycastle:bcjmail-jdk18on` from `1.83` to `1.84`

## [1.4.7] - 2025-12-11

### Fixed

- Fix an issue where fallback simple emails did not have the correct content

### Changed

- Bump `org.apache.commons:commons-text` from `1.14.0` to `1.15.0`

## [1.4.6] - 2025-12-08

### Fixed

- [BLMODULES-108](https://ortussolutions.atlassian.net/browse/BLMODULES-108) - Fix fallback server already having an initialized session

## [1.4.5] - 2025-12-02

### Fixed

- [BL-1943](https://ortussolutions.atlassian.net/browse/BL-1943) - Add mail server failover when the primary server fails
- Revert expression interpretation to the `spoolOrSend` method

### Changed

- Bump `org.bouncycastle:bcjmail-jdk18on` from `1.82` to `1.83` and other dependency updates

## [1.4.4] - 2025-11-19

### Fixed

- [BL-1855](https://ortussolutions.atlassian.net/browse/BL-1855) - Updated behavior of `mail` and `mailpart` components to not require explicit output and auto-evaluate body expressions

## [1.4.3] - 2025-10-23

### Changed

- [BL-1833](https://ortussolutions.atlassian.net/browse/BL-1833) - Change `useSSL` and `useTLS` attributes to boolean and `wrapText` to integer, and improve boolean casting

## [1.4.1] - 2025-04-24

### Fixed

- [BL-1278](https://ortussolutions.atlassian.net/browse/BL-1278) - Fix issues with correct assignment of `cc`, `bcc`, and `replyTo` fields

## [1.4.0] - 2025-03-24

### Changed

- [BL-1092](https://ortussolutions.atlassian.net/browse/BL-1092) - Migrate to Jakarta Mail and namespaces

### Fixed

- Fix boolean casting on CFConfig-sourced SSL and TLS arguments

## [1.3.0] - 2025-02-21

### Fixed

- [BL-1086](https://ortussolutions.atlassian.net/browse/BL-1086) - Fix sourcing of mail settings from CFConfig-generated `boxlang.json` files

## [1.2.0] - 2025-02-20

### Fixed

- Fix shadow jar missing the correct classifier by no longer minimizing, due to heavy runtime class loading

## [1.1.0] - 2025-02-18

### Added

- Added encryption and signature support

### Fixed

- [BL-415](https://ortussolutions.atlassian.net/browse/BL-415) - Fix casting of spool configuration settings
- Re-add parsing for root-level config mail servers

## [1.0.0] - 2024-06-13

- First iteration of this module

[unreleased]: https://github.com/ortus-boxlang/bx-mail/compare/v1.4.14...HEAD
[1.4.14]: https://github.com/ortus-boxlang/bx-mail/compare/v1.4.13...v1.4.14
[1.4.13]: https://github.com/ortus-boxlang/bx-mail/compare/v1.4.12...v1.4.13
[1.4.12]: https://github.com/ortus-boxlang/bx-mail/compare/v1.4.11...v1.4.12
[1.4.11]: https://github.com/ortus-boxlang/bx-mail/compare/v1.4.10...v1.4.11
[1.4.10]: https://github.com/ortus-boxlang/bx-mail/compare/v1.4.9...v1.4.10
[1.4.9]: https://github.com/ortus-boxlang/bx-mail/compare/v1.4.8...v1.4.9
[1.4.8]: https://github.com/ortus-boxlang/bx-mail/compare/v1.4.7...v1.4.8
[1.4.7]: https://github.com/ortus-boxlang/bx-mail/compare/v1.4.6...v1.4.7
[1.4.6]: https://github.com/ortus-boxlang/bx-mail/compare/v1.4.5...v1.4.6
[1.4.5]: https://github.com/ortus-boxlang/bx-mail/compare/v1.4.4...v1.4.5
[1.4.4]: https://github.com/ortus-boxlang/bx-mail/compare/v1.4.3...v1.4.4
[1.4.3]: https://github.com/ortus-boxlang/bx-mail/compare/v1.4.1...v1.4.3
[1.4.1]: https://github.com/ortus-boxlang/bx-mail/compare/v1.4.0...v1.4.1
[1.4.0]: https://github.com/ortus-boxlang/bx-mail/compare/v1.3.0...v1.4.0
[1.3.0]: https://github.com/ortus-boxlang/bx-mail/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/ortus-boxlang/bx-mail/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/ortus-boxlang/bx-mail/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/ortus-boxlang/bx-mail/compare/v1.0.0...v1.0.0
