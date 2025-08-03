FROM eclipse-temurin:21-jdk

RUN apt-get update && apt-get install -y \
    maven \
    nano \
    git \
    unzip \
    curl \
    ca-certificates \
    gnupg \
    lsb-release \
 && curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
 && apt-get install -y nodejs \
 && apt-get clean && rm -rf /var/lib/apt/lists/*

