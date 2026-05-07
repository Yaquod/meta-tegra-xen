# DomD Current Status & Device Tree Hacks

This document outlines the current working status of the Driver Domain (`domD`) in our Yocto meta-layer, focusing specifically on the device tree manipulation ("hacks") required in both `dom0` and `domD` to successfully pass through the GPU and its supporting hardware.

## 1. Overview and Features Enabled

Currently, `domD` operates as a privileged driver domain for graphical and hardware-accelerated workloads on the NVIDIA Jetson Orin platform (Tegra234). 

**Working Features:**
- **Direct GPU Passthrough (ga10b)**: Full near-native access to the Orin Nano's GPU.
- **Hardware SMMU Translation**: SMMU Isolation works—mapping specific Stream IDs (SIDs) to `domD` context banks safely.
- **host1x Command Submission Hub**: Fully functional host1x subsystem passed through for hardware channel arbitration and syncpoint management.
- **Out-of-band Clock Management**: `domD` gracefully controls GPU/Host1x clocks without breaking Linux CCF (Common Clock Framework) by relying on the BPMP IPC channel out-of-band rather than standard DT clock references.

---

## 2. Dom0 Device Tree Hacks

To allow Xen to assign devices to `domD`, `dom0` must first relinquish control over them. This is achieved via a dedicated Device Tree overlay applied at build/boot time: `tegra234-orin-nano-xen.dts`.

### Key Manipulations in `dom0` DTB:
1. **Disabling Target Devices**: 
   The overlay targets the GPU (`/bus@0/gpu@17000000`), USB (`usb@3610000`), and the `host1x` hub (`/bus@0/host1x@13e00000`). It forcibly sets `status = "disabled";`.
   
2. **Applying `xen,passthrough`**:
   Any node assigned to `domD` is tagged with the boolean property `xen,passthrough;`. Xen reads this to know that even though `dom0` is skipping the device, the physical memory regions must be protected/routed by the hypervisor.

3. **Pruning `host1x` Children**:
   Because `host1x` acts as a parent bus to multimedia hardware (NVDEC, VIC, NVJPG, NVDLA, ISP, etc.), if `dom0` does not disable them, its kernel will try to probe those engines while lacking control of the parent hub. Over a dozen `fragment` blocks exist specifically to disable children of `/bus@0/host1x@13e00000`.

---

## 3. DomD Partial Device Tree Status

Xen expects `domD` to be booted with a partial, localized Device Tree that defines **only** the virtualized interrupt controllers, and the physical MMIO devices it owns. This is defined in `tegra234-domd.dts`.

### Key Manipulations in `domD` DTB:
1. **SMMU (NISO1) Mapping (`iommu@8000000`)**:
   Standard kernels try to bind generic IOMMU drivers. We pass `smmu_niso1` manually, assigning it a local aliases `phandle` (e.g. `0x02`), mapping its split registers, and giving `domD` limited context bank access. The `nvidia,memory-controller` is stripped manually, as it's not passed to `domD`.

2. **GPU Node (`gpu@17000000`)**:
   - Pulled directly from the base DTS, but stripped of `xen,passthrough`.
   - `iommu-map-mask = <0x00>;` is injected. This is a critical hack that prevents the `domD` Linux kernel from trying to aggressively remap or configure a second SMMU context bank on top of the one Xen already secured hardware-side.
   - Clock, power, and reset `phandles` pointing to BPMP/CMU are stripped. Since BPMP is **not** passed through to `domD`, keeping them would cause endless deferred probes. `nvgpu` happily fetches clocks over BPMP IPC natively anyway.

3. **Host1x Hub (`host1x@13e00000`)**:
   - Requires manual translation of `iommu-map` entries. 
   - `dom0`'s Host1x maps multiple SMMUs (`niso0` and `niso1`). `domD` strips out any mapping referring to `smmu_niso0` (SID `0x04` etc.) because that SMMU belongs to `dom0` (used for GPCDMA). Only `niso1` references (SID `0x27`) are retained to prevent SMMU access violations.

## Conclusion

Combining the teardown done in the `dom0` overlay with the extremely selective device reconstruction in `tegra234-domd.dts` allows the NVIDIA stack to initialize normally inside `domD`. Future work will expand these hacks to dynamically map Unprivileged Domains (`domU`) and safely broker hardware slices.