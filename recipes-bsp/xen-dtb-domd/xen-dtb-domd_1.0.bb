LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://domd.dts"

S = "${WORKDIR}"

DEPENDS = "dtc-native"

do_compile() {
    dtc -I dts -O dtb -o ${S}/domd.dtb ${S}/domd.dts
}

do_install() {
    install -d ${D}/root
    
    install -m 0644 ${S}/domd.dtb ${D}/root/domd.dtb
}

FILES:${PN} += "/root/domd.dtb"