FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI += " \
    file://xen-domd.cfg \
    file://tegra234-domd.dts \
"

DEPENDS += "dtc-native"

inherit deploy
KERNEL_CONFIG_FRAGMENTS:append = " ${WORKDIR}/xen-domd.cfg"


do_compile:append() {
    bbnote "Compiling DomD partial DTB"
    dtc -I dts -O dtb \
        -o "${B}/tegra234-domd.dtb" \
        "${WORKDIR}/tegra234-domd.dts" \
        || bbfatal "dtc: failed to compile tegra234-domd.dts"
}


do_install:append() {
    install -d "${D}/boot"
    install -m 0644 "${B}/tegra234-domd.dtb" "${D}/boot/tegra234-domd.dtb"
}


do_deploy:append() {
    install -d "${DEPLOYDIR}/devicetree"
    install -m 0644 "${B}/tegra234-domd.dtb" \
        "${DEPLOYDIR}/devicetree/tegra234-domd.dtb"
}

addtask deploy after do_install before do_build

FILES:${PN} += "/boot/tegra234-domd.dtb"