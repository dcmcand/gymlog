# Contributing to GymLog

Thanks for your interest in contributing to GymLog!

## Getting Started

1. Fork the repository
2. Clone your fork locally
3. Create a branch for your change

## Development Setup

### Requirements

- JDK 21 (Temurin recommended)
- Android SDK with API 36
- Android Build Tools

### Building

```bash
./gradlew assembleDebug
```

### Running Tests

```bash
./gradlew test
```

## Making Changes

1. Create a feature branch from `main`
2. Write tests for new functionality
3. Make sure all tests pass before submitting
4. Keep commits focused - one logical change per commit

## Pull Requests

- Give your PR a clear title describing what it does
- Reference any related issues
- Make sure CI passes before requesting review

## Code Style

- Follow standard Kotlin conventions
- Use Jetpack Compose for all UI
- Room entities go in `com.gymlog.app.data`
- UI screens go in `com.gymlog.app.ui/<feature>/`
- Keep composables focused - extract reusable components

## Reporting Bugs

Open an issue with:
- What you expected to happen
- What actually happened
- Steps to reproduce
- Device model and Android version
