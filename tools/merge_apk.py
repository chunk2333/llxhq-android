#!/usr/bin/env python3
"""把 classes.dex 合并进 aapt2 链接出来的 APK（无 Gradle 构建链的一环）"""
import shutil
import sys
import zipfile


def main() -> int:
    if len(sys.argv) != 4:
        print("usage: merge_apk.py <base.apk> <classes.dex> <out.apk>")
        return 2
    base, dex, out = sys.argv[1], sys.argv[2], sys.argv[3]
    shutil.copyfile(base, out)
    with zipfile.ZipFile(out, "a", zipfile.ZIP_DEFLATED) as z:
        z.write(dex, "classes.dex")
    print("merged ->", out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
