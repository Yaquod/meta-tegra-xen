FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI += " \
    file://xen-dom0.cfg \
    file://tegra234-orin-nano-xen.dts \
"

DEPENDS += "dtc-native"

do_configure:append() {
    bbnote "Merging Xen Dom0 config"
    ${S}/scripts/kconfig/merge_config.sh -m -O ${B} ${B}/.config ${WORKDIR}/xen-dom0.cfg
    
    yes '' | oe_runmake -C ${S} O=${B} oldconfig
}

do_compile:append() {
    bbnote "Building Xen device tree overlay"
    mkdir -p "${B}/xen-dtbo"
    dtc -@ -I dts -O dtb \
        -o "${B}/xen-dtbo/tegra234-orin-nano-xen.dtbo" \
        "${WORKDIR}/tegra234-orin-nano-xen.dts" \
        || bbfatal "Failed to compile tegra234-orin-nano-xen.dts"
    bbnote "Compiled tegra234-orin-nano-xen.dtbo successfully"
}

do_install:append() {
    BASE_DTB="${DEPLOY_DIR_IMAGE}/devicetree/tegra234-p3768-0000+p3767-0000-nv.dtb"
    OVERLAY="${B}/xen-dtbo/tegra234-orin-nano-xen.dtbo"
    MERGED="${B}/xen-dtbo/tegra234-xen-merged.dtb"

    if [ ! -f "${BASE_DTB}" ]; then
        bbfatal "Base DTB not found: ${BASE_DTB}\n \
Make sure nvidia-kernel-oot-dtb has been built before this recipe.\n \
Run: bitbake nvidia-kernel-oot-dtb first, then bitbake this recipe."
    fi

    bbnote "Merging ${BASE_DTB} + ${OVERLAY} → tegra234-xen-merged.dtb"
    fdtoverlay \
        -i "${BASE_DTB}" \
        -o "${MERGED}" \
        "${OVERLAY}" \
        || bbfatal "fdtoverlay failed: check overlay compatibility strings"

    bbnote "Installing tegra234-xen-merged.dtb to /boot"
    install -d ${D}/boot
    install -m 0644 "${MERGED}" ${D}/boot/tegra234-xen-merged.dtb

    bbnote "Installing tegra234-orin-nano-xen.dtbo to /boot/devicetree"
    install -d ${D}/boot/devicetree
    install -m 0644 "${OVERLAY}" ${D}/boot/devicetree/tegra234-orin-nano-xen.dtbo
}

do_deploy:append() {
    MERGED="${B}/xen-dtbo/tegra234-xen-merged.dtb"
    OVERLAY="${B}/xen-dtbo/tegra234-orin-nano-xen.dtbo"

    install -d ${DEPLOYDIR}/devicetree
    install -m 0644 "${MERGED}"  ${DEPLOYDIR}/devicetree/tegra234-xen-merged.dtb
    install -m 0644 "${OVERLAY}" ${DEPLOYDIR}/devicetree/tegra234-orin-nano-xen.dtbo
    bbnote "Deployed tegra234-xen-merged.dtb to ${DEPLOYDIR}/devicetree/"
}

FILES:${PN} += " \
    /boot/tegra234-xen-merged.dtb \
    /boot/devicetree/tegra234-orin-nano-xen.dtbo \
"
