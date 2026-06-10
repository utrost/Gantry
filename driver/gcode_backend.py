import queue
import sys
import threading
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
        # How often to poll GRBL for its realtime position (seconds)
        self.position_poll_interval = 0.1


class GcodeBackend(PlotterBackend):
    def __init__(self, gcode_config=None):
        self.options = GcodeOptions()
        if gcode_config:
            self._apply_config(gcode_config)
        self._serial = None
        self._pen_is_down = False

        # --- Serial I/O is owned by a single reader thread ---------------
        # GRBL responds to the realtime '?' command with a status report at
        # any time, interleaved with the line-based command/'ok' protocol.
        # To poll position while streaming we route ALL reads through one
        # reader thread that classifies lines, rather than reading from
        # multiple places (which would let the poller steal an 'ok').
        self._serial_lock = threading.Lock()      # guards writes to the port
        self._reader_thread = None
        self._poller_thread = None
        self._running = False
        self._ack_queue = queue.Queue()           # 'ok'/'error' for _wait_for_ok
        self._raw_queue = queue.Queue()            # responses while collecting RAW
        self._collect_raw = False

        # Latest known work position (the coordinate frame G0/G1 use) and the
        # work-coordinate offset (WCO) reported by GRBL, used to derive work
        # position when only machine position (MPos) is given.
        self._last_wpos = (0.0, 0.0)
        self._wco = (0.0, 0.0)
        self._position_callback = None

        # Realtime feed-rate override (percent of programmed feed). GRBL clamps
        # this to 10-200% and reports the active value in its status 'Ov:' field.
        self._feed_override = 100
        self._speed_callback = None

    def _apply_config(self, cfg):
        for key in ('serial_port', 'baud_rate', 'pen_mode', 'servo_pin',
                     'feed_rate_draw', 'feed_rate_travel',
                     'pen_servo_up', 'pen_servo_down',
                     'z_up', 'z_down', 'machine_width', 'machine_height',
                     'position_poll_interval'):
            if key in cfg:
                setattr(self.options, key, cfg[key])

    def interactive(self):
        pass

    def update(self):
        pass

    def set_position_callback(self, callback):
        """Register callback(x, y) invoked from the reader thread whenever GRBL
        reports a new realtime work position. Presence of this method signals
        to the driver that the backend supports realtime position reporting."""
        self._position_callback = callback

    def set_speed_callback(self, callback):
        """Register callback(percent) invoked when GRBL's active feed-rate
        override changes."""
        self._speed_callback = callback

    def adjust_speed(self, direction):
        """Change the plot speed in realtime via GRBL feed-rate override.
        These are single-byte realtime commands processed immediately, without
        disturbing the planner buffer, so they take effect mid-move.
          'up'    -> +10%   (0x91)
          'down'  -> -10%   (0x92)
          'reset' -> 100%   (0x90)
        """
        if not self._serial or not self._serial.is_open:
            return
        d = str(direction).lower()
        byte = {"up": b"\x91", "down": b"\x92", "reset": b"\x90"}.get(d)
        if byte is None:
            return
        with self._serial_lock:
            try:
                self._serial.write(byte)
                self._serial.flush()
            except Exception:
                pass

    def connect(self) -> bool:
        try:
            import serial
            self._serial = serial.Serial(
                self.options.serial_port,
                self.options.baud_rate,
                timeout=1
            )
            time.sleep(2)
            # Drain and display the GRBL boot banner before the reader starts.
            while self._serial.in_waiting > 0:
                line = self._serial.readline().decode(errors='replace').strip()
                if line:
                    print(f"GRBL: {line}")

            # Start the reader thread so _wait_for_ok() can receive acks.
            self._running = True
            self._reader_thread = threading.Thread(target=self._reader_loop, daemon=True)
            self._reader_thread.start()

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

            # Start realtime position polling.
            self._poller_thread = threading.Thread(target=self._poller_loop, daemon=True)
            self._poller_thread.start()
            return True
        except Exception as e:
            print(f"ERROR: G-code connect failed: {e}")
            self._running = False
            return False

    def disconnect(self):
        if self._serial and self._serial.is_open:
            self.penup()
            self._send("G0 X0 Y0")
            self._wait_for_ok()
            self._running = False
            with self._serial_lock:
                try:
                    self._serial.close()
                except Exception:
                    pass
        else:
            self._running = False
        # Let the helper threads notice _running/closed port and exit.
        if self._reader_thread:
            self._reader_thread.join(timeout=2)
        if self._poller_thread:
            self._poller_thread.join(timeout=2)
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
        """Return the most recent work position reported by the poller."""
        if not self._serial or not self._serial.is_open:
            return None
        return self._last_wpos

    def send_raw(self, command):
        if not self._serial or not self._serial.is_open:
            return ["(no serial connection)"]
        # Collect every line the reader sees until 'ok'/'error' or timeout.
        while not self._raw_queue.empty():
            try:
                self._raw_queue.get_nowait()
            except queue.Empty:
                break
        self._collect_raw = True
        responses = []
        try:
            self._send(command)
            deadline = time.time() + 2.0
            while time.time() < deadline:
                try:
                    resp = self._raw_queue.get(timeout=0.1)
                except queue.Empty:
                    continue
                responses.append(resp)
                low = resp.lower()
                if low.startswith("ok") or low.startswith("error"):
                    break
        finally:
            self._collect_raw = False
        return responses if responses else ["(no response)"]

    # --- Internal serial plumbing --------------------------------------

    def _send(self, cmd):
        if self._serial and self._serial.is_open:
            with self._serial_lock:
                self._serial.write((cmd + "\n").encode())
                self._serial.flush()

    def _wait_for_ok(self, timeout=30):
        if not self._serial:
            return
        try:
            line = self._ack_queue.get(timeout=timeout)
        except queue.Empty:
            print("WARNING: G-code response timeout")
            return
        if line.lower().startswith("error"):
            print(f"WARNING: G-code error: {line}")

    def _reader_loop(self):
        """Single owner of serial reads. Classifies each line and dispatches
        acks, status reports and raw responses to the right consumer."""
        while self._running:
            ser = self._serial
            if not ser or not ser.is_open:
                break
            try:
                raw = ser.readline()
            except Exception:
                break
            if not raw:
                continue  # read timeout, loop again
            line = raw.decode(errors='replace').strip()
            if not line:
                continue

            low = line.lower()
            if low.startswith("ok") or low.startswith("error"):
                if self._collect_raw:
                    self._raw_queue.put(line)
                else:
                    self._ack_queue.put(line)
            elif line.startswith("<"):
                self._handle_status(line)
                if self._collect_raw:
                    self._raw_queue.put(line)
            else:
                if self._collect_raw:
                    self._raw_queue.put(line)
                else:
                    print(f"GRBL: {line}")
                    sys.stdout.flush()

    def _poller_loop(self):
        """Periodically request a GRBL status report (realtime '?' command)."""
        interval = self.options.position_poll_interval
        while self._running:
            ser = self._serial
            if not ser or not ser.is_open:
                break
            try:
                with self._serial_lock:
                    ser.write(b"?")
                    ser.flush()
            except Exception:
                break
            time.sleep(interval)

    def _handle_status(self, line):
        """Parse a GRBL status report such as
        <Idle|MPos:1.000,2.000,0.000|FS:0,0|WCO:0.000,0.000,0.000>
        and emit the work position via the callback."""
        body = line.strip("<>")
        mpos = None
        wpos = None
        for field in body.split("|"):
            if field.startswith("MPos:"):
                mpos = self._parse_xy(field[5:])
            elif field.startswith("WPos:"):
                wpos = self._parse_xy(field[5:])
            elif field.startswith("WCO:"):
                wco = self._parse_xy(field[4:])
                if wco is not None:
                    self._wco = wco
            elif field.startswith("Ov:"):
                # Ov:feed,rapid,spindle -- report feed override changes.
                try:
                    feed = int(field[3:].split(",")[0])
                    if feed != self._feed_override:
                        self._feed_override = feed
                        if self._speed_callback:
                            self._speed_callback(feed)
                except (ValueError, IndexError):
                    pass

        if wpos is not None:
            pos = wpos
        elif mpos is not None:
            pos = (mpos[0] - self._wco[0], mpos[1] - self._wco[1])
        else:
            return

        self._last_wpos = pos
        if self._position_callback:
            try:
                self._position_callback(pos[0], pos[1])
            except Exception:
                pass

    @staticmethod
    def _parse_xy(s):
        try:
            parts = s.split(",")
            return (float(parts[0]), float(parts[1]))
        except (ValueError, IndexError):
            return None
