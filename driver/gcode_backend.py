import time

from backend import PlotterBackend, BackendOptions


class GcodeOptions(BackendOptions):
    def __init__(self):
        super().__init__()
        self.serial_port = "/dev/ttyUSB0"
        self.baud_rate = 115200
        self.pen_mode = "servo"
        self.servo_pin = 0
        self.feed_rate_draw = 1000
        self.feed_rate_travel = 3000
        self.pen_servo_up = 60
        self.pen_servo_down = 30
        self.z_up = 5.0
        self.z_down = 0.0
        self.machine_width = 300.0
        self.machine_height = 200.0


class GcodeBackend(PlotterBackend):
    def __init__(self, gcode_config=None):
        self.options = GcodeOptions()
        if gcode_config:
            self._apply_config(gcode_config)
        self._serial = None
        self._pen_is_down = False

    def _apply_config(self, cfg):
        for key in ('serial_port', 'baud_rate', 'pen_mode', 'servo_pin',
                     'feed_rate_draw', 'feed_rate_travel',
                     'pen_servo_up', 'pen_servo_down',
                     'z_up', 'z_down', 'machine_width', 'machine_height'):
            if key in cfg:
                setattr(self.options, key, cfg[key])

    def interactive(self):
        pass

    def update(self):
        pass

    def connect(self) -> bool:
        try:
            import serial
            self._serial = serial.Serial(
                self.options.serial_port,
                self.options.baud_rate,
                timeout=5
            )
            time.sleep(2)
            # Read and display GRBL boot message
            while self._serial.in_waiting > 0:
                line = self._serial.readline().decode(errors='replace').strip()
                if line:
                    print(f"GRBL: {line}")
            # Unlock alarm state (GRBL boots in alarm on many setups)
            self._send("$X")
            self._wait_for_ok()
            self._send("G21")
            self._wait_for_ok()
            self._send("G90")
            self._wait_for_ok()
            # No homing/limit switches: define the pen's CURRENT position as the
            # work origin (0,0). After "$X" GRBL only clears the alarm; it does
            # not establish a known coordinate frame, so absolute plot moves
            # (G0/G1 under G90) would drive the head to arbitrary machine
            # coordinates and shoot off the bed. Zeroing here makes every
            # absolute move relative to where the user positioned the pen.
            # (Relative jog moves use G91 and are unaffected either way.)
            self._send("G92 X0 Y0")
            self._wait_for_ok()
            return True
        except Exception as e:
            print(f"ERROR: G-code connect failed: {e}")
            return False

    def disconnect(self):
        if self._serial and self._serial.is_open:
            self.penup()
            self._send("G0 X0 Y0")
            self._wait_for_ok()
            self._serial.close()
        self._serial = None

    def moveto(self, x, y):
        if self._pen_is_down:
            self.penup()
        feed = self.options.feed_rate_travel
        self._send(f"G0 X{x:.3f} Y{y:.3f} F{feed}")
        self._wait_for_ok()

    def lineto(self, x, y):
        if not self._pen_is_down:
            self.pendown()
        feed = self.options.feed_rate_draw
        self._send(f"G1 X{x:.3f} Y{y:.3f} F{feed}")
        self._wait_for_ok()

    def move(self, dx, dy):
        self._send("G91")
        self._wait_for_ok()
        feed = self.options.feed_rate_travel
        self._send(f"G1 X{dx:.3f} Y{dy:.3f} F{feed}")
        self._wait_for_ok()
        self._send("G90")
        self._wait_for_ok()

    def penup(self):
        self._pen_is_down = False
        mode = self.options.pen_mode
        if mode == "servo":
            self._send(f"M280 P{self.options.servo_pin} S{self.options.pen_servo_up}")
        elif mode == "zaxis":
            self._send(f"G0 Z{self.options.z_up:.2f}")
        elif mode == "m3m5":
            self._send(f"M3 S{int(self.options.pen_servo_up)}")
        self._wait_for_ok()

    def pendown(self):
        self._pen_is_down = True
        mode = self.options.pen_mode
        if mode == "servo":
            self._send(f"M280 P{self.options.servo_pin} S{self.options.pen_servo_down}")
        elif mode == "zaxis":
            self._send(f"G1 Z{self.options.z_down:.2f} F{self.options.feed_rate_draw}")
        elif mode == "m3m5":
            self._send(f"M3 S{int(self.options.pen_servo_down)}")
        self._wait_for_ok()
        time.sleep(0.15)

    def query_position(self):
        if not self._serial or not self._serial.is_open:
            return None
        self._serial.write(b"?")
        self._serial.flush()
        start = time.time()
        while time.time() - start < 2:
            if self._serial.in_waiting > 0:
                line = self._serial.readline().decode(errors='replace').strip()
                if line.startswith("<") and "MPos:" in line:
                    try:
                        mpos = line.split("MPos:")[1].split("|")[0]
                        parts = mpos.split(",")
                        return (float(parts[0]), float(parts[1]))
                    except (IndexError, ValueError):
                        return None
            else:
                time.sleep(0.01)
        return None

    def send_raw(self, command):
        if not self._serial or not self._serial.is_open:
            return ["(no serial connection)"]
        self._serial.write((command + "\n").encode())
        self._serial.flush()
        responses = []
        deadline = time.time() + 2.0
        while time.time() < deadline:
            if self._serial.in_waiting > 0:
                resp = self._serial.readline().decode(errors='replace').strip()
                if resp:
                    responses.append(resp)
                if resp.lower().startswith("ok") or resp.lower().startswith("error"):
                    break
            else:
                time.sleep(0.05)
        return responses if responses else ["(no response)"]

    def _send(self, cmd):
        if self._serial and self._serial.is_open:
            self._serial.write((cmd + "\n").encode())
            self._serial.flush()

    def _wait_for_ok(self, timeout=30):
        if not self._serial:
            return
        start = time.time()
        while time.time() - start < timeout:
            if self._serial.in_waiting > 0:
                line = self._serial.readline().decode(errors='replace').strip()
                if line.lower().startswith("ok"):
                    return
                if line.lower().startswith("error"):
                    print(f"WARNING: G-code error: {line}")
                    return
            else:
                time.sleep(0.01)
        print("WARNING: G-code response timeout")
