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


install -d ${D}/boot/extlinux
    cat > ${D}/boot/extlinux/extlinux.conf << 'EXTLINUX'
TIMEOUT 30
DEFAULT xen

MENU TITLE Jetson Orin Nano Boot Options

LABEL xen
      MENU LABEL Xen Hypervisor + Linux Dom0
      LINUX /boot/xen.efi
      FDT /boot/tegra234-xen-merged.dtb
      APPEND console=dtuart dtuart=/bus@0/serial@3100000 dom0_mem=2G dom0_max_vcpus=4 sched=credit2 loglvl=all guest_loglvl=all smmu=no iommu=no serrors=forward sync_console noreboot

LABEL linux
      MENU LABEL Linux (no Xen, fallback)
      LINUX /boot/Image
      FDT /boot/tegra234-p3768-0000+p3767-0000-nv.dtb
      APPEND ${cbootargs} root=/dev/mmcblk0p1 rw rootwait rootfstype=ext4 console=ttyTCU0,115200
      INITRD /boot/initrd
EXTLINUX

}

FILES:${PN} = " \
    /boot/xen.cfg \
    /boot/extlinux/extlinux.conf \
"
do_install[depends] += " \
    virtual/kernel:do_deploy \
    xen:do_install \
"
