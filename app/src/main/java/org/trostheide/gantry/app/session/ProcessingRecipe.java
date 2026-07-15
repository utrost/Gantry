package org.trostheide.gantry.app.session;

import org.trostheide.gantry.svgtoolbox.Config;
import org.trostheide.gantry.svgtoolbox.HatchStyle;

import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** JSON-friendly, exact snapshot of the processing controls used for an imported SVG. */
public record ProcessingRecipe(float strokeWidth, List<String> palette, boolean hatching,
        HatchRecipe hatch, Map<String,HatchRecipe> hatchOverrides, List<String> hiddenLayers,
        double simplifyTolerance, double rotationDegrees, Crop crop, boolean printStats,
        boolean optimizePaths, boolean lineSimplify, double lineSimplifyTolerance,
        boolean lineMerge, double lineMergeTolerance, boolean lineSort, boolean twoOpt,
        boolean reloop, boolean handDrawn, double wobbleStrength, double lineDetail,
        double wobbleSize, long seed) {

    public ProcessingRecipe {
        palette=palette==null?List.of():List.copyOf(palette);
        hatch=hatch==null?new HatchRecipe(45,5,"linear",0,0,0):hatch;
        hatchOverrides=hatchOverrides==null?Map.of():Map.copyOf(hatchOverrides);
        hiddenLayers=hiddenLayers==null?List.of():List.copyOf(hiddenLayers);
    }

    public static ProcessingRecipe fromConfig(Config c) {
        Map<String,HatchRecipe> overrides=c.overrides().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,e->HatchRecipe.from(e.getValue())));
        return new ProcessingRecipe(c.strokeWidth(),c.palette().stream().map(ProcessingRecipe::hex).toList(),c.enableHatching(),
                HatchRecipe.from(c.globalStyle()),overrides,c.hiddenLayers(),c.simplifyTolerance(),c.rotationDegrees(),Crop.from(c.cropBounds()),c.printStats(),
                c.optimizePaths(),c.linesimplify(),c.linesimplifyTolerance(),c.linemerge(),c.linemergeTolerance(),c.linesort(),c.linesortTwoOpt(),c.reloop(),
                c.handdrawn(),c.handdrawnMagnitude(),c.handdrawnSegment(),c.handdrawnWavelength(),c.handdrawnSeed());
    }

    public Config toConfig() {
        HatchStyle global=hatch.toStyle();
        Map<String,HatchStyle> overrides=hatchOverrides.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,e->e.getValue().toStyle()));
        return new Config.Builder().inputPath("").outputPath("").strokeWidth(strokeWidth)
                .palette(palette.stream().map(Color::decode).toList()).enableHatching(hatching).globalStyle(global).overrides(overrides)
                .strokeWidthOverrides(Map.of()).hiddenLayers(hiddenLayers).noHatchColors(List.of()).simplifyTolerance(simplifyTolerance)
                .hatchPattern(global.patternName()).rotationDegrees(rotationDegrees).cropBounds(crop==null?null:crop.toRectangle()).printStats(printStats)
                .optimizePaths(optimizePaths).linesimplify(lineSimplify).linesimplifyTolerance(lineSimplifyTolerance)
                .linemerge(lineMerge).linemergeTolerance(lineMergeTolerance).linesort(lineSort).linesortTwoOpt(twoOpt).reloop(reloop)
                .handdrawn(handDrawn).handdrawnMagnitude(wobbleStrength).handdrawnSegment(lineDetail).handdrawnWavelength(wobbleSize).handdrawnSeed(seed).build();
    }

    public record HatchRecipe(double angle,double gap,String pattern,double amplitude,double wavelength,double dotRadius){
        static HatchRecipe from(HatchStyle h){return new HatchRecipe(h.angle(),h.gap(),h.patternName(),h.amplitude(),h.wavelength(),h.dotRadius());}
        HatchStyle toStyle(){return new HatchStyle(angle,gap,pattern,amplitude,wavelength,dotRadius);}
    }
    public record Crop(double x,double y,double width,double height){
        static Crop from(Rectangle2D r){return r==null?null:new Crop(r.getX(),r.getY(),r.getWidth(),r.getHeight());}
        Rectangle2D toRectangle(){return new Rectangle2D.Double(x,y,width,height);}
    }
    private static String hex(Color c){return String.format("#%06x",c.getRGB()&0xffffff);}
}
