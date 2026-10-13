# Digital-Product-Expiry-Tracker
The Digital Product Expiry Tracker is a web-based full stack application that allows users to store and manage product expiry dates and receive alerts when products are about to expire.


## Continuous Integration

This project uses GitHub Actions to automate backend build verification,
JUnit testing, JaCoCo code coverage analysis, and report generation.

The CI workflow is configured in `.github/workflows/ci.yml`.

The pipeline runs automatically when changes are pushed to the `main`
branch or branches matching `feature/**`. It also validates pull requests
targeting `main`.

The workflow uses Java 17 and Maven. Test results and coverage reports
are published as downloadable GitHub Actions artifacts.
