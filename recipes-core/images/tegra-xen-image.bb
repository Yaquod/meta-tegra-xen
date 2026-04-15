SUMMARY = "Minimal Jetson Orin Nano image with Xen"
LICENSE = "MIT"

inherit core-image

IMAGE_INSTALL = " \
    packagegroup-core-boot \
    xen \
    xen-efi \
    xen-tools \
    xen-tools-xencommons \
    xen-tools-xenstored \
    xen-tools-xendomains \
    xen-tools-scripts-common \
    xen-tools-scripts-block \
    xen-tools-scripts-network \
    xen-tools-volatiles \
    xen-dtb-merge \
    xen-bootfiles \
    l4t-usb-device-mode \
    iptables \
    iproute2 \
    wpa-supplicant \
    iw \
    wireless-regdb-static \
    dom0-services \
"

IMAGE_BOOT_FILES += " \
    xen.efi \
    tegra234-xen-merged.dtb \
    xen.cfg \
"

IMAGE_INSTALL:append = " \
    networkmanager \
    networkmanager-nmcli \
    networkmanager-wifi \
    tegra-wifi \
    linux-firmware-rtl-nic \
    linux-firmware-rtl8168 \
    linux-firmware-rtl8822 \
    bridge-utils \
    pciutils \
    wpa-supplicant \
    iw \
    wireless-regdb-static \
    e2fsprogs e2fsprogs-mke2fs \
    util-linux \
"

IMAGE_INSTALL:remove = "networkmanager networkmanager-nmcli networkmanager-wifi init-ifupdown"
TEGRAFLASH_SDCARD_SIZE="64G"

# TEGRA_EXTRA_PARTITIONS = "domd:21474836480:xt-image-domd-${MACHINE}.rootfs.ext4"
# TEGRA_EXTRA_PARTITION_DEPS = "xt-image-domd"
# TEGRA_EXTRA_PARTITION_RESERVED = "APP"
# # You MUST also reduce the APP size here so the sum fits 64GB
# # If Dom0 is 30GB and DomD is 20GB, set Dom0 (APP) to 30GB:
# ROOTFSPART_SIZE = "32212254720"


# Inherit our custom multi-domain class
inherit tegra-multi-dom

# Define your domains: NAME:SIZE_IN_BYTES:RECIPE_NAME
# Size should be in bytes (e.g., 20GB = 21474836480)
TEGRA_DOMAINS = "\
    domd:21474836480:xt-image-domd \
"

# Ensure the main OS (Dom0) leaves room on the 64GB card
# 30GB Dom0 + 20GB DomD + 4GB DomUs = 54GB (Fits comfortably on 64GB card)
ROOTFSPART_SIZE = "32212254720"
ROOTFS_POSTPROCESS_COMMAND += "add_xen_extlinux_entry; "

add_xen_extlinux_entry() {
    EXTLINUX="${IMAGE_ROOTFS}${L4T_EXTLINUX_BASEDIR}/extlinux/extlinux.conf"
    if [ -f "${EXTLINUX}" ]; then
        sed -i 's/^DEFAULT primary/DEFAULT xen/' "${EXTLINUX}"
        cat >> "${EXTLINUX}" << 'XENENTRY'

LABEL xen
    MENU LABEL Xen Hypervisor + Linux Dom0
    LINUX /boot/xen.efi
    FDT /boot/tegra234-xen-merged.dtb
    APPEND ${cbootargs} console=dtuart dtuart=/bus@0/serial@3100000 dom0_mem=2G dom0_max_vcpus=4 sched=credit2 loglvl=all guest_loglvl=all smmu=yes iommu=no serrors=forward sync_console noreboot
XENENTRY
    fi
}
