FROM ubuntu:22.04

LABEL maintainer="meta-tegra-xen CI"
LABEL description="Yocto Scarthgap build environment for meta-tegra-xen"

ENV DEBIAN_FRONTEND=noninteractive
ENV LANG=en_US.UTF-8
ENV LC_ALL=en_US.UTF-8


RUN apt-get update && apt-get install -y --no-install-recommends \
    # Core build tools
    build-essential \
    gcc \
    g++ \
    make \
    # Python
    python3 \
    python3-pip \
    python3-pexpect \
    python3-git \
    python3-jinja2 \
    python3-subunit \
    # Source control
    git \
    git-lfs \
    # Compression / archive
    gzip \
    bzip2 \
    xz-utils \
    zstd \
    unzip \
    # Fetch / network
    wget \
    curl \
    ca-certificates \
    # Filesystem / disk
    cpio \
    diffstat \
    # Misc build deps
    chrpath \
    debianutils \
    file \
    locales \
    socat \
    texinfo \
    iputils-ping \
    patch \
    perl \
    rpm \
    pseudo \
    lz4 \
    # kas (Yocto project configurator)
    && pip3 install --no-cache-dir kas \
    # Generate locale
    && locale-gen en_US.UTF-8 \
    && update-locale LANG=en_US.UTF-8 \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

RUN useradd -m -s /bin/bash -u 1000 yoctouser \
    && mkdir -p /home/yoctouser/work \
    && chown -R yoctouser:yoctouser /home/yoctouser

RUN git config --system user.email "ci@meta-tegra-xen" \
    && git config --system user.name "meta-tegra-xen CI" \
    && git config --system safe.directory '*'

USER yoctouser
WORKDIR /home/yoctouser/work

CMD ["/bin/bash"]