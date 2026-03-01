FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI += "file://0001-tegra234-ras-Handle-unmatched-RAS-interrupt-graceful.patch \
            file://0001-tegra234-Clear-stale-CCPMU-RAS-error-in-bl31_platfor.patch \
            file://0001-tegra234-ras-Suppress-CCPMU-MB2-stale-boot-poison-fo.patch \
            file://0001-Pass-the-already-read-addr-into-the-poison-check-ins.patch \
            "

