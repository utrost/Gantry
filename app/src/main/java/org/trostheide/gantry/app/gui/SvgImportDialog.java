package org.trostheide.gantry.app.gui;

import javax.swing.*;
import java.awt.*;
import org.trostheide.gantry.pipeline.svgimport.*;
import org.trostheide.gantry.svgtoolbox.Config;

/** Import and raster workflows share the same placement and processing editor. */
public final class SvgImportDialog extends JDialog {
    public record Result(SvgImportOptions importOptions, Config toolboxConfig) { }
    private Result result;
    public SvgImportDialog(Window owner) { this(owner,null,300,200); }
    public SvgImportDialog(Window owner,java.io.File source) { this(owner,source,300,200); }
    public SvgImportDialog(Window owner,java.io.File source,double width,double height) {
        super(owner,"Add SVG or vector drawing",ModalityType.APPLICATION_MODAL);
        SvgImportOptionsPanel options=new SvgImportOptionsPanel(owner,source,width,height);
        setLayout(new BorderLayout()); add(options,BorderLayout.CENTER);
        JPanel footer=new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.add(WorkspaceStyle.button("Cancel",this::dispose));
        JButton apply=WorkspaceStyle.button("Import",()->{
            var chosen=options.collectResult();
            if(chosen!=null){result=new Result(chosen.importOptions(),chosen.toolboxConfig());dispose();}
        });
        footer.add(apply);add(footer,BorderLayout.SOUTH);getRootPane().setDefaultButton(apply);
        WorkspaceStyle.fitDialog(this,980,720);
    }
    static PaperFormat resolvePaperPreset(String value){return SvgImportOptionsPanel.resolvePaperPreset(value);}
    static String[] paperSizeOptions(){return SvgImportOptionsPanel.paperSizeOptions();}
    static String initialFitSelection(PaperFormat value){return SvgImportOptionsPanel.initialFitSelection(value);}
    public Result showDialog(){setVisible(true);return result;}
}
