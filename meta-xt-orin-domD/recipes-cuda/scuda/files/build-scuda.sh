#!/bin/bash
# Build SCUDA server natively on DomD (Jetson Orin Nano with CUDA 12.6)
# Run once after first boot: build-scuda.sh

set -e

SCUDA_DIR="/opt/scuda"
BUILD_DIR="${SCUDA_DIR}/build"

echo "Building SCUDA server natively on DomD..."

# Run codegen
cd "${SCUDA_DIR}/codegen"
python3 ./codegen.py

# CMake build
cd "${SCUDA_DIR}"
mkdir -p "${BUILD_DIR}"
cmake -B "${BUILD_DIR}" \
    -DCMAKE_CUDA_ARCHITECTURES=87 \
    -DCMAKE_BUILD_TYPE=Release
cmake --build "${BUILD_DIR}" -j$(nproc)

# Install
install -m 0755 "${BUILD_DIR}"/server_* /usr/bin/scuda-server
echo "SCUDA server built and installed to /usr/bin/scuda-server"
echo "Enabling and starting scuda-server.service..."
systemctl enable scuda-server
systemctl start scuda-server
