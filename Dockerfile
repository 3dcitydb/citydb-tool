# citydb-tool Dockerfile ######################################################
#   Official website    https://www.3dcitydb.org
#   GitHub              https://github.com/3dcitydb/citydb-tool
###############################################################################

# Fetch & build stage #########################################################
# ARGS
ARG BUILDER_IMAGE_TAG='25-jdk-noble'
ARG RUNTIME_IMAGE_TAG='25-jre-noble'

# Base image
FROM eclipse-temurin:${BUILDER_IMAGE_TAG} AS builder

# Copy source code
WORKDIR /build
COPY . /build

# Build
RUN set -x && \
    chmod u+x ./gradlew && ./gradlew installDockerDist

# Runtime stage ###############################################################
# Base image
FROM eclipse-temurin:${RUNTIME_IMAGE_TAG} AS runtime

# Version info
ARG CITYDB_TOOL_VERSION
ENV CITYDB_TOOL_VERSION=${CITYDB_TOOL_VERSION}

# Copy from builder
COPY --from=builder /build/citydb-cli/build/install/citydb-tool-docker /opt/citydb-tool

# Put start script in path
RUN set -x && \
    ln -sf /opt/citydb-tool/citydb /usr/local/bin

USER 1000
WORKDIR /data

ENTRYPOINT ["citydb"]
CMD ["--help"]
