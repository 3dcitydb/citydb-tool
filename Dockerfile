# 3DCityDB Importer/Exporter Dockerfile #######################################
#   Official website    https://www.3dcitydb.org
#   GitHub              https://github.com/3dcitydb/citydb-tool
###############################################################################

# Fetch & build stage #########################################################
# ARGS
ARG BUILDER_IMAGE_TAG='21-jdk-noble'
ARG RUNTIME_IMAGE_TAG='21-jre-noble'

# Base image
FROM eclipse-temurin:${BUILDER_IMAGE_TAG} AS builder

# Copy source code
WORKDIR /build
COPY . /build

# Build
RUN set -x && \
    apt update && apt install git -y && \
    chmod u+x ./gradlew && ./gradlew installDist

# Runtime stage ###############################################################
# Base image
FROM eclipse-temurin:${RUNTIME_IMAGE_TAG} AS runtime

# Version info
ARG CITYDB_TOOL_VERSION
ENV CITYDB_TOOL_VERSION=${CITYDB_TOOL_VERSION}

# Copy from builder
COPY --from=builder /build/citydb-cli/build/install/citydb-tool /opt/citydb-tool

# Put start script in path
RUN set -x && \
    ln -sf /opt/citydb-tool/citydb /usr/local/bin

USER 1000
WORKDIR /data

ENTRYPOINT ["citydb"]
CMD ["--help"]
