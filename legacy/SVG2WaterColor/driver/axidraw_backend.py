from backend import PlotterBackend


class AxiDrawBackend(PlotterBackend):
    def __init__(self, axidraw_module):
        self._ad = axidraw_module.AxiDraw()
        self.options = self._ad.options

    def interactive(self):
        self._ad.interactive()
        # Re-bind: interactive() may replace the internal options object
        self.options = self._ad.options

    def update(self):
        self._ad.update()

    def connect(self) -> bool:
        result = self._ad.connect()
        # Re-bind again in case connect() replaced options
        self.options = self._ad.options
        return result

    def disconnect(self):
        self._ad.disconnect()

    def moveto(self, x, y):
        self._ad.moveto(x, y)

    def lineto(self, x, y):
        self._ad.lineto(x, y)

    def move(self, dx, dy):
        self._ad.move(dx, dy)

    def penup(self):
        self._ad.penup()

    def pendown(self):
        self._ad.pendown()

    def query_position(self):
        try:
            result = self._ad.usb_query('QS\r')
            result_list = result.strip().split(",")
            a_pos, b_pos = int(result_list[0]), int(result_list[1])
            x_inch = (a_pos + b_pos) / (4 * self._ad.params.native_res_factor)
            y_inch = (a_pos - b_pos) / (4 * self._ad.params.native_res_factor)
            x_mm = x_inch * 25.4
            y_mm = y_inch * 25.4
            return (x_mm, y_mm)
        except Exception:
            return None
