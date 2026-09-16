FROM jenkins/jenkins:lts-jdk21

USER root

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl docker.io git jq nodejs npm \
    && rm -rf /var/lib/apt/lists/*

# Temurin JDK 25 (backend modules compile against Java 25)
RUN curl -fsSL "https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jdk/hotspot/normal/eclipse" -o /tmp/jdk25.tar.gz \
    && mkdir -p /opt/java \
    && tar -xzf /tmp/jdk25.tar.gz -C /opt/java --strip-components=1 \
    && rm -f /tmp/jdk25.tar.gz

# Maven 3.9
RUN curl -fsSL "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz" -o /tmp/mvn.tgz \
    && mkdir -p /opt/maven \
    && tar -xzf /tmp/mvn.tgz -C /opt/maven --strip-components=1 \
    && rm -f /tmp/mvn.tgz

# Helm 3
RUN curl -fsSL "https://get.helm.sh/helm-v3.16.4-linux-amd64.tar.gz" -o /tmp/helm.tgz \
    && tar -xzf /tmp/helm.tgz -C /usr/local/bin linux-amd64/helm --strip-components=1 \
    && chmod +x /usr/local/bin/helm \
    && rm -f /tmp/helm.tgz

# kubectl
RUN KUBECTL_VERSION="$(curl -fsSL https://dl.k8s.io/release/stable.txt)" \
    && curl -fsSL "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/amd64/kubectl" -o /usr/local/bin/kubectl \
    && chmod +x /usr/local/bin/kubectl

ENV JAVA_HOME=/opt/java \
    PATH=/opt/java/bin:/opt/maven/bin:$PATH \
    MAVEN_OPTS="-Xmx2g -XX:+UseG1GC" \
    MAVEN_CONFIG=/var/maven-cache/.m2

COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

COPY jenkins.yaml /var/jenkins_home/casc_configs/jenkins.yaml
RUN mkdir -p /workspace /var/maven-cache \
    && chown -R jenkins:jenkins /workspace /var/maven-cache /var/jenkins_home/casc_configs

USER jenkins
