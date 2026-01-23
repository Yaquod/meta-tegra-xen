FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

require recipes-kernel/linux/linux-jammy-nvidia-tegra_5.15.bb

SRC_URI += "\
  file://xen-dom0.cfg \
"

KERNEL_FEATURES:append = " xen-dom0.cfg"

SRC_URI += "\
  file://tegra234-orin-nano-xen.dts \ 
"

do_compile:append() {
    # Compile Xen-specific device tree
    dtc -I dts -O dtb -o ${B}/arch/arm64/boot/dts/nvidia/tegra234-orin-nano-xen.dtb \
        ${WORKDIR}/tegra234-orin-nano-xen.dts
}

do_install:append() {
    # Install Xen DTB to boot partition
    install -d ${D}/boot
    install -m 0644 ${B}/arch/arm64/boot/dts/nvidia/tegra234-orin-nano-xen.dtb \
        ${D}/boot/
}
