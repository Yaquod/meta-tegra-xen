SUMMARY = "Xen Domain Systemd Services and Configs"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://xl-domd.service \
    file://xl-domu.service \
    file://domd.cfg \
    file://domu.cfg \
    file://nvgpu-unbind.service \
"

inherit systemd

SYSTEMD_PACKAGES = "${PN}"
SYSTEMD_SERVICE:${PN} = "xl-domd.service xl-domu.service nvgpu-unbind.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

do_install() {
    # Install the systemd services
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/xl-domd.service ${D}${systemd_system_unitdir}/
    install -m 0644 ${WORKDIR}/xl-domu.service ${D}${systemd_system_unitdir}/
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/nvgpu-unbind.service ${D}${systemd_system_unitdir}/nvgpu-unbind.service

    # Install the Xen guest configuration files
    install -d ${D}${sysconfdir}/xen
    install -m 0644 ${WORKDIR}/domd.cfg ${D}${sysconfdir}/xen/
    install -m 0644 ${WORKDIR}/domu.cfg ${D}${sysconfdir}/xen/
}

FILES:${PN} += "${sysconfdir}/xen/domd.cfg ${sysconfdir}/xen/domu.cfg"