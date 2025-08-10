# Contributing to Java Music Organizer Pro

First off, thank you for considering contributing to Java Music Organizer Pro! 🎉

## 📋 Table of Contents
- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [How to Contribute](#how-to-contribute)
- [Pull Request Process](#pull-request-process)
- [Style Guidelines](#style-guidelines)
- [Testing Guidelines](#testing-guidelines)
- [Documentation](#documentation)

## 📜 Code of Conduct

This project and everyone participating in it is governed by our Code of Conduct. By participating, you are expected to uphold this code. Please be respectful and inclusive in all interactions.

## 🚀 Getting Started

### Prerequisites
- Java 21 or higher
- Maven 3.8+
- Git
- Your favorite IDE (IntelliJ IDEA recommended)

### Fork and Clone
1. Fork the repository on GitHub
2. Clone your fork locally:
```bash
git clone https://github.com/yourusername/java-music-organizer.git
cd java-music-organizer
```

3. Add the upstream repository:
```bash
git remote add upstream https://github.com/originalowner/java-music-organizer.git
```

## 🛠️ Development Setup

### Building the Project
```bash
mvn clean install
```

### Running Tests
```bash
mvn test
```

### Running the Application
```bash
mvn spring-boot:run
```

### IDE Setup

#### IntelliJ IDEA
1. Import as Maven project
2. Enable annotation processing
3. Set project SDK to Java 21
4. Install recommended plugins:
   - Google Java Format
   - SonarLint
   - Maven Helper

## 🤝 How to Contribute

### Reporting Bugs
- Check if the bug has already been reported
- Open a new issue using the bug report template
- Include detailed steps to reproduce
- Add logs and system information

### Suggesting Features
- Check if the feature has been suggested
- Open a new issue using the feature request template
- Explain the use case and benefits
- Consider implementation approach

### Contributing Code

#### Finding Issues
Look for issues labeled:
- `good first issue` - Great for newcomers
- `help wanted` - We need your help!
- `enhancement` - New features
- `bug` - Something needs fixing

#### Working on Issues
1. Comment on the issue to claim it
2. Create a feature branch:
```bash
git checkout -b feature/your-feature-name
```

3. Make your changes
4. Write/update tests
5. Update documentation
6. Commit your changes (see commit guidelines)

## 📥 Pull Request Process

1. **Update your fork**:
```bash
git fetch upstream
git checkout main
git merge upstream/main
```

2. **Create a feature branch**:
```bash
git checkout -b feature/amazing-feature
```

3. **Make your changes** following our style guidelines

4. **Test thoroughly**:
```bash
mvn clean test
mvn verify
```

5. **Commit with meaningful messages**:
```bash
git commit -m "feat: add amazing feature

- Detailed description of what changed
- Why it was changed
- Any breaking changes"
```

6. **Push to your fork**:
```bash
git push origin feature/amazing-feature
```

7. **Open a Pull Request** using our template

8. **Address review feedback** promptly

## 🎨 Style Guidelines

### Java Code Style
We follow the Google Java Style Guide with these modifications:
- 4 spaces for indentation (not 2)
- 120 character line limit
- Always use braces for control structures

### Code Formatting
```bash
mvn spotless:apply
```

### Commit Messages
We use conventional commits:
- `feat:` New feature
- `fix:` Bug fix
- `docs:` Documentation changes
- `style:` Formatting changes
- `refactor:` Code refactoring
- `perf:` Performance improvements
- `test:` Test changes
- `chore:` Build process or auxiliary tool changes

Example:
```
feat: add duplicate detection threshold configuration

- Add similarity threshold parameter to CLI
- Implement configurable matching algorithm
- Update documentation with new option

Closes #123
```

## 🧪 Testing Guidelines

### Test Coverage
- Aim for 80% code coverage minimum
- All new features must have tests
- Bug fixes should include regression tests

### Test Structure
```java
class MusicScannerTest {
    @Test
    @DisplayName("should scan directory with virtual threads")
    void shouldScanDirectoryWithVirtualThreads() {
        // Given
        Path testDir = createTestDirectory();
        
        // When
        ScanResult result = scanner.scan(testDir);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getFileCount()).isEqualTo(10);
    }
}
```

### Running Specific Tests
```bash
# Run a specific test class
mvn test -Dtest=MusicScannerTest

# Run a specific test method
mvn test -Dtest=MusicScannerTest#shouldScanDirectory
```

## 📚 Documentation

### Code Documentation
- All public APIs must have JavaDoc
- Include examples in JavaDoc where helpful
- Document complex algorithms

### README Updates
- Update README.md for new features
- Keep examples current
- Update performance benchmarks

### Architecture Documentation
- Update architecture diagrams for significant changes
- Document design decisions in ADRs (Architecture Decision Records)

## 🎯 Performance Considerations

When contributing performance-related changes:
1. Include benchmarks (JMH preferred)
2. Test with large datasets (10,000+ files)
3. Profile memory usage
4. Consider Virtual Thread implications

## 🔍 Review Process

### What We Look For
- Code quality and maintainability
- Test coverage and quality
- Performance impact
- Documentation completeness
- Adherence to project standards

### Review Timeline
- Initial review within 48 hours
- Feedback addressed within a week
- Inactive PRs closed after 30 days

## 📬 Communication

- **GitHub Issues**: Bug reports and feature requests
- **Discussions**: General questions and ideas
- **Discord**: Real-time chat and support

## 🏆 Recognition

Contributors will be:
- Listed in CONTRIBUTORS.md
- Mentioned in release notes
- Given credit in commit messages

## ❓ Questions?

Don't hesitate to ask! Open an issue with the `question` label or reach out on Discord.

Thank you for contributing to Java Music Organizer Pro! 🎵