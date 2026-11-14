package org.trostheide.gantry.app.gui;

import javax.swing.*;
import java.awt.*;
import org.trostheide.gantry.pipeline.svgimport.*;
import org.trostheide.gantry.svgtoolbox.Config;

/** Import and raster workflows share the same placement and processing editor. */
public final class SvgImportDialog extends JDialog {
    public record Result(SvgImportOptions importOptions, Config toolboxConfig) { }
    private Result result;
    private final SvgImportOptionsPanel options;
    private final JButton apply;
    private final JTextArea intent = WorkspaceStyle.note("Opens a new drawing. The current artwork is replaced only after a successful import.");
    public SvgImportDialog(Window owner) { this(owner,null,300,200); }
    public SvgImportDialog(Window owner,java.io.File source) { this(owner,source,300,200); }
    public SvgImportDialog(Window owner,java.io.File source,double width,double height) {
        super(owner,"Open SVG or vector drawing",ModalityType.APPLICATION_MODAL);
        options=new SvgImportOptionsPanel(owner,source,width,height);
        setLayout(new BorderLayout()); add(options,BorderLayout.CENTER);
        intent.setBorder(BorderFactory.createEmptyBorder(8,12,0,12));
        add(intent,BorderLayout.NORTH);
        JPanel footer=new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.add(WorkspaceStyle.button("Cancel",this::dispose));
        apply=WorkspaceStyle.button("Open drawing",()->{
            if(!options.previewReady())return;
            var chosen=options.collectResult();
            if(chosen!=null && options.previewReady()){result=new Result(chosen.importOptions(),chosen.toolboxConfig());dispose();}
        });
        apply.setEnabled(false);WorkspaceStyle.primary(apply);
        options.onPreviewChange(()->apply.setEnabled(options.previewReady()));
        footer.add(apply);add(footer,BorderLayout.SOUTH);getRootPane().setDefaultButton(apply);
        WorkspaceStyle.fitDialog(this,940,640);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getRootPane().registerKeyboardAction(e->dispose(),KeyStroke.getKeyStroke("ESCAPE"),JComponent.WHEN_IN_FOCUSED_WINDOW);
    }
    @Override public void dispose(){options.closePreview();super.dispose();}
    static PaperFormat resolvePaperPreset(String value){return SvgImportOptionsPanel.resolvePaperPreset(value);}
    static String[] paperSizeOptions(){return SvgImportOptionsPanel.paperSizeOptions();}
    static String initialFitSelection(PaperFormat value){return SvgImportOptionsPanel.initialFitSelection(value);}
    void setAppendIntent() {
        setTitle("Append SVG to current drawing");
        apply.setText("Add to drawing");
        intent.setText("Adds artwork beside the current drawing as new layers. Existing layers and their plot inclusion are preserved.");
    }
    void setPlacement(java.awt.geom.AffineTransform value){options.setPlacement(value);}
    void configurePreview(org.trostheide.gantry.app.plot.PlotSettings settings){options.configurePreview(settings);}
    public Result showDialog(){setVisible(true);return result;}
}
