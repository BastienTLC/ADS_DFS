# Stage 1: Build the application with Maven
FROM maven:3.8.7-eclipse-temurin-17 AS build

# Install protobuf compiler
RUN apt-get update && apt-get install -y protobuf-compiler

# Set the working directory inside the container
WORKDIR /app

# Copy the pom.xml and the module's POM files first to leverage Docker cache
COPY pom.xml .
COPY proto/pom.xml ./proto/
COPY client/pom.xml ./client/

# Download dependencies without building the entire project
RUN mvn dependency:go-offline -B

# Copy the entire project
COPY . .

# Compile and package the application as a JAR file
RUN mvn clean install -DskipTests

# Stage 2: Create the final image to run the application
FROM amazoncorretto:17-alpine-jdk

# Set the working directory inside the container
WORKDIR /app

# Copy the built JAR file from the previous stage
COPY --from=build /app/client/target/*.jar /app/client.jar

# Expose port 8080 for the client service
EXPOSE 8080

# Specify the entry point to run the application
ENTRYPOINT ["java", "-jar", "/app/client.jar"]

