import argparse
import json
import os
import sys
import threading
import time

# Import Config
import config
from transforms import transform_point, calculate_content_bounds, calculate_alignment_offset

# Try to import real pyaxidraw, fallback to mock if missing or flag set
try:
    from pyaxidraw import axidraw
except ImportError:
    axidraw = None

from mock_axidraw import AxiDraw as MockAxiDraw

def load_json(path):
    with open(path, 'r') as f:
        return json.load(f)

def validate_command_file(data):
    """Validate the JSON command file structure before plotting.
    Raises ValueError with a descriptive message on invalid input."""
    if not isinstance(data, dict):
        raise ValueError("Root must be a JSON object")
    if 'layers' not in data:
        raise ValueError("Missing required 'layers' array")
    if not isinstance(data['layers'], list):
        raise ValueError("'layers' must be an array")

    for i, layer in enumerate(data['layers']):
        prefix = f"layers[{i}]"
        if not isinstance(layer, dict):
            raise ValueError(f"{prefix}: layer must be an object")
        for field in ('id', 'stationId', 'commands'):
            if field not in layer:
                raise ValueError(f"{prefix}: missing required field '{field}'")
        if not isinstance(layer['commands'], list):
            raise ValueError(f"{prefix}.commands: must be an array")

        for j, cmd in enumerate(layer['commands']):
            cmd_prefix = f"{prefix}.commands[{j}]"
            if not isinstance(cmd, dict):
                raise ValueError(f"{cmd_prefix}: command must be an object")
            if 'op' not in cmd:
                raise ValueError(f"{cmd_prefix}: missing required field 'op'")

            op = cmd['op']
            if op == 'MOVE':
                for field in ('x', 'y'):
                    if field not in cmd:
                        raise ValueError(f"{cmd_prefix}: MOVE missing '{field}'")
            elif op == 'DRAW':
                if 'points' not in cmd:
                    raise ValueError(f"{cmd_prefix}: DRAW missing 'points'")
                if not isinstance(cmd['points'], list):
                    raise ValueError(f"{cmd_prefix}: DRAW 'points' must be an array")
                for k, pt in enumerate(cmd['points']):
                    for field in ('x', 'y'):
                        if field not in pt:
                            raise ValueError(f"{cmd_prefix}.points[{k}]: missing '{field}'")
            elif op == 'REFILL':
                if 'stationId' not in cmd:
                    raise ValueError(f"{cmd_prefix}: REFILL missing 'stationId'")
            else:
                raise ValueError(f"{cmd_prefix}: unknown op '{op}' (expected MOVE, DRAW, or REFILL)")

    return True

def perform_refill(ad, station_id):
    station = config.STATIONS.get(station_id)
    if not station:
        print(f"Warning: Unknown station '{station_id}'. Using default.")
        station = config.STATIONS.get("default_station")

    print(f"--- REFILLING at {station_id} ({station['x']} mm / {station['y']} mm) ---")

    # 1. Move to station
    ad.moveto(station['x'], station['y'])

    # 2. Dip
    print(f"  > Dipping into {station_id}...")
    ad.pendown()
    time.sleep(0.5) # Wait for ink
    ad.penup()

    # 3. Wiggle/Swirl if needed
    if station.get('behavior') == 'dip_swirl':
        print("  > Swirling brush...")
        ad.pendown()
        ad.lineto(station['x'] + 2, station['y'])
        ad.lineto(station['x'] - 2, station['y'])
        ad.moveto(station['x'], station['y'])
        ad.penup()

    print("--- Refill Complete ---")

