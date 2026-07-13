package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.model.ProcessorOutput;
import javax.swing.*;
import java.awt.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** Presents asynchronous command-model work without blocking the Swing event thread. */
final class BusyTaskRunner {
    private final JComponent parent; private final Consumer<String> log; private final Consumer<String> error;
    BusyTaskRunner(JComponent parent, Consumer<String> log, Consumer<String> error){this.parent=parent;this.log=log;this.error=error;}
    void run(String description, Callable<ProcessorOutput> task, Consumer<ProcessorOutput> success){
        Window window=SwingUtilities.getWindowAncestor(parent);if(window!=null)window.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        BusyOverlay overlay=BusyOverlay.show(parent,message(description));log.accept(message(description));
        new SwingWorker<ProcessorOutput,Void>(){
            @Override protected ProcessorOutput doInBackground() throws Exception{return task.call();}
            @Override protected void done(){try{success.accept(get());}catch(Exception ex){Throwable cause=ex.getCause()!=null?ex.getCause():ex;error.accept(description+" failed: "+cause.getMessage());}
                finally{if(overlay!=null)overlay.dismiss();if(window!=null)window.setCursor(Cursor.getDefaultCursor());}}
        }.execute();
    }
    private static String message(String description){return switch(description){case"Vectorize"->"Vectorizing image…";case"Import"->"Importing…";case"Process SVG"->"Processing SVG…";default->description+"…";};}
}
