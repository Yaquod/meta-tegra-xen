FILESEXTRAPATHS:prepend := "${THISDIR}/${PN}:"

SRC_URI += "file://0001-arm-Add-NVIDIA-Tegra234-platform-support.patch \
            file://0001-xen-Fix-printk-error.patch \
            file://0001-arm-efi-Tegra234-Clear-CCPMU-RAS-and-drain-SError-be.patch \
            file://xen-tegra234.cfg \
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
    
    # Create Xen config directory
    install -d ${D}${sysconfdir}/xen
    
    # Default xl.conf
    cat > ${D}${sysconfdir}/xen/xl.conf << 'EOF'
# Xen toolstack configuration for Tegra
autoballoon="off"
lockfile="/var/lock/xl"
vif.default.script="vif-bridge"
vif.default.bridge="xenbr0"
EOF
}

# Deploy Xen to boot partition
do_deploy:append() {
    install -d ${DEPLOYDIR}
    if [ -f ${D}/boot/xen.efi ]; then
        install -m 0644 ${D}/boot/xen.efi ${DEPLOYDIR}/xen.efi
    fi
}

FILES:${PN} += " \
    /boot/xen.efi \
    ${sysconfdir}/xen/xl.conf \
"
