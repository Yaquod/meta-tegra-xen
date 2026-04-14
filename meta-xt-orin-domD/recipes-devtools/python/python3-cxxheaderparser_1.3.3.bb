SUMMARY = "A pure python C++ header parser that parses C++ headers in a lightly structured manner."
HOMEPAGE = "https://github.com/robotpy/cxxheaderparser"
LICENSE = "BSD-3-Clause"

LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/BSD-3-Clause;md5=550794465ba0ec5312d6919e203a55f9"

inherit pypi python_setuptools_build_meta

PYPI_PACKAGE = "cxxheaderparser"
SRC_URI[md5sum] = "7fe5dfb330142d62ca36c11a378bf25f"
SRC_URI[sha256sum] = "2eb289b569f75eed73408cb1906218dc56653e66ba3666f914ffe127d35f4182"

BBCLASSEXTEND = "native"