def execute_layer(ad, layer, report_pos=False, verbose=False, model=1, invert_x=False, invert_y=False, swap_xy=False, offset_x=0, offset_y=0, width=None, height=None, data_rotation=0, content_bounds=None, debug_position=False, flip_y=False, realtime_position=False, start_event=None):
    print(f"\n=== Starting Layer: {layer['id']} (Station: {layer['stationId']}) ===")
    # Wait for the operator to confirm the layer. When a control thread owns
    # stdin (so it can also receive live SPEED commands), block on the event it
    # sets instead of reading stdin here. Fall back to input() for plain CLI use.
    print("Press Enter to start this layer (Ensure correct paint is ready)...")
    sys.stdout.flush()
    if start_event is not None:
        start_event.wait()
        start_event.clear()
    else:
        input()

    # Model 1 (V3 A4): X ~300, Y ~218
    # Model 2 (V3 A3): X ~430, Y ~297
    maxX = width if width else (300.0 if model == 1 else 430.0)
    maxY = height if height else (215.0 if model == 1 else 297.0)

    oob_count = [0]

    def soft_clamp(x, y):
        cx = max(0.0, min(x, maxX))
        cy = max(0.0, min(y, maxY))
        if cx != x or cy != y:
            oob_count[0] += 1
            print(f"  [SOFT LIMIT] Clamped ({x:.2f}, {y:.2f}) -> ({cx:.2f}, {cy:.2f})  "
                  f"(bed: {maxX:.0f}x{maxY:.0f})")
        return cx, cy

    def do_transform(tx, ty):
        return transform_point(tx, ty,
                               swap_xy=swap_xy, invert_x=invert_x, invert_y=invert_y,
                               max_x=maxX, max_y=maxY,
                               data_rotation=data_rotation, content_bounds=content_bounds)

    def check_position(expected_x, expected_y):
        if not debug_position:
            return
        actual = ad.query_position()
        if actual is None:
            return
        ax, ay = actual
        dx, dy = ax - expected_x, ay - expected_y
        flag = " *** DRIFT" if abs(dx) > 0.5 or abs(dy) > 0.5 else ""
        print(f"  [POS] cmd=({expected_x:.2f}, {expected_y:.2f})  hw=({ax:.2f}, {ay:.2f})  delta=({dx:+.2f}, {dy:+.2f}){flag}")

    commands = layer['commands']
    for cmd in commands:
        op = cmd['op']

        if op == "MOVE":
            px, py = do_transform(cmd['x'], cmd['y'])
            px += offset_x
            py += offset_y
            if flip_y:
                py = maxY - py
            px, py = soft_clamp(px, py)

            print(f"  [MOVE] To ({px:.2f}, {py:.2f}) [Orig: ({cmd['x']}, {cmd['y']})]")
            ad.moveto(px, py)
            check_position(px, py)
            # When the backend reports realtime hardware position, let that
            # drive the cursor instead of the commanded target (which on
            # buffered controllers like GRBL runs ahead of the actual pen).
            if report_pos and not realtime_position:
                print(f"POS:X:{px}:Y:{py}")
                sys.stdout.flush()

        elif op == "DRAW":
            print(f"  [DRAW] Polyline with {len(cmd['points'])} points")
            points = cmd['points']
            for p in points:
                px, py = do_transform(p['x'], p['y'])
                px += offset_x
                py += offset_y
                if flip_y:
                    py = maxY - py
                px, py = soft_clamp(px, py)

                if verbose:
                    print(f"    -> Lineto ({px:.2f}, {py:.2f})")

                ad.lineto(px, py)
                check_position(px, py)
                if report_pos and not realtime_position:
                    print(f"POS:X:{px}:Y:{py}")
                    sys.stdout.flush()

        elif op == "REFILL":
            perform_refill(ad, cmd['stationId'])

    if oob_count[0] > 0:
        print(f"  [WARNING] {oob_count[0]} point(s) were clamped to machine bounds ({maxX:.0f}x{maxY:.0f}mm)")
    print(f"=== Layer {layer['id']} Complete ===")

