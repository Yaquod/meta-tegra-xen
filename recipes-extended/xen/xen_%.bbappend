FILESEXTRAPATHS:prepend := "${THISDIR}\files:"

# TODO: should work on xen patch here in SRC_URI

PACKAGECONFIG:append = " sdl"

EXTRA_OECONF += " \
    --enable-systemd \
    --with-xen-scriptdir=${sysconfdir}/xen/scripts \
"

do_install:append() {
    install -d ${D}/boot

    # L4TLauncher expects xen.efi
    if [ -f ${B}/xen/xen ]; then
        install -m 0644 ${B}/xen/xen ${D}/boot/xen.efi
    fi

    # Create Xen config directory
    install -d ${D}${sysconfdir}/xen

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
    install -m 0644 ${D}/boot/xen.efi ${DEPLOYDIR}/
}

addtask deploy after do_install before do_package
FILESEXTRAPATHS:prepend := "${THISDIR}/${PN}:"

SRC_URI += "file://0001-arm-Add-NVIDIA-Tegra234-platform-support.patch"

