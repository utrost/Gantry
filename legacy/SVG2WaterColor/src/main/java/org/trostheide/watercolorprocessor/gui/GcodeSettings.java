package org.trostheide.watercolorprocessor.gui;

public class GcodeSettings {
    public String serial_port = "/dev/ttyUSB0";
    public int baud_rate = 115200;
    public String pen_mode = "servo";
    public int servo_pin = 0;
    public int feed_rate_draw = 1000;
    public int feed_rate_travel = 3000;
    public int pen_servo_up = 60;
    public int pen_servo_down = 30;
    public double z_up = 5.0;
    public double z_down = 0.0;
    public double machine_width = 300.0;
    public double machine_height = 200.0;
}
