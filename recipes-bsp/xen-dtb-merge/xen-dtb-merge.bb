DESCRIPTION = "Merge Xen DTS overlay with NVIDIA base DTB for Tegra234"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://tegra234-orin-nano-xen.dts"

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

DEPENDS = "dtc-native nvidia-kernel-oot-dtb"

inherit allarch deploy

do_configure[noexec] = "1"

do_compile() {
    bbnote "Compiling Xen DTS overlay"
    mkdir -p "${B}"

    dtc -@ -I dts -O dtb \
        -o "${B}/tegra234-orin-nano-xen.dtbo" \
        "${WORKDIR}/tegra234-orin-nano-xen.dts" \
        || bbfatal "dtc: failed to compile tegra234-orin-nano-xen.dts"

    # nvidia-kernel-oot-dtb installs DTBs into /boot/devicetree/
    # which stages into our recipe-sysroot at the same path.
    BASE_DTB="${RECIPE_SYSROOT}/boot/devicetree/tegra234-p3768-0000+p3767-0000-nv.dtb"

    if [ ! -f "${BASE_DTB}" ]; then
        bbfatal "Base DTB not found at: ${BASE_DTB}\n\
Searched: ${RECIPE_SYSROOT}/boot/devicetree/\n\
Contents: $(ls ${RECIPE_SYSROOT}/boot/devicetree/ 2>/dev/null || echo 'directory missing')"
    fi

    bbnote "Found base DTB: ${BASE_DTB}"
    bbnote "Merging base DTB + overlay → tegra234-xen-merged.dtb"

    fdtoverlay \
        -i "${BASE_DTB}" \
        -o "${B}/tegra234-xen-merged.dtb" \
        "${B}/tegra234-orin-nano-xen.dtbo" \
        || bbfatal "fdtoverlay failed — check overlay compatible strings match base DTB"

    bbnote "tegra234-xen-merged.dtb created successfully"
}

do_install() {
    install -d ${D}/boot
    install -m 0644 "${B}/tegra234-xen-merged.dtb"     ${D}/boot/
    install -d ${D}/boot/devicetree
    install -m 0644 "${B}/tegra234-orin-nano-xen.dtbo"  ${D}/boot/devicetree/
}

do_deploy() {
    install -d ${DEPLOYDIR}/devicetree
    install -m 0644 "${B}/tegra234-xen-merged.dtb"     ${DEPLOYDIR}/devicetree/
    install -m 0644 "${B}/tegra234-orin-nano-xen.dtbo"  ${DEPLOYDIR}/devicetree/
    bbnote "Deployed tegra234-xen-merged.dtb → ${DEPLOYDIR}/devicetree/"
}

addtask deploy after do_install before do_build

FILES:${PN} = " \
    /boot/tegra234-xen-merged.dtb \
    /boot/devicetree/tegra234-orin-nano-xen.dtbo \
"

PACKAGES = "${PN}"