def load_station_config(custom_path=None):
    # Look for config.json first (New Format), then stations.json (Old Format)
    files_to_check = []

    if custom_path:
        files_to_check.append(custom_path)

    # Default Paths
    files_to_check.append("config.json")
    files_to_check.append(os.path.join(os.path.dirname(__file__), "config.json"))

    for p in files_to_check:
        if os.path.exists(p):
            print(f"INFO: Loading Configuration from {p}")
            try:
                with open(p, 'r') as f:
                    data = json.load(f)

                    # 1. Load Stations
                    stations_data = data
                    if 'stations' in data:
                         stations_data = data['stations']

                    # Merge into config.STATIONS or replace
                    for k, v in stations_data.items():
                        config.STATIONS[k] = v

                    # 2. Return General Settings if present
                    if 'general' in data:
                        return data['general']

                return {} # Return empty dict if no general settings found but file loaded
            except Exception as e:
                print(f"ERROR: Failed to load config {p}: {e}")

    # Fallback to legacy stations.json
    paths = ["stations.json", os.path.join(os.path.dirname(__file__), "stations.json")]
    for p in paths:
        if os.path.exists(p):
            print(f"INFO: Loading Station Configuration from {p}")
            try:
                with open(p, 'r') as f:
                    data = json.load(f)
                    for k, v in data.items():
                        config.STATIONS[k] = v
                return
            except Exception as e:
                print(f"ERROR: Failed to load stations.json: {e}")

def _create_backend(args, loaded_general):
    if args.mock:
        print("INFO: Force Mock Mode selected.")
        return MockAxiDraw()

    if args.backend == 'gcode':
        from gcode_backend import GcodeBackend
        gcode_cfg = loaded_general.get('gcode', {}) if loaded_general else {}
        if args.serial_port:
            gcode_cfg['serial_port'] = args.serial_port
        if args.machine_width:
            gcode_cfg['machine_width'] = args.machine_width
        if args.machine_height:
            gcode_cfg['machine_height'] = args.machine_height
        print(f"INFO: Initializing G-code backend (port: {gcode_cfg.get('serial_port', '/dev/ttyUSB0')})")
        return GcodeBackend(gcode_config=gcode_cfg)

    if axidraw is None:
        print("WARNING: pyaxidraw not found. Falling back to MOCK AxiDraw.")
        return MockAxiDraw()

    print("INFO: Initializing REAL AxiDraw...")
    from axidraw_backend import AxiDrawBackend
    return AxiDrawBackend(axidraw)


