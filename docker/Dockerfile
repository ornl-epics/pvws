# === Stage 1: Clone the source code ===
FROM alpine/git:latest as source_fetch

RUN git clone --branch fnal https://github.com/fermi-ad/pvws.git /pvws

# === Stage 2: Build the project with Maven ===
FROM maven:3.8.7-eclipse-temurin-17 as maven_builder

ARG GITHUB_USERNAME
ARG GITHUB_TOKEN

# Create Maven settings with GitHub credentials
RUN mkdir -p /root/.m2
COPY settings.xml /root/.m2/settings.xml

# Replace placeholders *after* copying the file
RUN sed -i "s|GITHUB_USERNAME|${GITHUB_USERNAME}|g" /root/.m2/settings.xml && \
    sed -i "s|GITHUB_TOKEN|${GITHUB_TOKEN}|g" /root/.m2/settings.xml

# Copy source from previous stage
COPY --from=source_fetch /pvws /pvws

# Build with Maven
WORKDIR /pvws
RUN mvn clean package -DskipTests

# === Stage 3: Package app with Tomcat ===
FROM tomcat:9.0-jdk17

ARG PORT_NUMBER=8080

# Copy WAR file
COPY --from=maven_builder /pvws/target/pvws.war ${CATALINA_HOME}/webapps/pvws.war

# Set port and disable shutdown port
RUN sed -i.bak -e "s|Connector port=\"8080\"|Connector port=\"${PORT_NUMBER}\"|g" \
    -e 's|Server port="8005" shutdown="SHUTDOWN"|Server port="-1" shutdown="SHUTDOWN"|g' \
    ${CATALINA_HOME}/conf/server.xml

# Optional: include any JVM config
COPY ./setenv.sh ${CATALINA_HOME}/bin/