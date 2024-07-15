# 3DCityDB Importer/Exporter Dockerfile #######################################
#   Official website    https://www.3dcitydb.org
#   GitHub              https://github.com/3dcitydb/citydb-tool
###############################################################################

# Fetch & build stage #########################################################
# ARGS
ARG BUILDER_IMAGE_TAG='17-jdk-jammy'
ARG RUNTIME_IMAGE_TAG='17-jdk-jammy'

# Base image
FROM eclipse-temurin:${BUILDER_IMAGE_TAG} AS builder

# Copy source code
WORKDIR /build
COPY . /build

# Build
RUN chmod u+x ./gradlew && ./gradlew installDist

# Runtime stage ###############################################################
# Base image
FROM eclipse-temurin:${RUNTIME_IMAGE_TAG} AS runtime

# Version info
ARG CITYDB_TOOL_VERSION
ENV CITYDB_TOOL_VERSION=${CITYDB_TOOL_VERSION}

# Copy from builder
COPY --from=builder /build/citydb-cli/build/install/citydb-tool /opt/citydb-tool

# Run as non-root user, put start script in path and set permissions
RUN groupadd --gid 1000 -r citydb-tool && \
    useradd --uid 1000 --gid 1000 -d /data -m -r --no-log-init citydb-tool && \
    ln -sf /opt/citydb-tool/citydb /usr/local/bin

WORKDIR /data
USER 1000

ENTRYPOINT ["citydb"]
CMD ["--help"]
