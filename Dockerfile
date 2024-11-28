FROM img/android:2024.11.1

WORKDIR sing-box-for-android

USER root

# Go environment
ENV GOBIN='/home/circleci/go/bin'
ENV GOPATH="/home/circleci/go"
ENV PATH="$PATH:/usr/local/go/bin:/home/circleci/go/bin"

# prepare jdk
# prepare android ndk
RUN apt install openjdk-17-jdk && \
    update-alternatives --set java /usr/lib/jvm/java-17-openjdk-amd64/bin/java && \
    update-alternatives --set javac /usr/lib/jvm/java-17-openjdk-amd64/bin/javac && \
    update-alternatives --set javadoc /usr/lib/jvm/java-17-openjdk-amd64/bin/javadoc && \
    sdkmanager --install "ndk;26.2.11394342"

# download sing-box source code 
RUN cd .. && \
    wget https://codeload.github.com/SagerNet/sing-box/zip/refs/heads/main && \
    unzip main && \
    rm main && \
    mv sing-box-main sing-box && \
    wget https://go.dev/dl/go1.23.3.linux-amd64.tar.gz && \
    tar -C /usr/local -xzf go1.23.3.linux-amd64.tar.gz && \
    rm go1.23.3.linux-amd64.tar.gz 

COPY . .

RUN cd ../sing-box && \
    make lib_install && \
    make lib_android && \
    make build_android