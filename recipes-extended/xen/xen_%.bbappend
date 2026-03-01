FILESEXTRAPATHS:prepend := "${THISDIR}/${PN}:"

SRC_URI += "file://0001-arm-Add-NVIDIA-Tegra234-platform-support.patch \
            file://0001-xen-Fix-printk-error.patch \
            file://0001-SError-Drain-prevents-ATF-from-seeing-SError-duringe.patch \
            file://0001-arm-efi-Tegra234-Clear-CCPMU-RAS-and-drain-SError-be.patch \
            file://xen-tegra234.cfg \
            file://0001-Move-the-RAS-clear-out-of-efi-boot.h-entirely-and-in.patch \
            file://0001-Clear-only-ERR_STATUS.V-from-Xen-never-touch-ERR_ADD.patch \
            "

# Tegra-specific build flags
EXTRA_OECONF += " \
    --enable-systemd \
    --with-xen-scriptdir=${sysconfdir}/xen/scripts \
"

# Install Xen binary to /boot for L4TLauncher
do_install:append() {
    install -d ${D}/boot
   
    if [ -f ${B}/xen/xen ]; then
        install -m 0644 ${B}/xen/xen ${D}/boot/xen.efi
    fi
}

# Deploy Xen to boot partition
do_deploy:append() {
    install -d ${DEPLOYDIR}
    if [ -f ${D}/boot/xen.efi ]; then
        install -m 0644 ${D}/boot/xen.efi ${DEPLOYDIR}/xen.efi
    fi
}

FILES:${PN} += "/boot/xen.efi"
