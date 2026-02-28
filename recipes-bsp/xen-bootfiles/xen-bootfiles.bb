DESCRIPTION = "Xen boot configuration files for Jetson Orin Nano"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = ""

inherit allarch

do_configure[noexec] = "1"
do_compile[noexec]   = "1"

do_install() {
    install -d ${D}/boot
    cat > ${D}/boot/xen.cfg << 'XENCFG'
[global]
default=xen

[xen]
options=console=dtuart dtuart=/bus@0/serial@3100000 dom0_mem=2G dom0_max_vcpus=4 sched=credit2 loglvl=all guest_loglvl=all smmu=no iommu=no serrors=forward sync_console noreboot
kernel=Image console=hvc0 earlycon=xen earlyprintk=xen clk_ignore_unused root=/dev/mmcblk0p1 rw rootwait rootfstype=ext4
ramdisk=initrd
dtb=tegra234-xen-merged.dtb
XENCFG
}

FILES:${PN} = "/boot/xen.cfg"

do_install[depends] += " \
    virtual/kernel:do_deploy \
    xen:do_install \
"
