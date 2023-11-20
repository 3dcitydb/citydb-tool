# 3DCityDB Importer/Exporter Dockerfile #######################################
#   Official website    https://www.3dcitydb.org
#   GitHub              https://github.com/3dcitydb/citydb-tool
###############################################################################

# Fetch & build stage #########################################################
# ARGS
ARG BUILDER_IMAGE_TAG='17-jdk-slim'
ARG RUNTIME_IMAGE_TAG='17-slim'

# Base image
FROM openjdk:${BUILDER_IMAGE_TAG} AS builder

# Copy source code
WORKDIR /build
COPY . /build

# Build
RUN chmod u+x ./gradlew && ./gradlew installDist

# Runtime stage ###############################################################
# Base image
FROM openjdk:${RUNTIME_IMAGE_TAG} AS runtime

# Version info
ARG CITYDB_TOOL_VERSION
ENV CITYDB_TOOL_VERSION=${CITYDB_TOOL_VERSION}

# Copy from builder
COPY --from=builder /build/citydb-cli/build/install/citydb /opt/citydb-tool

# Run as non-root user
RUN groupadd --gid 1000 -r citydb-tool && \
    useradd --uid 1000 --gid 1000 -d /data -m -r --no-log-init citydb-tool

# Put start script in path and set permissions
RUN ln -sf /opt/citydb-tool/citydb /usr/local/bin

WORKDIR /data
USER 1000

ENTRYPOINT ["citydb"]
CMD ["--help"]

# Labels ######################################################################
LABEL maintainer="Bruno Willenborg"
LABEL maintainer.email="b.willenborg(at)tum.de"
LABEL maintainer.organization="Chair of Geoinformatics, Technical University of Munich (TUM)"
LABEL source.repo="https://github.com/3dcitydb/importer-exporter"
