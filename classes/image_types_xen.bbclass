# image_types_xen.bbclass

inherit image_types_tegra

DOMD_IMAGE_PATH = "${DEPLOY_DIR_IMAGE}/xt-image-domd-${MACHINE}.rootfs.ext4"
DOMD_SPARSE_IMAGE = "domd.img"

DOMD_PARTITION_XML = " \
    <partition name=\"DOMD\" type=\"data\"> \
        <allocation_policy> sequential </allocation_policy> \
        <filesystem_type> basic </filesystem_type> \
        <size> 21474836480 </size> \
        <file_system_attribute> 0 </file_system_attribute> \
        <allocation_attribute> 0x8 </allocation_attribute> \
        <percent_reserved> 0 </percent_reserved> \
        <align_boundary> 16384 </align_boundary> \
        <filename> ${DOMD_SPARSE_IMAGE} </filename> \
        <description> DomD Root Filesystem </description> \
    </partition>"

tegraflash_custom_post() {
    # 1. Verify DomD ext4 source exists
    if [ ! -f "${DOMD_IMAGE_PATH}" ]; then
        bbfatal "DomD ext4 not found at ${DOMD_IMAGE_PATH}"
    fi

    # 2. Convert ext4 → sparse image in-place inside the tegraflash staging dir
    #    mksparse is already in PATH here (tegra-flash-helper.sh uses it the same way)
    bbnote "Converting DomD ext4 to sparse image: ${DOMD_SPARSE_IMAGE}"
    mksparse -b ${TEGRA_BLBLOCKSIZE} --fillpattern=0 "${DOMD_IMAGE_PATH}" "./${DOMD_SPARSE_IMAGE}"

    # 3. Inject DOMD partition XML before APP in flash.xml.in
    if ! grep -q 'name="APP" id="1"' flash.xml.in; then
        bbfatal "APP partition anchor not found in flash.xml.in"
    fi

    if grep -q 'name="DOMD"' flash.xml.in; then
        bbnote "DOMD partition already present, skipping injection"
        return 0
    fi

    bbnote "Injecting DOMD partition into flash.xml.in"

    # Use a temp file to avoid sed -i portability issues
    python3 - <<'PYEOF'
import sys

xml_file = "flash.xml.in"
domd_sparse = "${DOMD_SPARSE_IMAGE}"

domd_block = """\
        <partition name="DOMD" type="data">
            <allocation_policy> sequential </allocation_policy>
            <filesystem_type> basic </filesystem_type>
            <size> 21474836480 </size>
            <file_system_attribute> 0 </file_system_attribute>
            <allocation_attribute> 0x8 </allocation_attribute>
            <percent_reserved> 0 </percent_reserved>
            <align_boundary> 16384 </align_boundary>
            <filename> """ + domd_sparse + """ </filename>
            <description> DomD Root Filesystem </description>
        </partition>
"""

with open(xml_file, 'r') as f:
    content = f.read()

anchor = '<partition name="APP" id="1"'
if anchor not in content:
    sys.exit("ERROR: APP partition anchor not found")

content = content.replace(anchor, domd_block + "        " + anchor, 1)

with open(xml_file, 'w') as f:
    f.write(content)

print("DOMD partition injected successfully")
PYEOF

    bbnote "DOMD sparse image and partition XML ready."
}