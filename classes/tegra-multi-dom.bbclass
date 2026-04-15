TEGRA_DOMAINS ??= ""

python __init_multi_dom() {
    import bb
    domains = d.getVar('TEGRA_DOMAINS') or ''
    for entry in domains.split():
        try:
            _, _, recipe = entry.split(':')
            d.appendVarFlag('do_image_tegraflash', 'depends', f' {recipe}:do_image_complete')
        except ValueError:
            bb.fatal(f"Bad TEGRA_DOMAINS: {entry}")
}
__init_multi_dom[eventmask] = "bb.event.RecipePreFinal"
addhandler __init_multi_dom

# This hook runs inside ${WORKDIR}/tegraflash where the XML exists
tegraflash_custom_post:append() {
    python3 ${WORKDIR}/inject_domains.py
}

python do_write_inject_script() {
    import os
    script_path = os.path.join(d.getVar('WORKDIR'), 'inject_domains.py')
    
    # We use f-strings to pass Yocto variables into the script
    with open(script_path, 'w') as f:
        f.write(f"""
import os, shutil, glob, sys

deploy = "{d.getVar('DEPLOY_DIR_IMAGE')}"
machine = "{d.getVar('MACHINE')}"
domains_raw = "{d.getVar('TEGRA_DOMAINS') or ''}"
xml_file = "flash.xml.in"

if not os.path.exists(xml_file):
    print(f"DEBUG INJECT: {{xml_file}} not found in {{os.getcwd()}}")
    sys.exit(0)

with open(xml_file, 'r') as f:
    lines = f.readlines()

for entry in domains_raw.split():
    if not entry.strip(): continue
    name, size, recipe = entry.split(':')
    
    # Find rootfs
    src = os.path.join(deploy, f"{{recipe}}-{{machine}}.rootfs.ext4")
    if not os.path.exists(src):
        matches = glob.glob(os.path.join(deploy, f"{{recipe}}-*.ext4"))
        src = matches[0] if matches else None

    if src:
        # 1. Copy file
        dst = f"{{name}}.ext4"
        print(f"DEBUG INJECT: Copying {{src}} -> {{dst}}")
        shutil.copy2(src, dst)
        
        # 2. Check if already injected
        if any(f'name="{{name}}"' in l for l in lines):
            continue

        # 3. Create formatted XML block
        new_part = [
            f'        <partition name="{{name}}" type="data">\\n',
            f'            <allocation_policy> sequential </allocation_policy>\\n',
            f'            <filesystem_type> ext4 </filesystem_type>\\n',
            f'            <size> {{size}} </size>\\n',
            f'            <file_system_attribute> 0 </file_system_attribute>\\n',
            f'            <allocation_attribute> 0x8 </allocation_attribute>\\n',
            f'            <percent_reserved> 0 </percent_reserved>\\n',
            f'            <align_boundary> 16384 </align_boundary>\\n',
            f'            <filename> {{name}}.ext4 </filename>\\n',
            f'            <description> Domain partition for {{name}} </description>\\n',
            f'        </partition>\\n'
        ]

        # 4. Insert before APP partition
        for i, line in enumerate(lines):
            if '<partition name="APP"' in line:
                lines[i:i] = new_part
                print(f"DEBUG INJECT: Patched {{name}} before APP partition")
                break

with open(xml_file, 'w') as f:
    f.writelines(lines)
""")
}

# Ensure the script is written before the flash task starts
addtask write_inject_script before do_image_tegraflash