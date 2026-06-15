from backend import PlotterBackend, BackendOptions


class AxiDraw(PlotterBackend):
    def __init__(self):
        self.connected = False
        self.options = BackendOptions()
        self._x = 0.0
        self._y = 0.0

    def interactive(self):
        print("[Mock] Entering Interactive Mode.")

    def update(self):
        print("[Mock] Updating Options.")

    def connect(self):
        print("[Mock] AxiDraw Connected.")
        self.connected = True
        return True

    def disconnect(self):
        print("[Mock] AxiDraw Disconnected.")
        self.connected = False

    def moveto(self, x, y):
        self._x, self._y = x, y
        print(f"[Mock] Moving to ({x:.2f}, {y:.2f}) [Pen UP]")

    def lineto(self, x, y):
        self._x, self._y = x, y
        print(f"[Mock] Drawing line to ({x:.2f}, {y:.2f}) [Pen DOWN]")

    def penup(self):
        print("[Mock] Pen UP")

    def pendown(self):
        print("[Mock] Pen DOWN")

    def move(self, dx, dy):
        self._x += dx
        self._y += dy
        print(f"[Mock] Relative Move ({dx:.2f}, {dy:.2f}) [Pen UP]")

    def query_position(self):
        return (self._x, self._y)
