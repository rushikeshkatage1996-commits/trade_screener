# Use a supported OpenJDK 21 or later image as the base image for Java applications
FROM openjdk:21-jdk-slim as build

# Set the working directory inside the container
WORKDIR /app

# Copy the Maven wrapper and pom.xml to download dependencies
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies (this step is cached if pom.xml doesn't change)
RUN ./mvnw dependency:go-offline

# Copy the entire project
COPY src ./src

# Build the application
RUN ./mvnw package -DskipTests

# Use a smaller base image for the final stage
FROM openjdk:21-jre-slim

# Set the working directory
WORKDIR /app

# Copy the built JAR file from the build stage
COPY --from=build /app/target/*.jar app.jar

# Expose the port the application runs on
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
