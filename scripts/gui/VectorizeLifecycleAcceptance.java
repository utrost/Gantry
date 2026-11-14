package org.trostheide.gantry.app.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/** Valid/stale/failed/cancelled trace lifecycle, using disposable source images only. */
public final class VectorizeLifecycleAcceptance {
    public static void main(String[] args) throws Exception {
        Path directory=Files.createTempDirectory("gantry-trace-review-");
        Path image=directory.resolve("line.png");
        BufferedImage raster=new BufferedImage(80,80,BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics=raster.createGraphics();graphics.setColor(Color.WHITE);graphics.fillRect(0,0,80,80);
        graphics.setColor(Color.BLACK);graphics.setStroke(new BasicStroke(3));graphics.drawLine(8,8,70,70);graphics.dispose();
        javax.imageio.ImageIO.write(raster,"png",image.toFile());
        VectorizeStudioDialog dialog=edt(()->{var studio=new VectorizeStudioDialog(null,image.toFile(),List.of("-s","centerline"),210,148);studio.addNotify();return studio;});
        SwingUtilities.invokeLater(()->dialog.setVisible(true));
        try {
            await(()->((JButton)field(dialog,"vectorizeBtn")).isEnabled());
            Set<File> files=files(dialog);check(!files.isEmpty(),"successful trace file exists");
            check(files.stream().noneMatch(svg -> new File(svg.getPath()+".json").exists()),"temporary metadata sidecars removed");
            edt(()->{
                JSpinner threshold=(JSpinner)field(dialog,"clThresholdSpinner");
                threshold.setValue(100);threshold.setValue(150);threshold.setValue(110);
                check(!((JButton)field(dialog,"vectorizeBtn")).isEnabled(),"rapid edits immediately invalidate Import");
                check(field(dialog,"validTrace")==null,"old trace cannot be accepted");return null;
            });
            await(()->((JButton)field(dialog,"vectorizeBtn")).isEnabled());
            check(files.size()==1,"superseded trace files removed");
            Files.delete(image);
            edt(()->{((JSpinner)field(dialog,"clThresholdSpinner")).setValue(120);return null;});
            await(()->((JLabel)field(dialog,"readout")).getText().startsWith("Trace failed:"));
            check(!edt(()->((JButton)field(dialog,"vectorizeBtn")).isEnabled()),"failed trace cannot import previous output");
            edt(()->{dialog.dispose();return null;});
            await(()->files.stream().noneMatch(File::exists));
            System.out.println("PASS trace success, rapid-edit invalidation, failure rejection and temporary-file cleanup on Close");
        } finally { edt(()->{dialog.dispose();return null;}); }
        javax.imageio.ImageIO.write(raster,"png",image.toFile());
        VectorizeStudioDialog accepted=edt(()->{var studio=new VectorizeStudioDialog(null,image.toFile(),List.of("-s","centerline"),210,148);studio.addNotify();return studio;});
        SwingUtilities.invokeLater(()->accepted.setVisible(true));
        try {
            await(()->((JButton)field(accepted,"vectorizeBtn")).isEnabled());
            edt(()->{((JButton)field(accepted,"vectorizeBtn")).doClick();return null;});
            VectorizeStudioDialog.Result result=edt(()->(VectorizeStudioDialog.Result)field(accepted,"result"));
            check(result!=null && result.tracedSvg().isFile(),"accepted trace survives dialog disposal for document import");
            Files.deleteIfExists(result.tracedSvg().toPath());
            System.out.println("PASS accepted trace remains available after Import closes the studio");
        } finally { edt(()->{accepted.dispose();return null;}); }
        Files.deleteIfExists(image);Files.deleteIfExists(directory);
        System.exit(0);
    }
    @SuppressWarnings("unchecked") private static Set<File> files(Object object)throws Exception{return (Set<File>)field(object,"traceFiles");}
    private static Object field(Object object,String name)throws Exception{Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void await(Callable<Boolean> test)throws Exception{for(int i=0;i<300;i++){if(edt(test))return;Thread.sleep(50);}throw new AssertionError("Timed out waiting for trace state");}
    private static <T>T edt(Callable<T> action)throws Exception{var task=new java.util.concurrent.FutureTask<T>(action);SwingUtilities.invokeAndWait(task);return task.get();}
}
