FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

ATF_DEBUG = "1"
ATF_LOG_LEVEL = "40"

do_install() {
    install -d ${D}${datadir}/trusted-os
    install -m 0644 ${B}/tegra/${TARGET_SOC}/debug/bl31.bin ${D}${datadir}/trusted-os/
}

SRC_URI += " \
    file://0001-tegra234-ras-Handle-unmatched-RAS-interrupt-graceful.patch \
    file://0001-tegra234-Clear-stale-CCPMU-RAS-error-in-bl31_platfor.patch \
    file://0001-tegra234-ras-Suppress-CCPMU-MB2-stale-boot-poison-fo.patch \
    file://0001-Pass-the-already-read-addr-into-the-poison-check-ins.patch \
"
PR:append = ".1"
