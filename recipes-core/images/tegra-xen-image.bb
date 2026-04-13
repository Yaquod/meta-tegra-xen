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
"

IMAGE_ROOTFS_EXTRA_SPACE = "1048576"

IMAGE_BOOT_FILES += " \
    xen.efi \
    tegra234-xen-merged.dtb \
    xen.cfg \
"

IMAGE_INSTALL:append = " networkmanager networkmanager-nmcli tegra-wifi linux-firmware-iwlwifi linux-firmware-rtl8168 \
    linux-firmware-rtl8822 \
    "

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
    APPEND ${cbootargs} console=dtuart dtuart=/bus@0/serial@3100000 dom0_mem=2G dom0_max_vcpus=4 sched=credit2 loglvl=all guest_loglvl=all smmu=no iommu=no serrors=forward sync_console noreboot
XENENTRY
    fi
}
