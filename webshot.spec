# -*- mode: python ; coding: utf-8 -*-


a = Analysis(
    ['webshot_standalone.py'],
    pathex=[],
    binaries=[],
    datas=[('/home/ace/miniconda3/envs/webshot/lib/python3.11/site-packages/playwright/driver', 'playwright/driver')],
    hiddenimports=['playwright.sync_api', 'playwright.async_api'],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
)
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name='webshot',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=True,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)
