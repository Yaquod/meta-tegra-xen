SUMMARY = "Tegra Xen DomD Image"
LICENSE = "MIT"
require recipes-core/images/core-image-minimal.bb

PACKAGE_INSTALL:append = " \
    kernel-modules \
"

WIFI_PACKAGES = " \
    wpa-supplicant \
    iw \
    wireless-regdb-static \
"

CAN_PACKAGES = " \
    iproute2 \
    can-utils \
"

GFX_PACKAGES = " \
    mesa \
    libdrm \
    kmscube \
"

IMAGE_INSTALL:append = " \
    optee-test \
    xen-tools-vchan \
    xen-tools-xl \
    xen-tools-xenstore \
    xen-tools-xenstored \
    xen-tools-xendomains \
    xen-tools-xencommons \
    xen-tools-xenstat \
    xen-tools-xen-watchdog \
    xen-tools-scripts-common \
    xen-tools-scripts-block \
    xen-tools-scripts-network \
    xen-tools-libxenstore \
    xen-tools-libxenlight \
    xen-tools-libxlutil \
    xen-tools-libxenctrl \
    xen-tools-libxentoolcore \
    xen-tools-libxentoollog \
    xen-tools-libxenevtchn \
    xen-tools-libxengnttab \
    xen-tools-libxenforeignmemory \
    xen-tools-libxenvchan \
    xen-tools-devd \
    xen-tools-console \
    xen-tools-misc \
    xen-tools-volatiles \
    cuda-toolkit \
    cuda-libraries \
    cudnn \
    tensorrt-core \
    tensorrt-plugins \
    vpi3-samples \
    deepstream-7.1 \
    deepstream-7.1-pyds \
    python3-pycuda \
    python3-cuda \
    python3-jetson-stats \
    libnvvpi3 \
    scuda-server \
    l4t-usb-device-mode \
    bridge-utils \
    iptables \
    socat \
    qemu \
    udev \
    ${@bb.utils.contains('MACHINE_FEATURES', 'domd_wifi', '${WIFI_PACKAGES}', '', d)} \
    ${@bb.utils.contains('MACHINE_FEATURES', 'domd_can', '${CAN_PACKAGES}', '', d)} \
    ${@bb.utils.contains('MACHINE_FEATURES', 'domd_hdmi', '${GFX_PACKAGES}', '', d)} \
"
IMAGE_FSTYPES = "ext4 tar.gz"
IMAGE_ROOTFS_SIZE = "16777216"