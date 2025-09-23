# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Java 21 Discord bot for managing ready checks in gaming communities. The bot uses Maven for builds, JDA 5.1.2 for Discord API integration, and Supabase for persistent storage.

## Development Commands

### Build and Package
```bash
mvn clean compile          # Compile source code
mvn clean package          # Build JAR with dependencies
```

### Testing
```bash
mvn test                   # Run unit tests
mvn surefire-report:report # Generate test reports
```

### Code Quality
```bash
mvn sonar:sonar            # Run SonarCloud analysis (requires auth)
```

### Running the Bot
```bash
# Build and run locally
mvn clean package
java -jar target/ready-check-bot-1.0-SNAPSHOT-jar-with-dependencies.jar

# With Docker Compose
docker-compose up -d
```

## Architecture

### Core Components

**Main Entry Point**: `com.projects.ReadyCheckBot`
- Initializes JDA with all gateway intents and member caching
- Registers slash commands (`/ready`, `/r`, `/info`)
- Sets up event listeners for commands, buttons, modals, and messages

**Ready Check Management**: `com.projects.readycheck.ReadyCheckManager`
- Thread-safe ready check lifecycle management using ConcurrentHashMap
- Scheduled tasks for countdown updates and automatic recovery
- Voice channel integration for automatic ready status
- Persistent storage integration with Supabase

**Command System**: `com.projects.commands.*`
- `ReadyCommand`: Main `/ready` command with role/user targeting
- `RCommand`: Quick access to saved configurations
- `InfoCommand`: Bot information display

**Event Listeners**: `com.projects.listeners.*`
- Button interactions for ready status toggles
- Modal interactions for custom time scheduling
- Selection menu handling for saved configurations
- Message processing for natural language commands

### Key Design Patterns

**Event-Driven Architecture**: Uses JDA's event system with dedicated listeners for different interaction types

**Concurrent Programming**: Thread-safe collections (ConcurrentHashMap) for managing active ready checks and user preferences

**Scheduled Task Management**: Periodic updates for countdown timers and automatic state recovery after bot restarts

**Database Integration**: Atomic operations via OkHttp for Supabase REST API calls with JSON serialization using Gson

## Environment Configuration

Required environment variables:
```bash
DISCORD_BOT_TOKEN=your_discord_bot_token
SUPABASE_URL=your_supabase_project_url
SUPABASE_KEY=your_supabase_anon_key
```

## Database Schema

The bot requires two Supabase tables:
- `saved_configs`: Stores reusable ready check configurations with LRU ordering
- `ready_checks`: Manages active ready check state for bot restart recovery

## Key Features Implementation

**Natural Language Time Parsing**: Smart detection of time formats (`in 15`, `at 7pm`, `at 530`) with AM/PM context awareness

**Voice Channel Integration**: Automatic ready status for users in voice channels, with filtered mentions to avoid spam

**Persistent State Recovery**: Automatic recovery of active ready checks from database after bot restarts using Discord message history

**Interactive Components**: Button-based status toggles, modal time input, and dropdown menus for saved configurations

## Development Notes

- Uses Java 21 features including modern language constructs
- Thread-safe design throughout with proper concurrent programming patterns
- Comprehensive logging with SLF4J and Logback
- Docker multi-stage builds for production deployment
- SonarCloud integration for continuous code quality monitoring

##  Standard Workflow
1. First think through the problem, read the codebase for relevant files, and write a plan to todo.md.
2. The plan should have a list of todo items that you can check off as you complete them
3. Before you begin working, check in with me and I will verify the plan.
4. Then, begin working on the todo items, marking them as complete as you go.
5. Please every step of the way just give me a high level explanation of what changes you made
6. Make every task and code change you do as simple as possible. We want to avoid making any massive or complex changes. Every change should impact as little code as possible. Everything is about simplicity.
7. Finally, add a review section to the todo.md file with a summary of the changes you made and any other relevant information.