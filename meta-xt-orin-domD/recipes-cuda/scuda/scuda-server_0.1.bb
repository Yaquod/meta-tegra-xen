SUMMARY = "SCUDA server — CUDA API forwarding daemon for DomU"
LICENSE = "CLOSED"

SRC_URI = " \
    git://github.com/kevmo314/scuda;protocol=https;branch=main \
    file://scuda-server.service \
    file://build-scuda.sh \
"
SRCREV = "${AUTOREV}"
S = "${WORKDIR}/git"

# ship source and build script
# SCUDA is built natively on DomD at first boot
DEPENDS = "python3-native python3-cxxheaderparser-native"

inherit systemd

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

do_configure[noexec] = "1"
do_compile[noexec] = "1"

RDEPENDS:${PN} += "bash"

do_install() {
    # Install source tree to DomD
    install -d ${D}/opt/scuda
    cp -r ${S}/. ${D}/opt/scuda/
    
    rm -rf ${D}/opt/scuda/.git

    install -d ${D}${bindir}
    install -m 0755 ${WORKDIR}/build-scuda.sh ${D}${bindir}/build-scuda.sh

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/scuda-server.service \
        ${D}${systemd_system_unitdir}/scuda-server.service
}

SYSTEMD_SERVICE:${PN} = "scuda-server.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

FILES:${PN} += " \
    /opt/scuda \
    ${systemd_system_unitdir}/scuda-server.service \
"

INHIBIT_PACKAGE_STRIP = "1"
INHIBIT_SYSROOT_STRIP = "1"
