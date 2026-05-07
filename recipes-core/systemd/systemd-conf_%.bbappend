FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI += " \
    file://xenbr0.netdev \
    file://xenbr0.network \
    file://eth0.network \
"

do_install:append() {
    install -d ${D}${systemd_unitdir}/network
    
    install -m 0644 ${WORKDIR}/xenbr0.netdev ${D}${systemd_unitdir}/network/
    install -m 0644 ${WORKDIR}/xenbr0.network ${D}${systemd_unitdir}/network/
    install -m 0644 ${WORKDIR}/eth0.network ${D}${systemd_unitdir}/network/
}