FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI += "file://xen-dom0.cfg"
KCONFIG_MODE = "--alldefconfig"
do_configure:append() {
    bbnote "Merging Xen Dom0 kernel config fragment"
    ${S}/scripts/kconfig/merge_config.sh \
        -m -O ${B} \
        ${B}/.config \
        ${WORKDIR}/xen-dom0.cfg
    yes '' | oe_runmake -C ${S} O=${B} oldconfig
}

