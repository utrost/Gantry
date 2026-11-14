package org.trostheide.gantry.app.gui;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.trostheide.gantry.svgtoolbox.*;
import org.trostheide.gantry.pipeline.svgimport.SvgImportOptions;

/** Real calibration and processing views; no backend is constructed. */
public final class CaptureFillScreenshots {
    static void save(JComponent content,Path path)throws Exception {
        var image=new BufferedImage(content.getWidth(),content.getHeight(),BufferedImage.TYPE_INT_RGB);
        var graphics=image.createGraphics();content.printAll(graphics);graphics.dispose();ImageIO.write(image,"png",path.toFile());
    }
    public static void main(String[] args)throws Exception{
        Path root=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectories(out);
        System.setProperty("gantry.config.file",Files.createTempDirectory("gantry-fill-capture-").resolve("config.json").toString());
        JFrame[] calibrationFrame = new JFrame[1];
        VisualizationPanel[] calibrationCanvas = new VisualizationPanel[1];
        SwingUtilities.invokeAndWait(()->{try{
            com.formdev.flatlaf.FlatDarkLaf.setup();
            JFrame frame=new JFrame("Pen/paper calibration artwork");JPanel content=new JPanel(new BorderLayout(12,12));content.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));
            JLabel title=new JLabel("Pen/paper calibration · effective width 0.5 mm");title.setFont(title.getFont().deriveFont(Font.BOLD,20));content.add(title,BorderLayout.NORTH);
            VisualizationPanel canvas=new VisualizationPanel();canvas.setMachineSize(80,32);canvas.setMachineOrigin("Bottom-Left");canvas.setEffectiveAxes(false,false,false);canvas.setCanvasAlignment("Center");canvas.editingEnabled=false;canvas.loadFromOutput(CalibrationSwatch.generate(.5,0));canvas.setPenWidthsMm(java.util.List.of(.5));content.add(canvas);
            content.add(WorkspaceStyle.note("0%, 10%, 20%, 30% overlap · labelled patches and stroke samples\nOrdinary artwork: review placement, then use Check and Start. No hardware is connected."),BorderLayout.SOUTH);
            frame.setContentPane(content);frame.setSize(960,540);frame.setVisible(true);
            calibrationFrame[0]=frame;calibrationCanvas[0]=canvas;
        }catch(Exception ex){throw new RuntimeException(ex);}});
        // Native peer/layout events arrive after setVisible returns. Capture on a later
        // EDT turn, after layout, rather than recording only the initial title paint.
        SwingUtilities.invokeAndWait(()->{try{
            JFrame frame=calibrationFrame[0];frame.validate();
            if(calibrationCanvas[0].getWidth()<100 || calibrationCanvas[0].getHeight()<100)
                throw new IllegalStateException("Calibration canvas has not been laid out");
            save(frame.getRootPane(),out.resolve("calibration.png"));frame.dispose();
        }catch(Exception ex){throw new RuntimeException(ex);}});
        SwingUtilities.invokeAndWait(()->{try{
            var config=new Config.Builder().enableHatching(true).globalStyle(new HatchStyle(0,5,"linear",0,0,0,new PenFill(.5,20,true))).build();
            var options=new SvgImportOptions(0,"pen",.01,70,50,true,0,0,false,true);
            EditProcessDialog dialog=new EditProcessDialog(null,root.resolve("docs/samples/hatch-fill.svg").toFile(),config,options,new java.awt.geom.AffineTransform());
            Timer timer=new Timer(100,null);int[] ticks={0};timer.addActionListener(e->{try{if(++ticks[0]>200)throw new IllegalStateException("Processing preview did not become ready");if(!dialog.getRootPane().getDefaultButton().isEnabled())return;timer.stop();save(dialog.getRootPane(),out.resolve("processing.png"));dialog.dispose();}catch(Exception ex){ex.printStackTrace();System.exit(1);}});timer.start();dialog.showDialog();
        }catch(Exception ex){throw new RuntimeException(ex);}});System.exit(0);
    }
}
