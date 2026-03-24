DESCRIPTION = "Merge Xen DTS overlay with NVIDIA base DTB for Tegra234"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://tegra234-orin-nano-xen.dts"
FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

DEPENDS = "dtc-native nvidia-kernel-oot-dtb"
inherit allarch deploy

do_configure[noexec] = "1"

do_compile() {
    bbnote "Compiling Xen DTS overlay"
    mkdir -p "${B}"

    dtc -@ -I dts -O dtb \
        -o "${B}/tegra234-orin-nano-xen.dtbo" \
        "${WORKDIR}/tegra234-orin-nano-xen.dts" \
        || bbfatal "dtc: failed to compile tegra234-orin-nano-xen.dts"

    BASE_DTB="${RECIPE_SYSROOT}/boot/devicetree/tegra234-p3768-0000+p3767-0000-nv.dtb"
    if [ ! -f "${BASE_DTB}" ]; then
        bbfatal "Base DTB not found at: ${BASE_DTB}"
    fi

    bbnote "Merging base DTB + overlay"
    fdtoverlay \
        -i "${BASE_DTB}" \
        -o "${B}/tegra234-xen-merged.dtb" \
        "${B}/tegra234-orin-nano-xen.dtbo" \
        || bbfatal "fdtoverlay failed"

    bbnote "Removing absent CPU nodes (fdtoverlay cannot do this)"

    python3 - "${B}/tegra234-xen-merged.dtb" "${B}/tegra234-xen-merged.dtb" << 'PYEOF'
import re, subprocess, sys, tempfile, os

input_dtb, output_dtb = sys.argv[1], sys.argv[2]

result = subprocess.run(
    ['dtc', '-I', 'dtb', '-O', 'dts', input_dtb],
    capture_output=True, text=True)
if result.returncode != 0:
    print(result.stderr); sys.exit(1)
dts = result.stdout

def remove_node(dts, pattern):
    m = re.search(pattern, dts)
    if not m:
        return dts, False
    start = m.start()
    pos = m.end() - 1
    if dts[pos] != '{':
        pos = dts.index('{', start)
    pos += 1
    depth = 1
    while pos < len(dts) and depth > 0:
        if dts[pos] == '{': depth += 1
        elif dts[pos] == '}': depth -= 1
        pos += 1
    if pos < len(dts) and dts[pos] == ';': pos += 1
    if pos < len(dts) and dts[pos] == '\n': pos += 1
    return dts[:start] + dts[pos:], True

# Remove absent cpu@ nodes
for node in ['10000', '10100','20000', '20100', '20200', '20300']:
    dts, ok = remove_node(dts, r'\t\tcpu@' + node + r' \{')
    print(f"cpu@{node}: {'removed' if ok else 'NOT FOUND'}")

# Remove cluster1 from cpu-map
dts, ok = remove_node(dts, r'\t\t\tcluster1 \{')
print(f"cpu-map/cluster1: {'removed' if ok else 'NOT FOUND'}")

# Remove cluster2/core2 and cluster2/core3 from cpu-map
for core in ['core2', 'core3']:
    cpu_map_start = dts.find('\t\tcpu-map {')
    l2_start = dts.find('\t\tl2-cache', cpu_map_start)
    cl2_m = re.search(r'\t\t\tcluster2 \{', dts[cpu_map_start:l2_start])
    if cl2_m:
        cl2_abs = cpu_map_start + cl2_m.start()
        cm = re.search(r'\t\t\t\t' + core + r' \{', dts[cl2_abs:cl2_abs+600])
        if cm:
            abs_start = cl2_abs + cm.start()
            pos = abs_start + len(cm.group())
            depth = 1
            while pos < len(dts) and depth > 0:
                if dts[pos] == '{': depth += 1
                elif dts[pos] == '}': depth -= 1
                pos += 1
            if dts[pos] == ';': pos += 1
            if dts[pos] == '\n': pos += 1
            dts = dts[:abs_start] + dts[pos:]
            print(f"cpu-map/cluster2/{core}: removed")
        else:
            print(f"cpu-map/cluster2/{core}: NOT FOUND")

with tempfile.NamedTemporaryFile(mode='w', suffix='.dts', delete=False) as f:
    f.write(dts); tmp = f.name
try:
    r = subprocess.run(
        ['dtc', '-I', 'dts', '-O', 'dtb', '-o', output_dtb,
         '-W', 'no-unit_address_vs_reg', tmp],
        capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stderr); sys.exit(1)
finally:
    os.unlink(tmp)
print("Done.")
PYEOF

    bbnote "tegra234-xen-merged.dtb created successfully"
}

do_install() {
    install -d ${D}/boot
    install -m 0644 "${B}/tegra234-xen-merged.dtb"    ${D}/boot/
    install -d ${D}/boot/devicetree
    install -m 0644 "${B}/tegra234-orin-nano-xen.dtbo" ${D}/boot/devicetree/
}

do_deploy() {
    install -d ${DEPLOYDIR}/devicetree
    install -m 0644 "${B}/tegra234-xen-merged.dtb"    ${DEPLOYDIR}/devicetree/
    install -m 0644 "${B}/tegra234-orin-nano-xen.dtbo" ${DEPLOYDIR}/devicetree/
    bbnote "Deployed tegra234-xen-merged.dtb → ${DEPLOYDIR}/devicetree/"
}

addtask deploy after do_install before do_build

FILES:${PN} = " \
    /boot/tegra234-xen-merged.dtb \
    /boot/devicetree/tegra234-orin-nano-xen.dtbo \
"
PACKAGES = "${PN}"