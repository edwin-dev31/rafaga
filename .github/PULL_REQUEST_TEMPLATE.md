## Summary

<!-- Briefly describe what this PR changes and why. -->

## Type of Change

- [ ] Feature (`feat`)
- [ ] Bug fix (`fix`)
- [ ] Refactor / chore (`refactor`, `chore`)
- [ ] Documentation (`docs`)
- [ ] Tests / CI (`test`, `ci`)

## Testing

<!-- List the Gradle test commands or verification steps executed. -->

- [ ] `./gradlew test` (or targeted module tests) passed

## Checklist

- [ ] Follows hexagonal / Spring Modulith module boundaries
- [ ] Multi-tenant isolation preserved (all queries/actions scoped by `tenantId`)
- [ ] No secrets, tokens, or credentials committed or logged
- [ ] Database migrations added/verified (if schema changed)
- [ ] Documentation updated (if behavior or API changed)
