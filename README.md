# 🎮 Discord Ready Check Bot

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![JDA](https://img.shields.io/badge/JDA-5.1.2-blue.svg)](https://github.com/DV8FromTheWorld/JDA)
[![Maven](https://img.shields.io/badge/Maven-3.9+-red.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

> **A sophisticated Discord bot for coordinating group activities with intelligent ready-checking system**

Built for gaming communities, teams, and any group that needs to coordinate activities efficiently. Features smart time parsing, persistent configurations, and an intuitive user experience.

---

## 🚀 **Key Features**

### ⚡ **Smart Time Management**
- **Intelligent AM/PM Detection**: Automatically determines whether "7" means 7 AM or 7 PM based on current time
- **Multiple Time Formats**: Supports `5`, `5:30`, `3:45pm`, `17:30`, natural language parsing
- **Countdown Timers**: Real-time countdown displays with automatic updates
- **Timezone Awareness**: Uses Discord's timestamp formatting for cross-timezone compatibility

### 💾 **Persistent Configuration System**
- **Auto-Save**: Automatically saves ready check configurations for quick reuse
- **Smart Deduplication**: Prevents duplicate saved configurations
- **Priority Ordering**: Most recently used configurations appear first
- **Mixed Target Support**: Handle roles and specific users in the same ready check

### 🎯 **Flexible User Interaction**
- **Slash Commands**: Modern Discord UI with `/ready` and `/r` commands
- **Message Commands**: Quick `r`, `r in 5`, `r at 7pm` text shortcuts
- **Interactive Buttons**: Toggle ready status, schedule times, pass on activity
- **Modal Forms**: Detailed time input with validation and examples

### 🔄 **Advanced State Management**
- **Real-time Updates**: Embed updates every minute with live countdowns
- **Auto-expiration**: Scheduled users automatically removed when time expires
- **State Persistence**: Ready checks survive bot restarts with JSON file storage
- **Conflict Resolution**: Detects and reuses existing ready checks for same groups

---

## 🛠 **Technical Implementation**

### **Core Technologies**
- **Java 21**: Latest LTS with modern language features and performance improvements
- **JDA 5.1.2**: Java Discord API for robust Discord integration
- **Maven**: Dependency management and build automation
- **Gson**: JSON serialization for configuration persistence
- **SLF4J + Logback**: Comprehensive logging framework

### **Architecture Highlights**

#### **🏗️ Modular Design Pattern**
```
├── managers/          # Business logic layer
├── listeners/         # Event handling layer  
├── commands/          # Command processing layer
├── botConfig/         # Configuration management
└── ReadyCheckBot.java # Application entry point
```

#### **🧵 Concurrent Programming**
- **Thread-Safe Collections**: `ConcurrentHashMap` for safe multi-user access
- **Scheduled Executors**: Background tasks for countdowns and auto-expiration
- **Non-blocking Operations**: Asynchronous Discord API calls with proper error handling

#### **📊 Data Structures & Algorithms**
- **Hash-based Lookups**: O(1) ready check retrieval and user state management
- **Set Operations**: Efficient user group comparisons and deduplication
- **Stream Processing**: Functional programming for data transformations

---

## 🎮 **User Experience Features**

### **Intuitive Commands**
```bash
/ready targets:@GameRole @Alice @Bob people:true    # Full featured command
/r                                                  # Quick saved config
r in 15                                            # Ready in 15 minutes
r at 7:30pm                                        # Ready at specific time
```

### **Interactive Elements**
- ✅ **Toggle Ready** - Instant ready status changes
- ⏰ **Ready At...** - Schedule specific ready times
- ⏳ **Ready Until...** - Set expiration times
- 🚫 **Pass** - Opt out of current activity
- 💾 **Save Config** - Store for future reuse

### **Smart Notifications**
- **@Mention Control**: Configurable user mentions
- **Auto-cleanup**: Temporary messages self-delete
- **Status Tracking**: Visual indicators for all user states
- **Completion Alerts**: Automatic notifications when everyone is ready

---

## 🏆 **Technical Skills Demonstrated**

### **Software Engineering**
- **Design Patterns**: Command pattern, Observer pattern, Factory pattern
- **SOLID Principles**: Single responsibility, dependency injection, interface segregation
- **Error Handling**: Comprehensive exception management and graceful degradation
- **Code Organization**: Clean separation of concerns and maintainable structure

### **Concurrent Programming**
- **Thread Safety**: Proper synchronization and atomic operations
- **Asynchronous Programming**: Non-blocking I/O and event-driven architecture
- **Resource Management**: Proper cleanup and memory management

### **Data Management**
- **Serialization**: JSON persistence with custom data structures
- **State Management**: Complex state transitions and consistency
- **Performance Optimization**: Efficient algorithms and data structures

### **API Integration**
- **REST APIs**: Discord API interaction patterns
- **Event Handling**: Reactive programming with Discord events
- **Rate Limiting**: Proper API usage and error recovery

---

## 📦 **Installation & Setup**

### **Prerequisites**
- Java 21 or higher
- Maven 3.9+
- Discord Bot Token

### **Quick Start**
```bash
# Clone the repository
git clone https://github.com/stoberdl/Ready-Check-Bot.git
cd Ready-Check-Bot

# Set environment variable
export DISCORD_BOT_TOKEN="your_bot_token_here"

# Build and run
mvn clean package
java -jar target/ready-check-bot-1.0-SNAPSHOT-jar-with-dependencies.jar
```

### **Configuration**
The bot automatically creates `saved_ready_checks.json` for persistent storage. No additional configuration required!

---

## 🎯 **Use Cases**

- **Gaming Communities**: Coordinate raids, matches, and group activities
- **Development Teams**: Schedule meetings and code review sessions  
- **Study Groups**: Organize study sessions and project work
- **Social Events**: Plan activities and gatherings
- **Any Group Activity**: Flexible enough for any coordination need

---

## 🔮 **Future Enhancements**

- **Database Integration**: PostgreSQL for enterprise-scale persistence
- **Analytics**: Usage statistics and group activity insights
- **Role-based Permissions**: Advanced access control

---

## 📈 **Project Metrics**

- **Lines of Code**: ~2,000+ (well-documented and tested)
- **Files**: 13 Java classes with clear separation of concerns
- **Features**: 15+ distinct user-facing features
- **Time Investment**: 40+ hours of development and testing

---

## 🤝 **Contributing**

This project demonstrates enterprise-level Java development practices and is designed to showcase technical proficiency for potential employers. The codebase emphasizes:

- **Clean Code**: Readable, maintainable, and well-documented
- **Best Practices**: Industry-standard patterns and principles
- **Scalability**: Designed for growth and feature expansion
- **Testing**: Comprehensive error handling and edge case management

---

## 📞 **Contact**

Created as a portfolio piece to demonstrate full-stack development capabilities, system design skills, and proficiency with modern Java ecosystem.

**David Stober** - [davidlstober@gmail.com](mailto:davidlstober@gmail.com) - [LinkedIn](https://www.linkedin.com/in/david-stober-640b08160/) - [GitHub](https://github.com/stoberdl)

---

*Built with ❤️ to showcase technical excellence and problem-solving abilities*
