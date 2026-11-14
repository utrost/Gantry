package org.trostheide.gantry.app.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import org.trostheide.gantry.app.session.DocumentSession;

/** Native Swing fill acceptance; no machine backend is constructed. */
public final class PenFillAcceptance {
    static Object field(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void shot(Component c,Path file)throws Exception{BufferedImage image=new BufferedImage(c.getWidth(),c.getHeight(),BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();c.printAll(g);g.dispose();ImageIO.write(image,"png",file.toFile());}
    public static void main(String[] args)throws Exception{
        Path evidence=Path.of(args[0]);Files.createDirectories(evidence);
        for(boolean dark:new boolean[]{true,false}){
            SwingUtilities.invokeAndWait(()->{if(dark)com.formdev.flatlaf.FlatDarkLaf.setup();else com.formdev.flatlaf.FlatLightLaf.setup();});
            SwingUtilities.invokeAndWait(()->{try{
                JPanel parent=new JPanel();var dialog=new PenFillDialog(parent,new Rectangle2D.Double(0,0,20,15),0,.5,20);
                JButton apply=(JButton)field(dialog,"apply");check(!apply.isEnabled(),"Apply enabled before preview");
                Timer timer=new Timer(40,null);timer.addActionListener(e->{try{if(!apply.isEnabled())return;timer.stop();shot(dialog,evidence.resolve(dark?"fill-dark.png":"fill-light.png"));dialog.dispose();}catch(Exception ex){throw new RuntimeException(ex);}});timer.start();
                check(dialog.showDialog()==null,"Cancel accepted a fill");
                check(!timer.isRunning(),"preview never completed");
            }catch(Exception ex){throw new RuntimeException(ex);}});
        }
        SwingUtilities.invokeAndWait(()->{try{
            var session=new DocumentSession();session.replace(CalibrationSwatch.generate(.5,0));var original=session.currentOutput();double originalWidth=session.penWidthsMm().get(0);
            int[] changes={0};DocumentEditor editor=new DocumentEditor(session,new JPanel(),()->changes[0]++,v->{},v->{},s->{},new DocumentEditor.Feedback(){public void edited(String s){}public void notice(String s){}});
            var mode=DocumentEditor.class.getDeclaredField("penWidthFill");mode.setAccessible(true);mode.setBoolean(editor,true);
            editor.setPhysicalTransform(()->AffineTransform.getScaleInstance(2,3));
            Timer accept=new Timer(40,null);boolean[] edited={false};accept.addActionListener(e->{try{
                for(Window window:Window.getWindows())if(window instanceof PenFillDialog dialog&&dialog.isVisible()){
                    var apply=(JButton)field(dialog,"apply");if(!apply.isEnabled())continue;
                    if(!edited[0]){edited[0]=true;var opts=(PenFillOptionsPanel)field(dialog,"options");opts.setFill(new org.trostheide.gantry.svgtoolbox.PenFill(.6,20,true));check(!apply.isEnabled(),"stale preview accepted");return;}
                    accept.stop();apply.doClick();return;
                }
            }catch(Exception ex){throw new RuntimeException(ex);}});accept.start();
            editor.hatch(new Path2D.Double(new Rectangle2D.Double(0,0,10,10)),0);
            check(changes[0]>0&&session.canUndo(),"fill did not invalidate/update undo");check(session.penWidthsMm().get(0)==.6,"layer width not applied");
            check(session.currentOutput().layers().get(0).commands().size()>original.layers().get(0).commands().size(),"no generated strokes");
            editor.undo();check(session.currentOutput().layers().get(0).commands().size()==original.layers().get(0).commands().size(),"undo did not restore geometry");check(session.penWidthsMm().get(0)==originalWidth,"undo did not restore pen width");
            editor.redo();check(session.penWidthsMm().get(0)==.6,"redo lost width");
        }catch(Exception ex){throw new RuntimeException(ex);}});
        System.out.println("PASS Light/Dark coverage, Cancel, stale-preview invalidation, transformed region Apply, width/geometry undo and redo; no hardware backend");System.exit(0);
    }
}
