DESCRIPTION = "Merge Xen DTS overlay with NVIDIA base DTB for Tegra234 Orin Nano Super"
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

    # The base DTB is built for the standard Orin Nano (p3767-0000).
    # The overlay's root compatible includes "nvidia,tegra234" which is also
    # present in the p3767-0005-super DTB, so fdtoverlay matches correctly.
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

    bbnote "Post-processing merged DTB: removing absent CPU nodes"

    # --------------------------------------------------------------------------
    # This script removes cpu@ nodes that do NOT exist on the Orin Nano (6-core,
    # 2-cluster SKU) but that may be present if the base DTB was built from a
    # full Orin AGX source (12-core, 3-cluster).
    #
    # Orin Nano Super actual topology (from base DTS / boot log):
    #   cluster0: cpu@0, cpu@100, cpu@200, cpu@300  (Cortex-A78 × 4)
    #   cluster1: cpu@10200, cpu@10300              (Cortex-A78 × 2)
    #
    # Nodes absent on this SKU (Orin AGX only):
    #   cpu@10000, cpu@10100   (cluster1 cores 0-1 on 12-core)
    #   cpu@20000 .. cpu@20300 (cluster2, all four cores)
    #
    # cpu-map/cluster1 HAS valid core0/core1 entries on the Nano —
    # it must NOT be removed.  Only cluster1/core2 and cluster1/core3
    # are removed (they map to the absent cpu@10000/10100 phandles).
    # cluster2 is removed entirely because all four of its cpu@ nodes
    # are absent.
    # --------------------------------------------------------------------------

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

# ------------------------------------------------------------------
# Step 1: Remove absent top-level cpu@ nodes.
#
# These six nodes exist on Orin AGX (12-core) but not on Orin Nano.
# If the base DTB was built for the Nano they will simply not be found
# and the script reports NOT FOUND — that is fine and expected.
# ------------------------------------------------------------------
for node in ['10000', '10100', '20000', '20100', '20200', '20300']:
    dts, ok = remove_node(dts, r'\t\tcpu@' + node + r' \{')
    print(f"cpu@{node}: {'removed' if ok else 'not present (ok)'}")

# ------------------------------------------------------------------
# Step 2: Remove cluster2 from cpu-map entirely.
#
# cluster2 maps to cpu@20000..20300 which are all absent on the Nano.
# Removing the whole cluster2 block is safe.
# ------------------------------------------------------------------
dts, ok = remove_node(dts, r'\t\t\tcluster2 \{')
print(f"cpu-map/cluster2: {'removed' if ok else 'not present (ok)'}")

# ------------------------------------------------------------------
# Step 3: Remove ONLY core2 and core3 from cpu-map/cluster1.
#
# On the Orin Nano, cluster1 has exactly two real CPUs:
#   core0 → cpu@10200
#   core1 → cpu@10300
#
# core2 and core3 in cluster1 (if present) reference phandles for
# cpu@10000 / cpu@10100 which do NOT exist on this SKU.  Remove them.
#
# core0 and core1 are valid and must be kept — the original recipe
# removed the entire cluster1 node which broke dom0 CPU affinity.
# ------------------------------------------------------------------
cpu_map_start = dts.find('\t\tcpu-map {')
if cpu_map_start == -1:
    print("cpu-map: NOT FOUND — skipping cluster1 core pruning")
else:
    # Bound the search to within cpu-map (find its closing brace)
    brace_pos = dts.index('{', cpu_map_start) + 1
    depth = 1
    cpu_map_end = brace_pos
    while cpu_map_end < len(dts) and depth > 0:
        if dts[cpu_map_end] == '{': depth += 1
        elif dts[cpu_map_end] == '}': depth -= 1
        cpu_map_end += 1

    for core in ['core2', 'core3']:
        cl1_m = re.search(r'\t\t\tcluster1 \{', dts[cpu_map_start:cpu_map_end])
        if not cl1_m:
            print(f"cpu-map/cluster1/{core}: cluster1 not present (ok)")
            continue
        cl1_abs = cpu_map_start + cl1_m.start()
        # Find the end of the cluster1 block so we search only within it
        cl1_brace = dts.index('{', cl1_abs) + 1
        cl1_depth = 1
        cl1_end = cl1_brace
        while cl1_end < len(dts) and cl1_depth > 0:
            if dts[cl1_end] == '{': cl1_depth += 1
            elif dts[cl1_end] == '}': cl1_depth -= 1
            cl1_end += 1
        # Search for the specific core node within cluster1
        cm = re.search(r'\t\t\t\t' + core + r' \{', dts[cl1_abs:cl1_end])
        if cm:
            abs_start = cl1_abs + cm.start()
            pos = abs_start + len(cm.group())
            depth = 1
            while pos < len(dts) and depth > 0:
                if dts[pos] == '{': depth += 1
                elif dts[pos] == '}': depth -= 1
                pos += 1
            if pos < len(dts) and dts[pos] == ';': pos += 1
            if pos < len(dts) and dts[pos] == '\n': pos += 1
            dts = dts[:abs_start] + dts[pos:]
            # Recalculate cpu_map_end after modification
            cpu_map_start = dts.find('\t\tcpu-map {')
            if cpu_map_start != -1:
                bp = dts.index('{', cpu_map_start) + 1
                d = 1; cpu_map_end = bp
                while cpu_map_end < len(dts) and d > 0:
                    if dts[cpu_map_end] == '{': d += 1
                    elif dts[cpu_map_end] == '}': d -= 1
                    cpu_map_end += 1
            print(f"cpu-map/cluster1/{core}: removed")
        else:
            print(f"cpu-map/cluster1/{core}: not present (ok)")

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
    install -m 0644 "${B}/tegra234-xen-merged.dtb"     ${D}/boot/
    install -d ${D}/boot/devicetree
    install -m 0644 "${B}/tegra234-orin-nano-xen.dtbo"  ${D}/boot/devicetree/
}

do_deploy() {
    install -d ${DEPLOYDIR}/devicetree
    install -m 0644 "${B}/tegra234-xen-merged.dtb"     ${DEPLOYDIR}/devicetree/
    install -m 0644 "${B}/tegra234-orin-nano-xen.dtbo"  ${DEPLOYDIR}/devicetree/
    bbnote "Deployed tegra234-xen-merged.dtb → ${DEPLOYDIR}/devicetree/"
}

addtask deploy after do_install before do_build

FILES:${PN} = " \
    /boot/tegra234-xen-merged.dtb \
    /boot/devicetree/tegra234-orin-nano-xen.dtbo \
"
PACKAGES = "${PN}"