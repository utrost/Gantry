package org.trostheide.gantry.svgtoolbox;

/** Physical millimetres, independent of SVG user units and artwork scale. */
public record PenFill(double widthMm, double overlapPercent, boolean insideBoundary) {
    public PenFill {
        if(!Double.isFinite(widthMm)||widthMm<=0||widthMm>100)
            throw new IllegalArgumentException("Effective pen width must be greater than 0 and at most 100 mm.");
        if(!Double.isFinite(overlapPercent)||overlapPercent<0||overlapPercent>=100)
            throw new IllegalArgumentException("Overlap must be between 0% (inclusive) and 100% (exclusive).");
    }
    public double spacingMm(){return widthMm*(1-overlapPercent/100);}
}
