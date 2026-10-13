# Digital-Product-Expiry-Tracker
The Digital Product Expiry Tracker is a web-based full stack application that allows users to store and manage product expiry dates and receive alerts when products are about to expire.


## CI Pipeline Validation

The Digital Product Expiry Tracker uses GitHub Actions
to validate backend changes before integration.

The CI pipeline performs four activities:

1. Build the Spring Boot backend using Maven.
2. Execute automated JUnit tests.
3. Generate JaCoCo code coverage reports.
4. Publish test and coverage reports as artifacts.

Pull requests targeting the main branch are automatically
validated through the configured GitHub Actions workflow.
