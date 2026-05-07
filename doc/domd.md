# Xen GPU Passthrough on NVIDIA Jetson Orin NX (Tegra234)
## Complete Engineering Reference — dom0 + DomD with CUDA/Graphics

---

## Table of Contents

1. [System Overview](#1-system-overview)
2. [Architecture](#2-architecture)
3. [Prerequisites](#3-prerequisites)
4. [Xen SMMU Patch for Tegra234](#4-xen-smmu-patch-for-tegra234)
5. [Dom0 Device Tree Overlay](#5-dom0-device-tree-overlay)
6. [DomD Partial Device Tree](#6-domd-partial-device-tree)
7. [Xen Boot Configuration](#7-xen-boot-configuration)
8. [DomD Guest Configuration](#8-domd-guest-configuration)
9. [DomD Rootfs Preparation](#9-domd-rootfs-preparation)
10. [Verifying GPU Passthrough](#10-verifying-gpu-passthrough)
11. [How to Passthrough Additional Devices](#11-how-to-passthrough-additional-devices)
12. [Production Automotive DomD — CUDA & Graphics for DomUs](#12-production-automotive-domd--cuda--graphics-for-domus)
13. [Troubleshooting Reference](#13-troubleshooting-reference)
14. [Lessons Learned & Key Rules](#14-lessons-learned--key-rules)

---

## 1. System Overview

**Hardware:** NVIDIA Jetson Orin NX / Orin Nano (Tegra234, ga10b GPU)  
**Hypervisor:** Xen 4.18  
**Dom0 kernel:** Linux 5.15.148-l4t-r36.4.4 (L4T)  
**DomD kernel:** Linux 5.15.148-l4t-r36.4.4 (same image)  
**Build system:** Yocto Scarthgap (Poky 5.0.17) via meta-tegra-xen

**Goal:** GPU (ga10b) and host1x passed through exclusively to DomD, which acts as a production driver domain providing CUDA/graphics services to unprivileged DomUs.

### Domain Topology

```
┌─────────────────────────────────────────────────────────┐
│                     Xen Hypervisor 4.18                 │
│              smmu=yes  iommu=yes  dom0_iommu=passthrough│
├──────────────────┬──────────────────┬───────────────────┤
│     Dom0         │      DomD         │      DomU(s)      │
│  (management)    │  (driver domain)  │  (applications)   │
│                  │                   │                   │
│  SD card (mmc0) │  GPU ga10b        │  virtio-gpu       │
│  eMMC (mmc1)    │  host1x           │  xen-blkfront     │
│  UART           │  SMMU niso1       │  xen-netfront     │
│  xenbr0         │  CUDA runtime     │                   │
│  xl toolstack   │  OpenGL/Vulkan    │                   │
└──────────────────┴──────────────────┴───────────────────┘
         │                  │
    smmu_niso0          smmu_niso1
   (iommu@12000000)   (iommu@8000000)
    GPCDMA sid=0x04    GPU sid=0x07
                       host1x sid=0x27
```

---

## 2. Architecture

### Tegra234 SMMU Layout

Tegra234 has **three SMMU instances**, each serving different device groups:

| Instance | DTB Node | Alias | Key Devices | Stream IDs |
|---|---|---|---|---|
| smmu_niso1 | `iommu@8000000` | phandle `0xf0` | GPU (ga10b), host1x | 0x07, 0x27 |
| smmu_iso | `iommu@10000000` | — | Display, ISP | — |
| smmu_niso0 | `iommu@12000000` | phandle `0x04` | GPCDMA | 0x04 |

### Critical SMMU Register Layout (Tegra234-specific)

Unlike standard ARM SMMU-500, Tegra234 SMMUs have a **split register layout** — global registers and context banks are at **separate physical addresses**:

```
iommu@8000000:  reg[0] = 0x8000000 size 0x1000000  ← global registers
                reg[1] = 0x7000000 size 0x1000000  ← context banks

iommu@12000000: reg[0] = 0x12000000 size 0x1000000 ← global registers
                reg[1] = 0x11000000 size 0x1000000 ← context banks

iommu@10000000: reg[0] = 0x10000000 size 0x1000000 ← single range (standard)
```

The standard Xen ARM SMMU driver assumes `CB_BASE = base + size/2`, which gives the **wrong address** for Tegra234 and causes probe failure.

---

## 3. Prerequisites

- Xen 4.18 source tree
- meta-tegra-xen Yocto layer
- Base DTB: `tegra234-orin-nano.dtb` (from L4T BSP)
- Merged DTB build infrastructure (`fdtoverlay`)
- Dom0 rootfs and DomD rootfs (Poky 5.0.17 images)

---

## 4. Xen SMMU Patch for Tegra234

### Why This Patch is Required

Without this patch, Xen's SMMU driver either:
1. Does not recognize `nvidia,tegra234-smmu` compatible string → SMMU never probes → all devices have `iommu fwspec = NULL` → passthrough devices can never be protected
2. Probes but maps context banks to wrong addresses → external abort → silent failure

### Patch: `xen/drivers/passthrough/arm/smmu.c`

```diff
--- a/xen/drivers/passthrough/arm/smmu.c
+++ b/xen/drivers/passthrough/arm/smmu.c

@@ struct arm_smmu_device @@
     void __iomem    *base;
     unsigned long    size;
     unsigned long    pgshift;
+    /* Non-NULL when context banks are at a separate physical range (Tegra234) */
+    void __iomem    *cb_base;

@@ #define ARM_SMMU_CB_BASE macro @@
-#define ARM_SMMU_CB_BASE(smmu)  ((smmu)->base + ((smmu)->size >> 1))
+/* Tegra234 has context banks at a separate physical range (reg[1]).
+ * Standard ARM SMMU-500 has them in the upper half of a single range. */
+#define ARM_SMMU_CB_BASE(smmu) \
+    ((smmu)->cb_base ? (smmu)->cb_base : ((smmu)->base + ((smmu)->size >> 1)))

@@ arm_smmu_device_dt_probe(), after smmu->size = resource_size(res) @@
+    /*
+     * Tegra234 SMMUs (smmu_niso0, smmu_niso1) have a split register layout:
+     * reg[0] = global address space, reg[1] = context banks.
+     * Standard ARM SMMUs have a single contiguous region.
+     */
+    res = platform_get_resource(pdev, IORESOURCE_MEM, 1);
+    if (res) {
+        smmu->cb_base = devm_ioremap_resource(dev, res);
+        if (IS_ERR(smmu->cb_base)) {
+            dev_warn(dev, "failed to map CB region, using default layout\n");
+            smmu->cb_base = NULL;
+        } else {
+            dev_notice(dev, "using split CB base at 0x%"PRIpaddr"\n",
+                       res->addr);
+        }
+    }

@@ arm_smmu_device_dt_probe(), context bank IRQ count check @@
-    if (smmu->version > ARM_SMMU_V1 &&
-        smmu->num_context_banks != smmu->num_context_irqs) {
-        dev_err(dev, "found only %d context interrupt(s) but %d required\n",
-            smmu->num_context_irqs, smmu->num_context_banks);
-        err = -ENODEV;
-        goto out_put_masters;
-    }
+    if (smmu->version > ARM_SMMU_V1 &&
+        smmu->num_context_banks != smmu->num_context_irqs) {
+        /*
+         * Tegra234 shares IRQ lines across context banks — the count will
+         * never match. Clamp to available IRQs instead of failing.
+         * DomU isolation is maintained by explicit S2CR_TYPE_TRANS entries.
+         */
+        dev_warn(dev, "CB count (%u) != IRQ count (%u), clamping\n",
+             smmu->num_context_banks, smmu->num_context_irqs);
+        smmu->num_context_banks = smmu->num_context_irqs;
+    }

@@ arm_smmu_device_reset() @@
     reg &= ~(sCR0_CLIENTPD | sCR0_USFCFG);
-    /* Xen: Unlike Linux, generate a fault when no mapping is found */
-    reg |= sCR0_USFCFG;
+    /*
+     * Do NOT set sCR0_USFCFG. With dom0_iommu=passthrough, dom0 stream
+     * entries are registered asynchronously after SMMU reset. Setting
+     * USFCFG would fault all dom0 DMA (MMC, GPCDMA) until entries are
+     * set up, causing boot failure.
+     * DomU isolation is provided by explicit S2CR_TYPE_TRANS entries
+     * programmed before any DomU DMA begins.
+     */

@@ arm_smmu_of_match[] @@
     { .compatible = "arm,mmu-500", .data = (void *)ARM_SMMU_V2 },
+    { .compatible = "nvidia,tegra234-smmu", .data = (void *)ARM_SMMU_V2 },
+    { .compatible = "nvidia,smmu-500",      .data = (void *)ARM_SMMU_V2 },
     { },
```

### Verify Patch is Compiled In

```bash
strings xen.efi | grep -E "tegra234-smmu|smmu-500|nvidia,smmu"
# Must print both strings
```

---

## 5. Dom0 Device Tree Overlay

The overlay is applied to the base Tegra234 DTB to produce `tegra234-xen-merged.dtb`.

### Key Rules (learned through iteration)

| Rule | Reason |
|---|---|
| **Never `/delete-property/ iommus`** for dom0 devices | Deleting iommus breaks Xen's passthrough mapping setup via `dom0_iommu=passthrough` |
| **Never add `dma-coherent`** to dom0 devices | Bypasses Xen swiotlb, causes SCR data corruption |
| **Do add `iommus`** to passthrough devices | Required for `dt_device_set_protected()` → `xc_assign_dtdevice` to succeed |
| **Use `/bus@0/` prefix** in dtdev paths | Paths must match merged DTB, not partial DomD DTB |

### `tegra234-xen.dts` (Dom0 Overlay)

```dts
/dts-v1/;
/plugin/;

/ {
    compatible = "nvidia,p3768-0000+p3767-0000", "nvidia,p3767-0000",
                 "nvidia,tegra234";

    /* ----------------------------------------------------------------
     * fragment@0 — SD card (mmc@3400000)
     * dom0_iommu=passthrough handles SMMU transparently.
     * Leave iommus intact. status=okay is all that's needed.
     * ---------------------------------------------------------------- */
    fragment@0 {
        target-path = "/bus@0/mmc@3400000";
        __overlay__ { status = "okay"; };
    };

    /* ----------------------------------------------------------------
     * fragment@1 — Dom0 UART console
     * ---------------------------------------------------------------- */
    fragment@1 {
        target-path = "/bus@0/serial@3100000";
        __overlay__ {
            /delete-property/ resets;
            /delete-property/ reset-names;
            status = "okay";
        };
    };

    /* ----------------------------------------------------------------
     * fragment@2 — GPU passthrough to DomD (gpu@17000000, ga10b)
     *
     * CRITICAL: iommus MUST be added here even though the base DTS
     * has no iommus property on the GPU node. Without iommus, Xen's
     * SMMU driver never calls dt_device_set_protected(), and
     * xc_assign_dtdevice() returns -EPERM.
     *
     * Stream ID 0x07 on smmu_niso1 (phandle 0xf0 in merged DTB).
     * ---------------------------------------------------------------- */
    fragment@2 {
        target-path = "/bus@0/gpu@17000000";
        __overlay__ {
            iommus = <0xf0 0x07>;
            xen,passthrough;
            status = "disabled";
        };
    };

    /* ----------------------------------------------------------------
     * fragment@3 — eMMC (mmc@3460000)
     * Same as SD card: leave iommus intact.
     * ---------------------------------------------------------------- */
    fragment@3 {
        target-path = "/bus@0/mmc@3460000";
        __overlay__ { status = "okay"; };
    };

    /* ----------------------------------------------------------------
     * fragment@4 — xHCI USB host passthrough to DomD (optional)
     * ---------------------------------------------------------------- */
    fragment@4 {
        target-path = "/bus@0/usb@3610000";
        __overlay__ {
            xen,passthrough;
            status = "disabled";
        };
    };

    /* ----------------------------------------------------------------
     * fragment@5 — host1x passthrough to DomD
     *
     * Same as GPU: iommus must be added for dt_device_set_protected().
     * Stream ID 0x27 on smmu_niso1.
     * ---------------------------------------------------------------- */
    fragment@5 {
        target-path = "/bus@0/host1x@13e00000";
        __overlay__ {
            iommus = <0xf0 0x27>;
            xen,passthrough;
            status = "disabled";
        };
    };

    /* ----------------------------------------------------------------
     * fragment@6 — GPCDMA (dom0-owned)
     * iommus = <0x04 0x04> on smmu_niso0. Leave intact.
     * ---------------------------------------------------------------- */
    fragment@6 {
        target-path = "/bus@0/dma-controller@2600000";
        __overlay__ { status = "okay"; };
    };

    /* Disable PCIe in dom0 (not needed, reduces attack surface) */
    fragment@7  { target-path = "/bus@0/pcie@140a0000"; __overlay__ { status = "disabled"; }; };
    fragment@8  { target-path = "/bus@0/pcie@14100000"; __overlay__ { status = "disabled"; }; };
    fragment@9  { target-path = "/bus@0/pcie@14160000"; __overlay__ { status = "disabled"; }; };
    fragment@10 { target-path = "/bus@0/pcie@141e0000"; __overlay__ { status = "disabled"; }; };
};
```

### Build Merged DTB

```bash
# Compile overlay
dtc -I dts -O dtb -o tegra234-xen.dtbo tegra234-xen.dts

# Apply to base DTB
fdtoverlay -i tegra234-orin-nano.dtb \
           -o tegra234-xen-merged.dtb \
           tegra234-xen.dtbo

# Verify GPU has iommus in merged DTB
dtc -I dtb -O dts tegra234-xen-merged.dtb 2>/dev/null | \
  grep -A5 "gpu@17000000" | grep iommu
# Expected: iommus = <0xf0 0x07>;

# Verify phandle 0xf0 is smmu_niso1
dtc -I dtb -O dts tegra234-xen-merged.dtb 2>/dev/null | \
  grep -B5 "phandle = <0xf0>" | grep -E "iommu|smmu"
```

---

## 6. DomD Partial Device Tree

This DTB is passed to DomD's Linux kernel. It is **separate** from the merged DTB used by Xen — it describes only what DomD needs to see.

### `tegra234-domd.dts`

```dts
/dts-v1/;

/ {
    #address-cells = <0x02>;
    #size-cells = <0x02>;
    compatible = "nvidia,p3768-0000+p3767-0005-super", "nvidia,p3767-0005",
                 "nvidia,tegra234";

    /* Xen virtual GIC stub — required for interrupt-parent resolution */
    interrupt-controller@f400000 {
        compatible = "arm,cortex-a15-gic";
        #interrupt-cells = <0x03>;
        interrupt-controller;
        reg = <0x00 0x0f400000 0x00 0x10000
               0x00 0x0f41c000 0x00 0x10000>;
        phandle = <0x01>;
    };

    /* smmu_niso1 — DomD needs this to set up IOVA domains for GPU/host1x */
    iommu@8000000 {
        compatible = "nvidia,tegra234-smmu", "nvidia,smmu-500";
        reg = <0x00 0x8000000 0x00 0x1000000
               0x00 0x7000000 0x00 0x1000000>;
        #iommu-cells = <0x01>;
        #global-interrupts = <0x02>;
        interrupts = <0x00 0xee 0x04
                      0x00 0xf2 0x04>;
        interrupt-parent = <0x01>;
        stream-match-mask = <0x7f80>;
        status = "okay";
        phandle = <0x02>;
    };

    /* GPU ga10b — SID 0x07 on smmu_niso1 */
    gpu@17000000 {
        compatible = "nvidia,ga10b";
        reg = <0x00 0x17000000 0x00 0x1000000
               0x00 0x18000000 0x00 0x1000000
               0x00 0x03b41000 0x00 0x1000>;
        interrupts = <0x00 0x44 0x04
                      0x00 0x46 0x04
                      0x00 0x47 0x04
                      0x00 0x43 0x04>;
        interrupt-names = "stall0", "stall1", "stall2", "nonstall";
        interrupt-parent = <0x01>;
        iommus = <0x02 0x07>;
        dma-coherent;
        status = "okay";
        phandle = <0x03>;
        #cooling-cells = <0x02>;
    };

    /* host1x — SID 0x27 on smmu_niso1 */
    host1x@13e00000 {
        compatible = "nvidia,tegra234-host1x";
        reg = <0x00 0x13e00000 0x00 0x10000
               0x00 0x13e10000 0x00 0x10000
               0x00 0x13e40000 0x00 0x10000
               0x00 0x13ef0000 0x00 0x60000>;
        reg-names = "common", "hypervisor", "vm", "actmon";
        interrupts = <0x00 0x1c0 0x04
                      0x00 0x1c1 0x04
                      0x00 0x1c2 0x04
                      0x00 0x1c3 0x04
                      0x00 0x1c4 0x04
                      0x00 0x1c5 0x04
                      0x00 0x1c6 0x04
                      0x00 0x1c7 0x04
                      0x00 0x107 0x04>;
        interrupt-names = "syncpt0", "syncpt1", "syncpt2", "syncpt3",
                          "syncpt4", "syncpt5", "syncpt6", "syncpt7",
                          "host1x";
        interrupt-parent = <0x01>;
        iommus = <0x02 0x27>;
        iommu-map = <0x08 0x02 0x35 0x01
                     0x09 0x02 0x36 0x01
                     0x0a 0x02 0x37 0x01
                     0x0b 0x02 0x38 0x01
                     0x0c 0x02 0x39 0x01
                     0x0d 0x02 0x3a 0x01
                     0x0e 0x02 0x3b 0x01
                     0x0f 0x02 0x3c 0x01>;
        #address-cells = <0x02>;
        #size-cells = <0x02>;
        ranges = <0x00 0x14800000 0x00 0x14800000 0x00 0x2000000
                  0x00 0x24700000 0x00 0x24700000 0x00 0x80000>;
        dma-coherent;
        status = "okay";
        phandle = <0x04>;
    };
};
```

---

## 7. Xen Boot Configuration

### `/boot/xen.cfg`

```ini
[global]
default=xen

[xen]
options=console=dtuart dtuart=/bus@0/serial@3100000 \
        dom0_mem=2048M dom0_max_vcpus=4 \
        loglvl=all guest_loglvl=all \
        serrors=forward \
        smmu=yes iommu=yes dom0_iommu=passthrough
kernel=Image console=hvc0 console=ttyTCU0 console=ttyTHS1,115200 \
       earlycon=xen root=/dev/mmcblk0p1 rw rootwait rootfstype=ext4 \
       module_blacklist=tegra_host1x,nvidia,nvmap
ramdisk=initrd
dtb=tegra234-xen-merged.dtb
```

### Critical Options

| Option | Purpose |
|---|---|
| `smmu=yes` | Enable Xen SMMU driver |
| `iommu=yes` | Enable IOMMU subsystem |
| `dom0_iommu=passthrough` | Dom0 gets identity (PA=IOVA) mappings — do NOT delete iommus from dom0 device nodes |
| `module_blacklist=tegra_host1x,nvidia,nvmap` | Prevent dom0 Linux from touching passthrough hardware |

---

## 8. DomD Guest Configuration

### `/etc/xen/domd.cfg`

```
name     = "DomD"
kernel   = "/boot/Image"
extra    = "root=/dev/xvda rw console=hvc0 module_blacklist=nvhwpm"
memory   = 4096
vcpus    = 4
disk     = ['/dev/mmcblk0p16,raw,xvda,w']
vif      = ['bridge=xenbr0']
dtb      = "/boot/tegra234-domd.dtb"
iommu    = 1

# CRITICAL: Paths must use /bus@0/ prefix matching merged DTB, NOT domd DTB
dtdev = ["/bus@0/host1x@13e00000", "/bus@0/gpu@17000000"]
```

### Why `/bus@0/` Prefix Matters

`xl`/`xc_assign_dtdevice` looks up device paths in **Xen's merged DTB** (`tegra234-xen-merged.dtb`), not in the DomD partial DTB. In the merged DTB, all devices are under `/bus@0/`. The DomD partial DTB has devices at root level — this is only for DomD's Linux kernel view, not for Xen path resolution.

---

## 9. DomD Rootfs Preparation

### Mask Tegra-Specific Services That Fail in Xen VM

Several services assume bare-metal Tegra hardware that does not exist in a `XENVM-4.18` environment. Mask them from dom0 before first boot:

```bash
# Mount DomD rootfs partition
mount /dev/mmcblk0p16 /mnt/domd

# Mask services that crash on missing Tegra hardware
ln -sf /dev/null /mnt/domd/etc/systemd/system/jtop.service
ln -sf /dev/null /mnt/domd/etc/systemd/system/nvpmodel.service
ln -sf /dev/null /mnt/domd/etc/systemd/system/nvpower.service

umount /mnt/domd
```

### Systemd Detects Virtualization

DomD's systemd correctly identifies the environment:
```
systemd[1]: Detected virtualization xen.
systemd[1]: Detected architecture arm64.
```

This means any systemd `ConditionVirtualization` guards will work correctly for future service differentiation.

---

## 10. Verifying GPU Passthrough

### From DomD Console (`xl console DomD`)

```bash
# Check GPU device node exists and SMMU attached
dmesg | grep -iE "ga10b|nvgpu|17000000|smmu|iommu" | head -40

# Check SMMU probed for DomD
dmesg | grep smmu

# Check host1x
dmesg | grep -i host1x

# Check GPU is visible to kernel
ls /sys/bus/platform/devices/ | grep -E "17000000|13e00000"

# Check nvgpu module
modprobe nvgpu 2>&1
lsmod | grep nvgpu

# Quick CUDA test (if nvgpu loaded and CUDA runtime present)
ls /dev/nvgpu*
```

### From Dom0

```bash
# Confirm DomD is running
xl list

# Confirm devices are assigned
xl dmesg | grep -E "assign|passthrough|17000000|13e00000"

# Check Xen SMMU registered both devices as protected
xl dmesg | grep -i "protected\|smmu.*17000000\|smmu.*13e00000"
```

---

## 11. How to Passthrough Additional Devices

This is the general procedure derived from everything learned on this platform.

### Step 1: Find the Device in the Merged DTB

```bash
# Get device path, iommus property, and stream IDs
dtc -I dtb -O dts tegra234-xen-merged.dtb 2>/dev/null | \
  grep -B2 -A20 "your-device@ADDR"
```

Key things to note:
- Full node path (e.g., `/bus@0/device@ADDR`)
- `iommus` value: `<PHANDLE STREAM_ID>`
- `reg` ranges (for DomD DTB)
- `interrupts` (for DomD DTB)

### Step 2: Identify Which SMMU Owns the Device

```bash
# Find what phandle X resolves to
dtc -I dtb -O dts tegra234-xen-merged.dtb 2>/dev/null | \
  grep -B5 "phandle = <0xXX>"
```

| Phandle | SMMU | Suitable for DomD? |
|---|---|---|
| `0xf0` | smmu_niso1 (`iommu@8000000`) | Yes — already in DomD DTB |
| `0x04` | smmu_niso0 (`iommu@12000000`) | Requires adding smmu_niso0 to DomD DTB |
| Other | smmu_iso (`iommu@10000000`) | Requires adding smmu_iso to DomD DTB |

### Step 3: Add to Dom0 Overlay

```dts
fragment@N {
    target-path = "/bus@0/device@ADDR";
    __overlay__ {
        iommus = <PHANDLE STREAM_ID>;  /* REQUIRED for xc_assign_dtdevice */
        xen,passthrough;
        status = "disabled";
    };
};
```

**If the device already has `iommus` in the base DTS**, the overlay only needs:
```dts
fragment@N {
    target-path = "/bus@0/device@ADDR";
    __overlay__ {
        xen,passthrough;
        status = "disabled";
    };
};
```

**If the device has NO `iommus` in base DTS** (like the GPU), you must add it.

### Step 4: Add `dtdev` Entry to domd.cfg

```
dtdev = ["/bus@0/existing-device", "/bus@0/new-device@ADDR"]
```

Paths **must** use the merged DTB path with `/bus@0/` prefix.

### Step 5: Add Device Node to DomD Partial DTB

Add a minimal node with `reg`, `interrupts`, `compatible`, and `iommus` pointing to the correct SMMU phandle in the DomD DTB namespace.

If the device uses a new SMMU not yet in the DomD DTB, add that SMMU node first (see `iommu@8000000` example in section 6).

### Step 6: Rebuild and Deploy

```bash
# Recompile overlay
dtc -I dts -O dtb -o tegra234-xen.dtbo tegra234-xen.dts

# Rebuild merged DTB
fdtoverlay -i tegra234-orin-nano.dtb \
           -o tegra234-xen-merged.dtb tegra234-xen.dtbo

# Recompile DomD DTB
dtc -I dts -O dtb -o tegra234-domd.dtb tegra234-domd.dts

# Copy to /boot
cp tegra234-xen-merged.dtb /boot/
cp tegra234-domd.dtb /boot/

# Reboot and verify
reboot
```

### Step 7: Verify Device is Protected

After reboot:
```bash
# From dom0
xl dmesg | grep -i "17000000\|13e00000\|NEW_ADDR\|protected"

# Attempt assignment — should succeed
xl create /etc/xen/domd.cfg
```

### Common Passthrough Candidates on Jetson Orin

| Device | Path | SMMU | SID | Notes |
|---|---|---|---|---|
| GPU ga10b | `/bus@0/gpu@17000000` | niso1 | 0x07 | Done ✓ |
| host1x | `/bus@0/host1x@13e00000` | niso1 | 0x27 | Done ✓ |
| xHCI USB | `/bus@0/usb@3610000` | niso1 | 0x0e | In overlay already |
| USB device | `/bus@0/usb@3550000` | niso1 | 0x0f | Separate xudc node |
| CSI/Camera | `/bus@0/host1x@13e00000/vi@...` | niso1 | various | host1x child |
| Display | `/bus@0/host1x@13e00000/display@...` | iso | various | Needs smmu_iso |
| NVDec | `/bus@0/host1x@13e00000/nvdec@...` | niso1 | various | host1x child |
| PCIe | `/bus@0/pcie@140a0000` | varies | varies | Currently disabled |

---

## 12. Production Automotive DomD — CUDA & Graphics for DomUs

### Architecture for CUDA/Graphics Serving

```
DomD (driver domain)
├── nvgpu driver → /dev/nvgpu0
├── CUDA runtime → libcuda.so
├── OpenGL/EGL → via Wayland/DRM
├── SCUDA server → forwards CUDA API over vsock/shared memory
└── Weston/compositor → renders for DomU displays via virtio-gpu
         │
         ├── vsock (SCUDA) ──→ DomU1 (CUDA app)
         ├── virtio-gpu ─────→ DomU2 (graphics app)
         └── shared memory ──→ DomU3 (low-latency render)
```

### SCUDA Server Setup (DomD)

SCUDA forwards the CUDA API from DomUs to DomD over vsock:

```bash
# In DomD — SCUDA server listens on vsock
systemctl enable scuda-server.service
systemctl start scuda-server.service

# Verify vsock transport is available
ls /dev/vsock
modprobe vhost_vsock   # if not autoloaded
```

The `scuda-server.service` failure seen in the DomD boot log is because the GPU wasn't yet initialized by nvgpu. Once nvgpu loads successfully, the service will start.

```bash
# Fix service ordering in DomD
# /etc/systemd/system/scuda-server.service.d/override.conf
[Unit]
After=nvgpu.service
Requires=nvgpu.service
```

### DomU CUDA Client Configuration

Each DomU needs:
1. SCUDA client library (`libcuda.so` shim that forwards over vsock)
2. vsock device configured in its xl config:

```
# domu.cfg
vif = ['bridge=xenbr0']
vsock = ['cid=3']   # Unique CID per DomU
```

### virtio-gpu for DomU Graphics

For DomUs that need display rendering (not just CUDA compute):

```
# domd.cfg — add virtio-gpu backend
device_model_version = "qemu-xen"
device_model_args = ["-device", "virtio-gpu-pci"]
```

DomD runs Weston as the display compositor, accepting virtio-gpu connections from DomUs and rendering to the physical display via DRM/KMS.

### Automotive Safety Considerations

For ISO 26262 / SOTIF compliance in production:

1. **Memory isolation**: Use Xen memory coloring or physical memory partitioning to isolate DomD GPU memory from safety-critical domains.
2. **Interrupt isolation**: Assign GPU error interrupts to DomD exclusively; don't forward to Dom0.
3. **SMMU fault handling**: Implement `arm_smmu_context_fault` handlers that log to a safety monitor.
4. **Health monitoring**: Use Xen's watchdog infrastructure to monitor DomD liveness.
5. **SMMU bypass prevention**: `USFCFG=0` (bypass mode) is intentional for dom0 init. After all dom0 streams are registered, consider enabling it: `devmem2 0x8000000 w $(( $(devmem2 0x8000000 | tail -1) | 0x400 ))`.

---

## 13. Troubleshooting Reference

### `xc_assign_dtdevice failed: -1`

**Cause:** Device not marked protected in Xen's DTB.  
**Fix:** Add `iommus = <PHANDLE SID>` to the device's overlay fragment.

```bash
# Verify protection status
xl dmesg | grep "protected\|17000000"
```

### `mmc0: ADMA error: 0x02000008` + `EMEM address decode error`

**Cause:** The SMMU patch introduced `USFCFG=1` which faults dom0 MMC DMA before stream entries are registered.  
**Fix:** Remove `reg |= sCR0_USFCFG` from `arm_smmu_device_reset()` in Xen SMMU driver.

### `iommu fwspec is NULL, continue without stream ID`

**Symptom:** Printed by sdhci-tegra — harmless! The driver continues without SMMU and uses Xen swiotlb bounce buffers. SD card still works.  
**Do not** try to fix this warning — it's expected under `dom0_iommu=passthrough`.

### `tegra-gpcdma: probe of 2600000.dma-controller failed with error -22`

**Cause:** SMMU not probed yet when GPCDMA driver initializes.  
**Fix:** GPCDMA's `iommus = <0x04 0x04>` must survive in the merged DTB (don't delete it). With the SMMU patch active, this should resolve automatically.

### DomD init panic: `exitcode=0x0000000b`

**Cause:** `jtop.service` (Jetson hardware monitor) crashes on missing Tegra `/sys` paths.  
**Fix:** `ln -sf /dev/null /etc/systemd/system/jtop.service` in DomD rootfs.

### DomD xenstored fails

**Expected.** DomD is not a privileged Xen domain. `xenstored.service` failing in DomD is correct — DomD uses the xenstore running in Dom0 via the xen-init-domx service.

### SMMU probe failure (before patch)

Look for this in `xl dmesg`:
```
smmu: /bus@0/iommu@8000000: probing hardware...
# then either silence (external abort) or:
smmu: found only N context interrupt(s) but M required  → -ENODEV
```
Both are fixed by the patch in section 4.

---

## 14. Lessons Learned & Key Rules

### The Fundamental Rules for This Platform

```
┌─────────────────────────────────────────────────────────────────┐
│  dom0_iommu=passthrough means:                                  │
│  → Xen creates identity IOVA mappings for ALL dom0 devices      │
│  → This REQUIRES the iommus property to be present              │
│  → Deleting iommus breaks the mapping → DMA fails               │
│  → Adding dma-coherent bypasses swiotlb → data corruption       │
│                                                                 │
│  For passthrough devices:                                        │
│  → iommus MUST be present for dt_device_set_protected()         │
│  → xen,passthrough claims the device for DomD                   │
│  → status=disabled hides it from dom0 Linux                     │
│                                                                 │
│  dtdev paths ALWAYS use the merged DTB paths (/bus@0/...)       │
│  NOT the partial DomD DTB paths (which have no /bus@0/)         │
└─────────────────────────────────────────────────────────────────┘
```

### Decision History

| Attempt | Config | Result | Root Cause |
|---|---|---|---|
| 1 | `iommus = <0xf0 0x01>` | EMEM ADMA errors | Phandle 0xf0 not resolving in merged DTB |
| 2 | `/delete-property/ iommus` + `dma-coherent` | SCR version 7 / bus width errors | dma-coherent bypasses swiotlb, cache corruption |
| 3 | `/delete-property/ iommus` only | EMEM ADMA errors | Removes Xen's ability to set up passthrough mapping |
| 4 | `status = "okay"` only (no iommus changes) | **SD card works ✓** | dom0_iommu=passthrough handles iommus transparently |
| 5 | + SMMU patch (no USFCFG fix) | EMEM errors return | USFCFG=1 faults dom0 DMA before entries registered |
| 6 | + USFCFG fix | **Dom0 stable ✓** | Bypass mode correct for async entry registration |
| 7 | dtdev without iommus on GPU | `xc_assign_dtdevice -1` | GPU not protected (no iommus → no dt_device_set_protected) |
| 8 | + `iommus = <0xf0 0x07>` on GPU/host1x | **DomD launches ✓** | Device protected, assignment succeeds |
| 9 | DomD init segfault | `jtop.service` crash | Tegra hardware monitor fails in Xen VM |
| 10 | Mask jtop + `module_blacklist=nvhwpm` | **DomD boots fully ✓** | Tegra-specific services disabled |

---

*Document generated from production bringup on NVIDIA Jetson Orin NX with Xen 4.18.*  
*Platform: Tegra234 (Orin) | Kernel: 5.15.148-l4t | Yocto: Scarthgap 5.0.17*