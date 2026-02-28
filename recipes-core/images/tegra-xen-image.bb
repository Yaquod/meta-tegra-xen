SUMMARY = "Minimal Jetson Orin Nano image with Xen"
LICENSE = "MIT"

inherit core-image

IMAGE_INSTALL = " \
    packagegroup-core-boot \
    xen \
    xen-tools \
    kernel-image \
    kernel-devicetree \
    xen-bootfiles \
"

IMAGE_ROOTFS_EXTRA_SPACE = "1048576"

IMAGE_BOOT_FILES += " \
    xen.efi \
    tegra234-xen-merged.dtb \
    xen.cfg \
"
