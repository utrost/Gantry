package org.trostheide.gantry.app.gui;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/** Presents asynchronous command-model work without blocking the Swing event thread. */
final class BusyTaskRunner {
    @FunctionalInterface
    interface CancellableTask<T> {
        T call(BooleanSupplier cancellationRequested) throws Exception;
    }

    private final JComponent parent; private final Consumer<String> log; private final Consumer<String> error;
    BusyTaskRunner(JComponent parent, Consumer<String> log, Consumer<String> error){this.parent=parent;this.log=log;this.error=error;}
    <T> void run(String description, Callable<T> task, Consumer<T> success){
        Window window=SwingUtilities.getWindowAncestor(parent);if(window!=null)window.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        BusyOverlay overlay=BusyOverlay.show(parent,message(description));log.accept(message(description));
        new SwingWorker<T,Void>(){
            @Override protected T doInBackground() throws Exception{return task.call();}
            @Override protected void done(){try{success.accept(get());}catch(Exception ex){Throwable cause=ex.getCause()!=null?ex.getCause():ex;error.accept(description+" failed: "+cause.getMessage());}
                finally{if(overlay!=null)overlay.dismiss();if(window!=null)window.setCursor(Cursor.getDefaultCursor());}}
        }.execute();
    }

    <T> void runCancellable(String description, CancellableTask<T> task,
            Consumer<T> success, Runnable cancelled) {
        Window window=SwingUtilities.getWindowAncestor(parent);
        if(window!=null)window.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        AtomicBoolean cancellationRequested=new AtomicBoolean();
        @SuppressWarnings("unchecked") SwingWorker<T,Void>[] workerRef=new SwingWorker[1];
        BusyOverlay overlay=BusyOverlay.show(parent,message(description),()->{
            cancellationRequested.set(true);
            SwingWorker<T,Void> worker=workerRef[0];
            if(worker!=null)worker.cancel(true);
        });
        log.accept(message(description));
        SwingWorker<T,Void> worker=new SwingWorker<>(){
            @Override protected T doInBackground() throws Exception{return task.call(cancellationRequested::get);}
            @Override protected void done(){try{success.accept(get());}
                catch(Exception ex){Throwable cause=ex.getCause()!=null?ex.getCause():ex;
                    if(cause instanceof CancellationException){log.accept(description+" cancelled.");cancelled.run();}
                    else error.accept(description+" failed: "+cause.getMessage());}
                finally{if(overlay!=null)overlay.dismiss();if(window!=null)window.setCursor(Cursor.getDefaultCursor());}}
        };workerRef[0]=worker;worker.execute();
    }
    private static String message(String description){return switch(description){case"Vectorize"->"Vectorizing image…";case"Import"->"Importing…";case"Process SVG"->"Processing SVG…";case"Optimize"->"Optimizing artwork…";default->description+"…";};}
}
