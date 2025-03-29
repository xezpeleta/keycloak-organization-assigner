# Stage 1: Define the build environment
# Use an official Maven image with a suitable JDK (11 or 17 for Keycloak 26.x)
# Using Eclipse Temurin JDK 11 here as it's common for SPI compilation
FROM maven:3.9-eclipse-temurin-17 AS builder

# Set the working directory inside the container
WORKDIR /usr/src/app

# Clean potential old cache within the image layer just in case
# This ensures dependencies are fetched fresh relative to pom.xml
RUN rm -rf /root/.m2/repository/*

# Copy the pom.xml first to leverage Docker cache layers for dependencies
COPY pom.xml .

# Download Maven dependencies. This layer is cached if pom.xml doesn't change.
# -B runs in non-interactive (batch) mode
# Using dependency:go-offline is often preferred as it downloads all dependencies
RUN mvn org.apache.maven.plugins:maven-dependency-plugin:go-offline -B

# Copy the entire project source code into the container
COPY src ./src

# Run the Maven package command to compile the code and create the JAR
# -DskipTests is common in build containers unless you need tests to run here
RUN mvn package -B -DskipTests -X -Dmaven.compiler.verbose=true


# --- The build artifact (JAR file) is now available in /usr/src/app/target/ ---
# This Dockerfile is primarily intended for building. If you wanted to run
# something by default, you could add an ENTRYPOINT or CMD, but for just
# building and extracting the artifact, this is sufficient.
