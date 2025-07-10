FROM openjdk:21-jdk-slim

WORKDIR /app

# Install Maven
RUN apt-get update && apt-get install -y maven

# Copy project files
COPY pom.xml .
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests

# Create data directory for JSON persistence
RUN mkdir -p /app/data

# Copy the built JAR to a standard location
RUN cp target/ready-check-bot-1.0-SNAPSHOT-jar-with-dependencies.jar app.jar

# Run the bot
CMD ["java", "-jar", "app.jar"]