"""
Shared coordinate transformation logic for the Watercolor Plotter system.

This module provides the single source of truth for coordinate transforms
used by both the driver execution and alignment bounds calculation.
"""


def transform_point(tx, ty, swap_xy=False, invert_x=False, invert_y=False,
                    max_x=0, max_y=0, data_rotation=0, content_bounds=None):
    """
    Transform a logical input coordinate to physical machine coordinates.

    Pipeline:
      1. Rotate around content center (if data_rotation != 0)
      2. Swap X/Y axes (if swap_xy)
      3. Invert X axis (if invert_x)
      4. Invert Y axis (if invert_y)

    Args:
        tx, ty: Input coordinates (mm)
        swap_xy: Swap X and Y axes
        invert_x: Mirror along X axis
        invert_y: Mirror along Y axis
        max_x: Machine width for inversion calculation
        max_y: Machine height for inversion calculation
        data_rotation: Rotation in degrees (0, 90, 180, 270)
        content_bounds: Dict with minX, maxX, minY, maxY for rotation center

    Returns:
        (px, py): Transformed physical coordinates
    """
    # 1. Apply Data Rotation (around content center)
    if data_rotation != 0 and content_bounds:
        cx = (content_bounds['minX'] + content_bounds['maxX']) / 2
        cy = (content_bounds['minY'] + content_bounds['maxY']) / 2
        dx, dy = tx - cx, ty - cy
        if data_rotation == 90:
            dx, dy = -dy, dx
        elif data_rotation == 180:
            dx, dy = -dx, -dy
        elif data_rotation == 270:
            dx, dy = dy, -dx
        tx, ty = dx + cx, dy + cy

    # 2. Swap if requested
    if swap_xy:
        px, py = ty, tx
    else:
        px, py = tx, ty

    # 3. Invert Physical X if requested
    if invert_x:
        px = max_x - px

    # 4. Invert Physical Y if requested
    if invert_y:
        py = max_y - py

    return px, py


def calculate_content_bounds(layers):
    """
    Scan all DRAW commands across all layers to find raw content bounds.

    Args:
        layers: List of layer dicts from the JSON command file

    Returns:
        (min_x, min_y, max_x, max_y, has_points) tuple
    """
    min_x, min_y = float('inf'), float('inf')
    max_x, max_y = float('-inf'), float('-inf')
    has_points = False

    for layer in layers:
        for cmd in layer['commands']:
            if cmd['op'] == 'DRAW':
                for p in cmd['points']:
                    px, py = p['x'], p['y']
                    has_points = True
                    min_x = min(min_x, px)
                    max_x = max(max_x, px)
                    min_y = min(min_y, py)
                    max_y = max(max_y, py)

    return min_x, min_y, max_x, max_y, has_points


def calculate_alignment_offset(canvas_align, content_bounds, machine_w, machine_h,
                               swap_xy=False, invert_x=False, invert_y=False,
                               data_rotation=0, origin_right=False,
                               padding_x=0, padding_y=0):
    """
    Calculate X/Y offset to align content on the machine canvas.

    Args:
        canvas_align: Alignment mode ('top-left', 'top-right', 'bottom-left', 'bottom-right', 'center')
        content_bounds: Dict with minX, maxX, minY, maxY
        machine_w, machine_h: Machine dimensions
        swap_xy, invert_x, invert_y: Transform flags
        data_rotation: Rotation degrees
        origin_right: Whether machine origin is top-right
        padding_x, padding_y: Padding in mm

    Returns:
        (offset_x, offset_y) tuple
    """
    min_x = content_bounds['minX']
    max_x = content_bounds['maxX']
    min_y = content_bounds['minY']
    max_y = content_bounds['maxY']

    # Transform corners to find physical bounds
    corners = [(min_x, min_y), (max_x, min_y), (min_x, max_y), (max_x, max_y)]
    t_corners = [
        transform_point(c[0], c[1],
                         swap_xy=swap_xy, invert_x=invert_x, invert_y=invert_y,
                         max_x=machine_w, max_y=machine_h,
                         data_rotation=data_rotation, content_bounds=content_bounds)
        for c in corners
    ]

    t_min_x = min(c[0] for c in t_corners)
    t_max_x = max(c[0] for c in t_corners)
    t_min_y = min(c[1] for c in t_corners)
    t_max_y = max(c[1] for c in t_corners)

    print(f"INFO: Content Physical Bounds: X[{t_min_x:.2f}, {t_max_x:.2f}], Y[{t_min_y:.2f}, {t_max_y:.2f}]")

    if origin_right:
        content_right_edge = t_min_x
        content_left_edge = t_max_x
        target_left = machine_w - padding_x
        target_right = 0 + padding_x
        print("INFO: Origin Right -> Content Right=MinX, Left=MaxX")
    else:
        content_left_edge = t_min_x
        content_right_edge = t_max_x
        target_left = 0 + padding_x
        target_right = machine_w - padding_x
        print("INFO: Origin Left -> Content Left=MinX, Right=MaxX")

    target_top = 0 + padding_y
    target_bottom = machine_h - padding_y

    offset_x = 0
    offset_y = 0

    if canvas_align == 'top-left':
        offset_x = target_left - content_left_edge
        offset_y = target_top - t_min_y
    elif canvas_align == 'top-right':
        offset_x = target_right - content_right_edge
        offset_y = target_top - t_min_y
    elif canvas_align == 'bottom-left':
        offset_x = target_left - content_left_edge
        offset_y = target_bottom - t_max_y
    elif canvas_align == 'bottom-right':
        offset_x = target_right - content_right_edge
        offset_y = target_bottom - t_max_y
    elif canvas_align == 'center':
        t_width = t_max_x - t_min_x
        t_height = t_max_y - t_min_y
        offset_x = (machine_w - t_width) / 2 - t_min_x
        offset_y = (machine_h - t_height) / 2 - t_min_y

    print(f"INFO: Alignment Offset -> X={offset_x:.2f}, Y={offset_y:.2f}")
    return offset_x, offset_y
