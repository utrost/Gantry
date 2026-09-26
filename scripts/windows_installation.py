"""Read native MSI product scope rather than inferring it from ARP registry views."""


def installed_products():
    import ctypes
    from ctypes import wintypes
    msi = ctypes.WinDLL('msi')
    enum = msi.MsiEnumProductsExW
    enum.argtypes = [wintypes.LPCWSTR, wintypes.LPCWSTR, wintypes.DWORD, wintypes.DWORD,
                     wintypes.LPWSTR, ctypes.POINTER(wintypes.DWORD), wintypes.LPWSTR,
                     ctypes.POINTER(wintypes.DWORD)]
    enum.restype = wintypes.UINT
    info = msi.MsiGetProductInfoExW
    info.argtypes = [wintypes.LPCWSTR, wintypes.LPCWSTR, wintypes.DWORD, wintypes.LPCWSTR,
                     wintypes.LPWSTR, ctypes.POINTER(wintypes.DWORD)]
    info.restype = wintypes.UINT

    def property_value(code, sid, context, name):
        size = wintypes.DWORD(0)
        status = info(code, sid, context, name, None, ctypes.byref(size))
        if status not in (0, 234):
            # Advertised products need not have installed-product properties.
            if status == 1605:
                return ''
            raise OSError(status, f'MsiGetProductInfoExW: {name}')
        value = ctypes.create_unicode_buffer(size.value + 1)
        size.value += 1
        status = info(code, sid, context, name, value, ctypes.byref(size))
        if status:
            raise OSError(status, f'MsiGetProductInfoExW: {name}')
        return value.value

    products = []
    index = 0
    while True:
        code = ctypes.create_unicode_buffer(39)
        context = wintypes.DWORD()
        sid = ctypes.create_unicode_buffer(2048)
        size = wintypes.DWORD(len(sid))
        # Context 7 includes current-user managed/unmanaged and machine installations.
        status = enum(None, None, 7, index, code, ctypes.byref(context), sid, ctypes.byref(size))
        if status == 259:
            break
        if status:
            raise OSError(status, 'MsiEnumProductsExW')
        user = sid.value if context.value != 4 else None
        name = property_value(code.value, user, context.value, 'InstalledProductName')
        if name == 'Gantry':
            products.append({'code': code.value, 'context': context.value,
                             'version': property_value(code.value, user, context.value, 'VersionString')})
        index += 1
    return products


def per_user_version(products):
    if len(products) != 1:
        raise RuntimeError(f'Expected one installed Gantry product, found {products}')
    if products[0]['context'] not in (1, 2):
        raise RuntimeError(f'Gantry has machine scope according to Windows Installer: {products}')
    return products[0]['version']
