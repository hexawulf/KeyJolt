# Contributing to KeyJolt

Thank you for your interest in contributing to KeyJolt! This document provides guidelines for contributing to the project.

## Code of Conduct

By participating in this project, you agree to maintain a respectful and inclusive environment for all contributors.

## Getting Started

1. Fork the repository
2. Clone your fork locally
3. Create a new branch for your feature or bug fix
4. Make your changes
5. Test thoroughly
6. Submit a pull request

## Development Setup

### Prerequisites
- Java 17 or higher
- Maven 3.6+
- Git

### Local Development
```bash
# Clone your fork
git clone https://github.com/YOUR_USERNAME/KeyJolt.git
cd KeyJolt

# Build the project
mvn clean install

# Run tests
mvn test

# Start the development server
mvn spring-boot:run
```

## Pull Request Process

1. **Create a feature branch** from `main`
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make your changes** following the coding standards below

3. **Test your changes** thoroughly
   - Run all existing tests: `mvn test`
   - Test the web interface manually
   - Verify security features work correctly

4. **Update documentation** if needed
   - Update README.md for new features
   - Add/update code comments
   - Update configuration examples

5. **Commit your changes** with clear, descriptive messages
   ```bash
   git commit -m "Add feature: description of what you added"
   ```

6. **Push to your fork** and create a pull request
   ```bash
   git push origin feature/your-feature-name
   ```

## Coding Standards

### Java Code Style
- Use 4 spaces for indentation
- Follow standard Java naming conventions
- Add comprehensive JavaDoc comments for public methods
- Keep methods focused and under 50 lines when possible
- Use meaningful variable and method names

### Security Guidelines
- Never log sensitive information (keys, passwords, etc.)
- Validate all user inputs on both client and server side
- Follow secure coding practices for cryptographic operations
- Ensure proper error handling without information leakage

### Frontend Guidelines
- Use semantic HTML elements
- Follow accessibility best practices
- Maintain responsive design principles
- Keep JavaScript modular and well-commented
- Test across different browsers and devices

## Testing

### Unit Tests
- Write unit tests for all new business logic
- Aim for at least 80% code coverage
- Use meaningful test names that describe the scenario
- Mock external dependencies appropriately

### Integration Tests
- Test key generation functionality end-to-end
- Verify file cleanup mechanisms work correctly
- Test rate limiting behavior
- Validate security headers are set correctly

### Manual Testing Checklist
- [ ] Form validation works for all fields
- [ ] Key generation completes successfully
- [ ] Download links work correctly
- [ ] Files are cleaned up after the timeout
- [ ] Rate limiting prevents abuse
- [ ] Error messages are user-friendly
- [ ] UI is responsive on mobile devices
- [ ] Accessibility features work with screen readers

## Reporting Issues

When reporting issues, please include:

1. **Environment details**
   - Operating system
   - Java version
   - Browser (for UI issues)

2. **Steps to reproduce**
   - Clear, numbered steps
   - Expected vs actual behavior
   - Screenshots if applicable

3. **Error messages**
   - Full error messages or stack traces
   - Relevant log entries

## Feature Requests

For new features:

1. Check existing issues to avoid duplicates
2. Describe the use case and benefit
3. Consider security implications
4. Propose implementation approach if possible

## Security Considerations

- Report security vulnerabilities privately to dev@0xwulf.dev
- Do not commit secrets or sensitive data
- Follow OWASP guidelines for web application security
- Consider cryptographic best practices for key-related features

## Documentation

- Update README.md for user-facing changes
- Add inline code comments for complex logic
- Include configuration examples
- Update API documentation if endpoints change

## Release Process

Maintainers handle releases, but contributors should:

- Ensure changes are backward compatible when possible
- Document breaking changes clearly
- Update version numbers in relevant files
- Test against production-like environments

## Questions?

If you have questions about contributing:

- Check existing issues and discussions
- Contact the maintainer at dev@0xwulf.dev
- Create a discussion issue for general questions

Thank you for contributing to KeyJolt!