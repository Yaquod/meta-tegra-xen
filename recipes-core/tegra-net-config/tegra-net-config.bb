DESCRIPTION = "systemd-networkd config for Tegra234 PCIe NIC"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://10-pcie-eth.network \
           file://10-wait.conf"

do_install() {
    install -d ${D}${sysconfdir}/systemd/network
    install -m 0644 ${WORKDIR}/10-pcie-eth.network \
                    ${D}${sysconfdir}/systemd/network/10-pcie-eth.network

    install -d ${D}${sysconfdir}/systemd/network/10-pcie-eth.network.d
    install -m 0644 ${WORKDIR}/10-wait.conf \
                    ${D}${sysconfdir}/systemd/network/10-pcie-eth.network.d/10-wait.conf
}

FILES:${PN} = " \
    ${sysconfdir}/systemd/network/10-pcie-eth.network \
    ${sysconfdir}/systemd/network/10-pcie-eth.network.d/10-wait.conf \
"