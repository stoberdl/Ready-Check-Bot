# Discord Ready Check Bot

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![JDA](https://img.shields.io/badge/JDA-5.1.2-blue.svg)](https://github.com/DV8FromTheWorld/JDA)
[![Maven](https://img.shields.io/badge/Maven-3.9+-red.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=stoberdl_Ready-Check-Bot&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=stoberdl_Ready-Check-Bot)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=stoberdl_Ready-Check-Bot&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=stoberdl_Ready-Check-Bot)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=stoberdl_Ready-Check-Bot&metric=bugs)](https://sonarcloud.io/summary/new_code?id=stoberdl_Ready-Check-Bot)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=stoberdl_Ready-Check-Bot&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=stoberdl_Ready-Check-Bot)
[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=stoberdl_Ready-Check-Bot&metric=sqale_index)](https://sonarcloud.io/summary/new_code?id=stoberdl_Ready-Check-Bot)

A production-ready Discord bot for coordinating group activities with intelligent ready checking, persistent storage, and advanced scheduling capabilities.

## Core Functionality

**Ready Check Management**
- Create ready checks for Discord roles or specific users
- Real-time status tracking with interactive buttons
- Smart duplicate detection and reuse of existing checks
- Persistent storage with automatic recovery after restarts

**Flexible Time Scheduling**
- Natural language time parsing: `r in 15`, `r at 7pm`, `r at 530`
- Smart AM/PM detection based on current time context
- Support for multiple formats: `5`, `5:30`, `17:30`, `5:30pm`
- Discord timestamp integration for timezone compatibility

**Voice Channel Integration**
- Automatic ready status for users in voice channels
- Smart mention filtering (only mentions users not in voice)
- Voice state monitoring for enhanced user experience

**Configuration Persistence**
- Save ready check configurations for quick reuse
- Supabase database integration for reliable storage
- LRU ordering with most recent configurations prioritized
- Cross-session continuity with state recovery

## Technical Architecture

**Core Technologies**
- Java 21 with modern language features
- JDA 5.1.2 for Discord API integration
- Maven for dependency management and builds
- Supabase for persistent data storage
- Docker with multi-stage builds for deployment

**Key Design Patterns**
- Event-driven architecture with reactive programming
- Command pattern for slash command handling
- Concurrent programming with thread-safe collections
- Scheduled task management for countdowns and reminders

**Data Management**
- JSON serialization with Gson
- Atomic database operations with OkHttp
- In-memory caching with ConcurrentHashMap
- Automatic cleanup of expired data

## Commands & Usage

**Slash Commands**
```
/ready targets:@GameRole @Alice @Bob people:true    # Full featured ready check
/r                                                  # Quick access to saved configs
/info                                              # Bot information
```

**Natural Language Shortcuts**
```
r                      # Use most recent saved configuration
r in 15               # Ready check with 15-minute countdown
r at 7:30pm           # Ready check scheduled for specific time
r at 530              # Smart time detection (5:30pm or 5:30am)
```

**Interactive Features**
- **Toggle Ready** - Instant status changes with one click
- **Ready At...** - Schedule specific times with modal input
- **Pass** - Opt out of the ready check gracefully
- **Save Configuration** - Store setup for future reuse

## Installation & Deployment

**Prerequisites**
- Java 21 or higher
- Maven 3.9+
- Discord Bot Token
- Supabase project with database tables

**Environment Variables**
```bash
DISCORD_BOT_TOKEN=your_discord_bot_token
SUPABASE_URL=your_supabase_project_url
SUPABASE_KEY=your_supabase_anon_key
```

**Docker Deployment**
```bash
# Configure environment
echo "DISCORD_BOT_TOKEN=your_token" > .env
echo "SUPABASE_URL=your_url" >> .env
echo "SUPABASE_KEY=your_key" >> .env

# Deploy with Docker Compose
docker-compose up -d

# View logs
docker-compose logs -f
```

**Manual Build**
```bash
# Clone and build
git clone https://github.com/stoberdl/Ready-Check-Bot.git
cd Ready-Check-Bot
mvn clean package

# Run
java -jar target/ready-check-bot-1.0-SNAPSHOT-jar-with-dependencies.jar
```

## Database Schema

The bot requires two Supabase tables:

**saved_configs** - Stores reusable ready check configurations
**ready_checks** - Manages active ready check state for recovery

Database setup scripts and migration details available in project documentation.

## Advanced Features

**Fault Tolerance**
- Automatic message recovery from Discord history
- State reconstruction after bot restarts
- Graceful handling of missing users or roles
- Comprehensive error logging and recovery

**Performance Optimizations**
- Lazy loading of Discord entities
- Efficient batch operations for database updates
- Connection pooling for external API calls
- Memory-efficient concurrent data structures

**User Experience**
- Cross-timezone timestamp display
- Progressive enhancement from simple to advanced features
- Accessibility considerations with clear visual indicators
- Mobile-friendly interface design

## Code Quality

This project demonstrates enterprise-level Java development practices including modern Java 21 features, comprehensive error handling, thread-safe concurrent programming, and clean architecture principles. Integrated with SonarCloud for continuous quality monitoring.

**Architecture Highlights**
- Layered design with clear separation of concerns
- Event-driven reactive programming model
- Comprehensive logging and monitoring
- Docker containerization for consistent deployment

---

**David Stober** - [LinkedIn](https://www.linkedin.com/in/david-stober-640b08160/) - [GitHub](https://github.com/stoberdl)
