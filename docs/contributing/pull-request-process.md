---
title: Pull Request Process
---

# Pull Request Process

This guide explains the pull request process for contributing to TrustWeave.

## Overview

The pull request process ensures code quality and consistency:

1. **Create Feature Branch** – create a branch for your changes
2. **Make Changes** – implement your changes
3. **Test Changes** – ensure all tests pass
4. **Submit Pull Request** – open a PR for review
5. **Address Feedback** – respond to review comments
6. **Merge** – PR is merged after approval

## Creating a Pull Request

### Before Submitting

Before submitting a pull request:

- [ ] All tests pass (`./gradlew test`)
- [ ] Code is formatted (`./gradlew ktlintCheck`)
- [ ] Build succeeds (`./gradlew build`)
- [ ] Documentation updated (if applicable)
- [ ] Commit messages follow conventions
- [ ] Branch is up to date with main

### Pull Request Title

Use descriptive titles:

```
✅ Good: "Add support for did:web DID method"
✅ Good: "Fix DID resolution error handling"
❌ Bad: "Updates"
❌ Bad: "WIP"
```

### Pull Request Description

Include:

- **Purpose** – what problem does this solve?
- **Changes** – what was changed?
- **Testing** – how was it tested?
- **Related Issues** – link to related issues

Example:

```markdown
## Purpose
Adds support for the did:web DID method.

## Changes
- Implemented WebDidMethod class
- Added WebDidMethodProvider for SPI discovery
- Added tests for did:web operations

## Testing
- All existing tests pass
- Added unit tests for WebDidMethod
- Added integration tests for did:web resolution

## Related Issues
Fixes #123
```

## Code Review Process

### Review Checklist

Reviewers check:

- [ ] Code follows style guidelines
- [ ] Tests are included and pass
- [ ] Documentation is updated
- [ ] Error handling is appropriate
- [ ] Performance considerations addressed
- [ ] No security issues

### Responding to Feedback

When receiving feedback:

1. **Read carefully** – understand the feedback
2. **Ask questions** – if something is unclear
3. **Make changes** – address the feedback
4. **Update PR** – push changes and comment

### Requesting Changes

If changes are requested:

1. **Review feedback** – understand required changes
2. **Make changes** – implement requested changes
3. **Test changes** – ensure tests still pass
4. **Update PR** – push changes and request re-review

## Commit Messages

### Commit Message Format

Follow conventional commits:

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Types

- `feat` – new feature
- `fix` – bug fix
- `docs` – documentation changes
- `style` – code style changes
- `refactor` – code refactoring
- `test` – test changes
- `chore` – build/tool changes

### Examples

```
feat(did): add did:web DID method support

Implemented WebDidMethod class with full W3C spec compliance.
Added SPI provider for auto-discovery.

Fixes #123
```

```
fix(anchor): fix transaction confirmation wait logic

Previously, transaction confirmation wait would timeout
immediately. Now properly waits for confirmation.

Fixes #456
```

## Merging Process

### Merge Requirements

Before merging:

- [ ] At least one approval
- [ ] All CI checks pass
- [ ] No merge conflicts
- [ ] Branch is up to date

### Merge Methods

- **Squash and Merge** – preferred for feature branches
- **Rebase and Merge** – for clean history
- **Merge Commit** – for complex branches

## Continuous Integration

### CI Checks

PRs automatically run:

- **Build** – ensures code compiles
- **Tests** – runs all tests
- **Linting** – checks code style
- **Coverage** – measures test coverage

### Fixing CI Failures

If CI fails:

1. **Review logs** – understand the failure
2. **Fix locally** – make necessary changes
3. **Test locally** – ensure fixes work
4. **Push changes** – CI will re-run

## Best Practices

### Small, Focused PRs

Keep PRs focused:

- **Single feature** – one feature per PR
- **Reasonable size** – easy to review
- **Clear purpose** – obvious what changed

### Communication

Communicate clearly:

- **Description** – explain what and why
- **Comments** – explain complex logic
- **Responses** – respond to feedback promptly

### Keep PRs Updated

Keep PRs current:

- **Rebase regularly** – keep up with main
- **Respond to feedback** – address comments quickly
- **Update status** – mark as ready when done

## After Merge

### Post-Merge Tasks

After merge:

- **Close related issues** – link PR to issues
- **Update documentation** – if needed
- **Announce changes** – if significant

## Next Steps

- Review [Development Setup](development-setup.md) for environment setup
- See [Code Style](code-style.md) for coding conventions
- Check [Testing Guidelines](testing-guidelines.md) for testing practices
- Explore [Creating Plugins](creating-plugins.md) for plugin development

## References

- [Conventional Commits](https://www.conventionalcommits.org/)
- [Git Flow](https://www.atlassian.com/git/tutorials/comparing-workflows/gitflow-workflow)
- [Code Review Best Practices](https://github.com/google/eng-practices/blob/master/review/)