def main():
    parser = argparse.ArgumentParser(description='Watercolor Driver')
    parser.add_argument('input', nargs='?', help='Input JSON file (optional if --manual-pen used)')
    parser.add_argument('--invert-x', action='store_true', help='Invert X Axis (Mirror Plot) for standard SVGs')
    parser.add_argument('--invert-y', action='store_true', help='Invert Y Axis (Mirror Plot)')
    parser.add_argument('--swap-xy', action='store_true', help='Swap X and Y Axis')
    parser.add_argument('--mock', action='store_true', help='Force Mock Mode')
    parser.add_argument('--verbose', action='store_true', help='Enable verbose logging')
    parser.add_argument('--model', type=int, default=None, help='AxiDraw Model (1=A4, 2=A3/XL)')
    parser.add_argument('--speed-down', type=int, default=None, help='Pen Down Speed (1-100)')
    parser.add_argument('--speed-up', type=int, default=None, help='Pen Up Speed (1-100)')
    parser.add_argument('--pen-up', type=int, default=None, help='Pen Up Height (0-100)')
    parser.add_argument('--pen-down', type=int, default=None, help='Pen Down Height (0-100)')
    parser.add_argument('--manual-pen', choices=['UP', 'DOWN'], help='Manually move pen and exit')
    parser.add_argument('--move-x', type=float, help='Manual Move X (mm)')
    parser.add_argument('--move-y', type=float, help='Manual Move Y (mm)')
    parser.add_argument('--interactive-server', action='store_true', help='Run in persistent server mode for manual control')
    parser.add_argument('--report-position', action='store_true', help='Report realtime position for GUI')
    parser.add_argument('--config', help='Path to custom configuration file')
    parser.add_argument('--backend', choices=['axidraw', 'gcode'], default='axidraw',
                        help='Plotter backend (axidraw or gcode)')
    parser.add_argument('--serial-port', help='Serial port for gcode backend')
    parser.add_argument('--machine-width', type=float, default=None, help='Machine width in mm (gcode)')
    parser.add_argument('--machine-height', type=float, default=None, help='Machine height in mm (gcode)')
    parser.add_argument('--machine-origin', choices=['top-left', 'top-right', 'bottom-left', 'bottom-right'],
                        help='Machine origin corner (derives --invert-x, --invert-y, --origin-right)')

    # Canvas Alignment Args
    parser.add_argument('--canvas-align', choices=['top-left', 'top-right', 'bottom-left', 'bottom-right', 'center'],
                        help='Auto-calculate offset to align content on canvas')
    parser.add_argument('--origin-right', action='store_true', help='Machine Origin is Top-Right (0,0 is Right). Swaps Left/Right alignment targets.')
    parser.add_argument('--data-rotation', type=int, default=0, choices=[0, 90, 180, 270], help='Rotate drawing data (degrees CCW)')
    parser.add_argument('--padding-x', type=float, default=0.0, help='Padding X (mm) for alignment')
    parser.add_argument('--padding-y', type=float, default=0.0, help='Padding Y (mm) for alignment')
    parser.add_argument('--debug-position', action='store_true', help='Query and log actual hardware position after moves')
    parser.add_argument('--portrait', action='store_true', help='Portrait mode: auto-swaps axes and adjusts inversion for vertical/horizontal mapping')
    parser.add_argument('--flip-y', action='store_true', help='Flip Y axis direction (for CNC machines where Y+ goes up)')

    args = parser.parse_args()

    # Load Station Config Early
    loaded_general = load_station_config(getattr(args, 'config', None))

    # Apply config with clear precedence: CLI explicit (not None) > config file > hardcoded default
    def resolve(cli_val, config_key, hardcoded_default, transform=None):
        """Return CLI value if explicitly set, else config value if present, else hardcoded default."""
        if cli_val is not None:
            return cli_val
        if loaded_general and config_key in loaded_general:
            val = loaded_general[config_key]
            return transform(val) if transform else val
        return hardcoded_default

    args.model = resolve(args.model, 'modelIndex', 1, lambda v: v + 1)
    args.speed_down = resolve(args.speed_down, 'speedDown', 25)
    args.speed_up = resolve(args.speed_up, 'speedUp', 75)
    args.pen_up = resolve(args.pen_up, 'penUp', None)
    args.pen_down = resolve(args.pen_down, 'penDown', None)

    # Resolve machine origin from CLI or config (derives invert/origin flags)
    machine_origin = args.machine_origin
    if not machine_origin and loaded_general:
        mo = loaded_general.get('machineOrigin', '')
        if mo:
            machine_origin = mo.lower()
    if machine_origin:
        args.invert_x = 'right' in machine_origin
        args.invert_y = 'bottom' in machine_origin
        args.origin_right = 'right' in machine_origin

    # Boolean flags: CLI --flag is explicit True; config can set True; default is False
    if loaded_general:
        if not args.mock and loaded_general.get('mock', False): args.mock = True
        if not machine_origin:
            if not args.invert_x and loaded_general.get('invertX', False): args.invert_x = True
            if not args.invert_y and loaded_general.get('invertY', False): args.invert_y = True
        if not args.swap_xy and loaded_general.get('swapXY', False): args.swap_xy = True

    # Portrait mode: motor X is vertical, motor Y is horizontal.
    # Swap which axis gets inverted and toggle swap.
    # Applied AFTER config resolution so portrait has the final say.
    if args.portrait:
        args.invert_x, args.invert_y = args.invert_y, args.invert_x
        args.swap_xy = not args.swap_xy

    print(f"INFO: Active Configuration -> Model: {args.model}, Mock: {args.mock}, InvertX: {args.invert_x}, InvertY: {args.invert_y}, SwapXY: {args.swap_xy}, FlipY: {args.flip_y}")

    # Resolve backend from config if not set on CLI
    if args.backend == 'axidraw' and loaded_general:
        args.backend = loaded_general.get('backend', 'axidraw')

    # Initialize Driver
    ad = _create_backend(args, loaded_general)

    # Official pyaxidraw pattern: interactive() -> set options -> connect()
    # Options set before connect() are auto-applied on connection.
    print("INFO: Entering Interactive Mode...")
    ad.interactive()

    # Set all options BEFORE connect() so they auto-apply
    ad.options.units = 2
    if args.backend != 'gcode':
        ad.options.pen_pos_up = config.PEN_HEIGHTS["UP"]
        ad.options.pen_pos_down = config.PEN_HEIGHTS["DOWN"]

        if args.pen_up is not None:
            print(f"INFO: Overriding Pen Up -> {args.pen_up}%")
            ad.options.pen_pos_up = args.pen_up
        if args.pen_down is not None:
            print(f"INFO: Overriding Pen Down -> {args.pen_down}%")
            ad.options.pen_pos_down = args.pen_down
        try:
            ad.options.model = args.model
        except Exception as e:
            print(f"WARNING: Could not set model option: {e}")

        print(f"INFO: Setting Speeds -> Draw: {args.speed_down}%, Travel: {args.speed_up}%")
        ad.options.speed_pendown = args.speed_down
        ad.options.speed_penup = args.speed_up

    print(f"INFO: Active Options -> Units: {ad.options.units} (mm), Model: {args.model}")

    # Connect - options set above are auto-applied by pyaxidraw
    print("INFO: Connecting to plotter...")
    connected = ad.connect()

    if not connected:
        print("ERROR: Could not connect to plotter! Check USB connection and power.")
        if not args.mock:
            print("       Exiting...")
            sys.exit(1)
        else:
            print("       Continuing because we are in Mock mode (or fell back to it).")

    print("INFO: Connection Successful.")

    # Realtime position reporting: if the backend can stream the actual
    # hardware position (e.g. GRBL '?' status polling), register a callback
    # that emits POS lines for the GUI. This supersedes the per-waypoint
    # commanded positions, which on buffered controllers run ahead of the pen.
    realtime_position = False
    if args.report_position and hasattr(ad, 'set_position_callback'):
        def _emit_position(x, y):
            print(f"POS:X:{x}:Y:{y}")
            sys.stdout.flush()
        ad.set_position_callback(_emit_position)
        realtime_position = True
        print("INFO: Realtime position reporting enabled.")

    # Live speed feedback: emit the active feed-rate override percent for the GUI.
    if args.report_position and hasattr(ad, 'set_speed_callback'):
        def _emit_speed(percent):
            print(f"SPEED:{percent}")
            sys.stdout.flush()
        ad.set_speed_callback(_emit_speed)

    print(f"INFO: Verify options after connect -> units={ad.options.units}, "
          f"speed_pendown={ad.options.speed_pendown}, speed_penup={ad.options.speed_penup}, "
          f"pen_pos_up={ad.options.pen_pos_up}, pen_pos_down={ad.options.pen_pos_down}")

    if args.debug_position:
        pos = ad.query_position()
        if pos:
            print(f"INFO: [POSCHECK] Initial position: ({pos[0]:.2f}, {pos[1]:.2f}) mm")
        else:
            print("INFO: [POSCHECK] Position query not supported by this backend")

    # Safety: Always raise pen on connect/start
    print("INFO: Safely raising pen...")
    ad.penup()

    # Manual Mode Check
    if args.manual_pen:
        print(f"INFO: Manual Pen Mode -> {args.manual_pen}")
        if args.manual_pen == 'DOWN':
            ad.pendown()
        else:
            ad.penup()

        print("INFO: Manual operation complete. Exiting.")
        ad.disconnect()
        return

    # Manual Move Check
    if args.move_x is not None or args.move_y is not None:
        mx = args.move_x if args.move_x is not None else 0.0
        my = args.move_y if args.move_y is not None else 0.0
        print(f"INFO: Manual Move -> X: {mx} mm, Y: {my} mm")

        ad.moveto(mx, my)

        print("INFO: Manual move complete. Exiting.")
        ad.disconnect()
        return

    # Interactive Server Mode
    if args.interactive_server:
        print("INFO: Starting Interactive Server Mode...")
        print("SERVER_READY")
        sys.stdout.flush()

        try:
            while True:
                line = sys.stdin.readline()
                if not line:
                    break
                line = line.strip()
                if not line:
                    continue

                parts = line.split()
                cmd = parts[0].upper()

                if cmd == "MOVE":
                    try:
                        dx = float(parts[1])
                        dy = float(parts[2])

                        if args.swap_xy:
                            dx, dy = dy, dx

                        print(f"INFO: Moving relative ({dx}, {dy})")
                        ad.move(dx, dy)
                        print("OK")
                    except ValueError:
                        print(f"ERR Invalid Number Format: {parts}")
                    except Exception as e:
                        print(f"ERR {e}")

                elif cmd == "PEN":
                    try:
                        sub = parts[1].upper()
                        if sub == "UP":
                            print("INFO: Pen UP")
                            ad.penup()
                            print("OK")
                        elif sub == "DOWN":
                            print("INFO: Pen DOWN")
                            ad.pendown()
                            print("OK")
                        else:
                            print(f"ERR Invalid Pen Command: {sub}")
                    except IndexError:
                        print("ERR Missing Pen Argument")
                    except Exception as e:
                        print(f"ERR {e}")

                elif cmd == "RAW":
                    try:
                        raw_cmd = line[4:].strip()
                        if not raw_cmd:
                            print("ERR Missing G-code command")
                        else:
                            print(f"INFO: Sending raw: {raw_cmd}")
                            responses = ad.send_raw(raw_cmd)
                            for resp in responses:
                                print(f"GRBL> {resp}")
                                sys.stdout.flush()
                            print("OK")
                    except Exception as e:
                        print(f"ERR {e}")

                elif cmd == "EXIT":
                    break
                else:
                    print(f"ERR Unknown command: {cmd}")

                sys.stdout.flush()

        except KeyboardInterrupt:
            pass

        print("INFO: Interactive Server Exiting.")
        ad.disconnect()
        return


    if args.input:
        data = load_json(args.input)
        try:
            validate_command_file(data)
            print(f"INFO: Command file validated ({len(data['layers'])} layers)")
        except ValueError as e:
            print(f"ERROR: Invalid command file: {e}")
            ad.disconnect()
            sys.exit(1)
        print(f"INFO: Plot Configuration -> Invert X: {args.invert_x}, Invert Y: {args.invert_y}, Swap X/Y: {args.swap_xy}")
    elif not args.interactive_server and not args.manual_pen:
        print("ERROR: No input file specified for plot mode.")
        sys.exit(1)

    try:
        print(f"DEBUG: ad.options.units = {ad.options.units} (Should be 2 for mm)")

        # Calculate Content Bounds & Offset if Alignment Requested
        offset_x = 0
        offset_y = 0
        # Machine dimensions: CLI > gcode config > model defaults
        if args.machine_width:
            machine_w = args.machine_width
        elif args.backend == 'gcode' and hasattr(ad.options, 'machine_width'):
            machine_w = ad.options.machine_width
        else:
            machine_w = 300.0 if args.model == 1 else 430.0
        if args.machine_height:
            machine_h = args.machine_height
        elif args.backend == 'gcode' and hasattr(ad.options, 'machine_height'):
            machine_h = ad.options.machine_height
        else:
            machine_h = 215.0 if args.model == 1 else 297.0
        print(f"INFO: Machine Dimensions -> {machine_w:.0f} x {machine_h:.0f} mm")

        min_x, min_y, max_x, max_y, has_points = calculate_content_bounds(data['layers'])

        if args.canvas_align and has_points:
            content_bounds = {'minX': min_x, 'maxX': max_x, 'minY': min_y, 'maxY': max_y}
            offset_x, offset_y = calculate_alignment_offset(
                canvas_align=args.canvas_align,
                content_bounds=content_bounds,
                machine_w=machine_w, machine_h=machine_h,
                swap_xy=args.swap_xy, invert_x=args.invert_x, invert_y=args.invert_y,
                data_rotation=args.data_rotation, origin_right=args.origin_right,
                padding_x=args.padding_x, padding_y=args.padding_y
            )

        # Build content bounds dict for execute_layer
        content_bounds = {'minX': min_x, 'maxX': max_x, 'minY': min_y, 'maxY': max_y} if has_points else None

        # Pre-flight bounds check: transform all four content corners and check against machine limits
        if has_points:
            corners = [(min_x, min_y), (max_x, min_y), (min_x, max_y), (max_x, max_y)]
            extremes_x, extremes_y = [], []
            for cx, cy in corners:
                tx, ty = transform_point(cx, cy,
                    swap_xy=args.swap_xy, invert_x=args.invert_x, invert_y=args.invert_y,
                    max_x=machine_w, max_y=machine_h,
                    data_rotation=args.data_rotation, content_bounds=content_bounds)
                tx += offset_x
                ty += offset_y
                if args.flip_y:
                    ty = machine_h - ty
                extremes_x.append(tx)
                extremes_y.append(ty)
            plot_min_x, plot_max_x = min(extremes_x), max(extremes_x)
            plot_min_y, plot_max_y = min(extremes_y), max(extremes_y)
            print(f"INFO: Plot bounds -> X: {plot_min_x:.1f} to {plot_max_x:.1f}, Y: {plot_min_y:.1f} to {plot_max_y:.1f}")
            oob = False
            if plot_min_x < -0.5 or plot_max_x > machine_w + 0.5:
                print(f"WARNING: Plot X range [{plot_min_x:.1f}, {plot_max_x:.1f}] exceeds machine width {machine_w:.0f}mm!")
                oob = True
            if plot_min_y < -0.5 or plot_max_y > machine_h + 0.5:
                print(f"WARNING: Plot Y range [{plot_min_y:.1f}, {plot_max_y:.1f}] exceeds machine height {machine_h:.0f}mm!")
                oob = True
            if oob:
                print("WARNING: Some coordinates will be clamped to machine bounds. Drawing may be clipped.")

        # Control channel: a single thread owns stdin during the plot so it can
        # receive live "SPEED UP/DOWN/RESET" commands while also releasing the
        # per-layer start gate (any other line, e.g. the GUI's Enter). This
        # avoids two readers competing for stdin.
        layer_start_event = threading.Event()

        def control_loop():
            for raw_line in sys.stdin:
                line = raw_line.strip()
                if line.upper().startswith("SPEED"):
                    parts = line.split()
                    direction = parts[1].lower() if len(parts) > 1 else "reset"
                    if hasattr(ad, 'adjust_speed'):
                        ad.adjust_speed(direction)
                        print(f"INFO: Speed {direction}")
                    else:
                        print("INFO: Live speed control is not supported on this backend.")
                    sys.stdout.flush()
                else:
                    layer_start_event.set()

        control_thread = threading.Thread(target=control_loop, daemon=True)
        control_thread.start()

        for layer in data['layers']:
            execute_layer(ad, layer, report_pos=args.report_position, verbose=args.verbose, model=args.model, invert_x=args.invert_x, invert_y=args.invert_y, swap_xy=args.swap_xy, offset_x=offset_x, offset_y=offset_y, width=machine_w, height=machine_h, data_rotation=args.data_rotation, content_bounds=content_bounds, debug_position=args.debug_position, flip_y=args.flip_y, realtime_position=realtime_position, start_event=layer_start_event)

        # Return to home
        print("\nPlot Complete. Returning Home.")
        ad.moveto(0, 0)
        ad.disconnect()

    except KeyboardInterrupt:
        print("\nAborted by User.")
        ad.penup()
        ad.disconnect()

if __name__ == "__main__":
    main()
