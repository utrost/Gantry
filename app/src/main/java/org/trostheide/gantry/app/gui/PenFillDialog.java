package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.svgtoolbox.PenFill;
import org.trostheide.gantry.svgtoolbox.patterns.PenFillGeometry;
import javax.swing.*;
import java.awt.*;
import java.util.concurrent.CancellationException;

/** Apply accepts only the currently displayed generation; Cancel cancels the worker. */
final class PenFillDialog extends JDialog {
    record Accepted(PenFill fill,PenFillGeometry.Result geometry) { }
    private final PenFillOptionsPanel options;
    private final FillCoverageCanvas canvas=new FillCoverageCanvas();
    private final JTextArea status=WorkspaceStyle.note("");
    private final JButton apply=new JButton("Apply fill");
    private final Timer debounce;
    private SwingWorker<Accepted,Void> worker;
    private Accepted preview,result;
    private int revision;
    PenFillDialog(Component parent,Shape physicalRegion,double angle,double width,double overlap) {
        this(parent,physicalRegion,angle,width,overlap,true);
    }
    PenFillDialog(Component parent,Shape physicalRegion,double angle,double width,double overlap,boolean inside) {
        super(SwingUtilities.getWindowAncestor(parent),"Fill coverage — physical millimetres",ModalityType.APPLICATION_MODAL);
        ((JPanel)getContentPane()).setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        options=new PenFillOptionsPanel(width,overlap);options.setFill(new PenFill(width,overlap,inside));setLayout(new BorderLayout(8,8));add(options,BorderLayout.NORTH);add(canvas,BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout());footer.add(status,BorderLayout.CENTER);JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JCheckBox coverage=new JCheckBox("Stroke coverage",true);coverage.addActionListener(e->canvas.setCoverage(coverage.isSelected()));buttons.add(coverage);
        JButton cancel=new JButton("Cancel");cancel.addActionListener(e->dispose());buttons.add(cancel);WorkspaceStyle.primary(apply);buttons.add(apply);footer.add(buttons,BorderLayout.SOUTH);add(footer,BorderLayout.SOUTH);
        apply.addActionListener(e->{try{options.commit();}catch(Exception ex){status.setText(ex.getMessage());return;}if(preview!=null&&preview.fill().equals(options.fill())){result=preview;dispose();}});
        debounce=new Timer(180,e->{final int generation=revision;final PenFill settings;try{options.commit();settings=options.fill();}catch(Exception ex){status.setText(ex.getMessage());return;}
            worker=new SwingWorker<>(){
                protected Accepted doInBackground(){return new Accepted(settings,PenFillGeometry.preview(physicalRegion,angle,settings));}
                protected void done(){if(generation!=revision||isCancelled())return;try{preview=get();canvas.setResult(preview.geometry());apply.setEnabled(!preview.geometry().paths().isEmpty());
                    double length=preview.geometry().paths().stream().mapToDouble(s->{var b=s.getBounds2D();return Math.hypot(b.getWidth(),b.getHeight());}).sum();
                    status.setText(String.format(java.util.Locale.ROOT,"%.4f mm spacing · %d strokes · %.0f mm travel while drawing\nBlue: coverage · Amber: uncovered/narrow features · Red: overshoot\nRound-tip geometry only; paper, pressure and ink affect the result.%s",settings.spacingMm(),preview.geometry().paths().size(),length,preview.geometry().paths().isEmpty()?"\nRegion too narrow for this pen.":""));
                }catch(CancellationException ignored){}catch(Exception ex){status.setText("Preview unavailable: "+(ex.getCause()==null?ex:ex.getCause()).getMessage());}}
            };worker.execute();});debounce.setRepeats(false);
        getRootPane().registerKeyboardAction(e->dispose(),KeyStroke.getKeyStroke("ESCAPE"),JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().setDefaultButton(apply);
        options.onChange(this::schedule);setDefaultCloseOperation(DISPOSE_ON_CLOSE);WorkspaceStyle.fitDialog(this,680,650);schedule();
    }
    private void schedule(){revision++;preview=null;apply.setEnabled(false);canvas.setResult(null);status.setText("Updating coverage…");if(worker!=null)worker.cancel(true);debounce.restart();}
    Accepted showDialog(){setVisible(true);return result;}
    @Override public void dispose(){revision++;if(debounce!=null)debounce.stop();if(worker!=null)worker.cancel(true);super.dispose();}
}
