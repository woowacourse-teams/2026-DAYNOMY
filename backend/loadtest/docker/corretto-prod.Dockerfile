FROM amazonlinux:2023@sha256:74c545e3e04db388b00bd31d7cc5640d4e9c12058a6d72af938d113da3c82893

# Match the headless RPM observed on production; do not follow a moving Java tag.
RUN set -eux; \
    rpm --import file:///etc/pki/rpm-gpg/RPM-GPG-KEY-amazon-linux-2023; \
    curl -fSL https://corretto.aws/downloads/resources/21.0.13.0.11/java-21-amazon-corretto-headless-21.0.13.0.11-1.amzn2023.1.aarch64.rpm -o /tmp/corretto.rpm; \
    rpm -K /tmp/corretto.rpm | grep -F 'digests signatures OK'; \
    dnf install -y /tmp/corretto.rpm; \
    rm /tmp/corretto.rpm; \
    dnf clean all; \
    test "$(rpm -q glibc)" = 'glibc-2.34-231.amzn2023.0.5.aarch64'

ENV JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto.aarch64
ENV PATH="${JAVA_HOME}/bin:${PATH}"
