package org.trostheide.watercolorprocessor.gui;

/**
 * Persistent general plotter settings.
 * Serialized as the "general" section of config.json.
 */
public class GeneralSettings {
    public int modelIndex;
    public boolean mock;
    public boolean invertX;
    public boolean invertY;
    public boolean swapXY;
    public boolean visualMirror;
    public String machineOrigin;
    public int speedDown;
    public int speedUp;
    public int penUp;
    public int penDown;
    public String orientation;
    public String canvasAlignment;
    public int viewRotation;
    public double paddingX;
    public double paddingY;
    public boolean invertJogY;
    public String backend = "axidraw";
    public String pythonPath;
    public GcodeSettings gcode;
}
