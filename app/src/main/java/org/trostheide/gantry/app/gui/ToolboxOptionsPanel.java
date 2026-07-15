package org.trostheide.gantry.app.gui;

import org.trostheide.gantry.svgtoolbox.Config;
import org.trostheide.gantry.svgtoolbox.HatchStyle;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

/** Beginner-first editor for SVG processors, hatching and path optimization. */
final class ToolboxOptionsPanel extends JPanel {
    enum Preset {
        UNCHANGED("Keep artwork unchanged", "Import the paths as they are."),
        RECOMMENDED("Recommended", "Shorten pen-up travel without changing the drawing."),
        FAST("Plot faster", "Reduce points, join nearby lines and improve drawing order."),
        CLEAN("Smooth & clean", "Gently simplify noisy paths and improve drawing order."),
        HAND_DRAWN("Hand-drawn", "Add a repeatable, natural wobble without smoothing it away."),
        FILLED("Fill closed shapes", "Add evenly spaced lines inside filled shapes."),
        CUSTOM("Custom", "Your own combination of settings.");
        final String label, help;
        Preset(String label, String help) { this.label=label; this.help=help; }
        @Override public String toString() { return label; }
    }

    private record Pattern(String label, String id) {
        @Override public String toString() { return label; }
    }
    private static final Pattern[] PATTERNS = {
            new Pattern("Straight lines", "linear"), new Pattern("Crosshatch", "cross"),
            new Pattern("Zigzag", "zigzag"), new Pattern("Waves", "wave"),
            new Pattern("Dots", "dot"), new Pattern("Keep outline only", "none"),
            new Pattern("Remove filled shape", "empty")};

    private final JComboBox<Preset> presetCombo = new JComboBox<>(Preset.values());
    private final JLabel presetHelp = helpLabel();
    private final JLabel warningLabel = helpLabel();

    private final JCheckBox hatchCheck = new JCheckBox("Fill closed shapes");
    private final JComboBox<Pattern> hatchPatternCombo = new JComboBox<>(PATTERNS);
    private final JSpinner hatchAngleSpinner = spinner(45, -360, 360, 5);
    private final JSpinner hatchGapSpinner = spinner(5, .1, 1000, .5);
    private final JSpinner hatchAmplitudeSpinner = spinner(0, 0, 1000, .5);
    private final JSpinner hatchWavelengthSpinner = spinner(0, 0, 1000, .5);
    private final JSpinner dotRadiusSpinner = spinner(0, 0, 1000, .1);
    private final JPanel hatchDetails = verticalPanel();
    private final JPanel waveDetails = verticalPanel();
    private final JPanel dotDetails = verticalPanel();
    private final HatchOverridesPanel hatchOverridesPanel;

    private final JCheckBox handdrawnCheck = new JCheckBox("Make lines look hand-drawn");
    private final JSpinner handdrawnMagnitudeSpinner = spinner(2, 0, 100, .5);
    private final JSpinner handdrawnSegmentSpinner = spinner(4, .5, 100, .5);
    private final JSpinner handdrawnWavelengthSpinner = spinner(30, 1, 1000, 5);
    private final JSpinner handdrawnSeedSpinner = new JSpinner(new SpinnerNumberModel(1337, 0, Integer.MAX_VALUE, 1));
    private final JPanel handdrawnDetails = verticalPanel();

    private final JCheckBox optimizeCheck = new JCheckBox("Improve path order");
    private final JCheckBox linesimplifyCheck = new JCheckBox("Remove unnecessary points");
    private final JSpinner linesimplifyToleranceSpinner = spinner(.378, 0, 100, .01);
    private final JCheckBox linemergeCheck = new JCheckBox("Join nearby line segments");
    private final JSpinner linemergeToleranceSpinner = spinner(1.89, 0, 100, .01);
    private final JCheckBox linesortCheck = new JCheckBox("Draw nearby paths together");
    private final JCheckBox linesortTwoOptCheck = new JCheckBox("Spend extra time finding a shorter route");
    private final JCheckBox reloopCheck = new JCheckBox("Start closed shapes near the previous path");
    private final JCheckBox printStatsCheck = new JCheckBox("Write processing statistics to the log");

    private final JTextField strokeWidthField = new JTextField("0", 8);
    private final JTextField paletteField = new JTextField(20);
    private final JTextField hiddenLayersField = new JTextField(20);
    private final JSpinner simplifyToleranceSpinner = spinner(0, 0, 100, .1);
    private final JSpinner rotateSpinner = spinner(0, -360, 360, 90);
    private final JComboBox<String> cropCombo = new JComboBox<>(new String[]{"None", "A4", "Letter", "Custom"});
    private final JTextField cropCustomField = new JTextField("793.7x1122.5", 12);
    private final JPanel advancedPanel = verticalPanel();
    private final JToggleButton advancedToggle = new JToggleButton("Show fine-tuning");

    private final List<Runnable> changeListeners = new ArrayList<>();
    private boolean applying;
    private static Config savedConfig;

    ToolboxOptionsPanel() { this(List.of()); }

    ToolboxOptionsPanel(Collection<String> fillColors) {
        hatchOverridesPanel = new HatchOverridesPanel(fillColors);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel preset = card("Start with a goal");
        preset.add(row("Preset", presetCombo));
        presetHelp.setBorder(BorderFactory.createEmptyBorder(3, 0, 2, 0));
        preset.add(presetHelp);
        add(preset); add(gap());

        JPanel appearance = card("Look & feel");
        appearance.add(handdrawnCheck);
        handdrawnDetails.add(row("Wobble strength", slider(handdrawnMagnitudeSpinner, 0, 10, 2)));
        handdrawnDetails.add(row("Wobble size", handdrawnWavelengthSpinner));
        handdrawnDetails.add(row("Line detail", handdrawnSegmentSpinner));
        handdrawnDetails.add(row("Repeatable variation", handdrawnSeedSpinner));
        appearance.add(indented(handdrawnDetails));
        add(appearance); add(gap());

        JPanel fill = card("Filled areas");
        hatchCheck.setToolTipText("Turns SVG fill colours into plotter-friendly marks inside closed shapes");
        fill.add(hatchCheck);
        hatchDetails.add(row("Pattern", hatchPatternCombo));
        hatchDetails.add(row("Direction", hatchAngleSpinner));
        hatchDetails.add(row("Spacing (smaller = darker)", slider(hatchGapSpinner, .5, 30, 2)));
        waveDetails.add(row("Wave height (0 = automatic)", hatchAmplitudeSpinner));
        waveDetails.add(row("Wave length (0 = automatic)", hatchWavelengthSpinner));
        dotDetails.add(row("Dot size (0 = automatic)", dotRadiusSpinner));
        hatchDetails.add(waveDetails); hatchDetails.add(dotDetails);
        hatchDetails.add(disclosure("Per-colour fill settings", hatchOverridesPanel));
        fill.add(indented(hatchDetails));
        add(fill); add(gap());

        JPanel faster = card("Plot faster");
        faster.add(new JLabel("Safe choices are already selected by the Recommended preset."));
        faster.add(optimizeCheck);
        faster.add(disclosure("Fine-tune optimization", optimizationFineTuning()));
        add(faster); add(gap());

        warningLabel.setForeground(new Color(173, 93, 0));
        warningLabel.setVisible(false);
        add(warningLabel); add(gap());

        advancedToggle.setAlignmentX(LEFT_ALIGNMENT);
        advancedToggle.setToolTipText("Show layout, colour and geometry controls intended for experienced users");
        advancedPanel.add(row("Stroke width override (0 = keep)", strokeWidthField));
        advancedPanel.add(row("Limit colours (hex, comma-separated)", paletteField));
        advancedPanel.add(row("Hide colours (hex, comma-separated)", hiddenLayersField));
        advancedPanel.add(row("General path smoothing (0 = off)", simplifyToleranceSpinner));
        advancedPanel.add(row("Rotate artwork (degrees)", rotateSpinner));
        advancedPanel.add(row("Crop to", cropCombo));
        advancedPanel.add(row("Custom crop (W x H pixels)", cropCustomField));
        advancedPanel.add(printStatsCheck);
        advancedPanel.setBorder(BorderFactory.createEmptyBorder(6, 14, 0, 0));
        advancedPanel.setVisible(false);
        add(advancedToggle); add(advancedPanel);

        cropCustomField.setEnabled(false);
        cropCombo.addActionListener(e -> cropCustomField.setEnabled("Custom".equals(cropCombo.getSelectedItem())));
        advancedToggle.addActionListener(e -> { advancedPanel.setVisible(advancedToggle.isSelected()); revalidate(); });
        presetCombo.addActionListener(e -> { if (!applying) applyPreset((Preset)presetCombo.getSelectedItem()); });
        installListeners();

        if (savedConfig != null) applyConfig(savedConfig); else applyPreset(Preset.RECOMMENDED);
        fillColors.forEach(hatchOverridesPanel::addIfAbsent);
        updateContext();
    }

    private JPanel optimizationFineTuning() {
        JPanel p=verticalPanel(); p.add(linesimplifyCheck); p.add(indented(row("Point removal strength", slider(linesimplifyToleranceSpinner, 0, 5, 100))));
        p.add(linemergeCheck); p.add(indented(row("Maximum join distance", slider(linemergeToleranceSpinner, 0, 10, 100))));
        p.add(linesortCheck);p.add(reloopCheck);p.add(linesortTwoOptCheck); return p;
    }

    private void applyPreset(Preset preset) {
        if (preset == null || preset == Preset.CUSTOM) return;
        applying=true;
        clearProcessing();
        switch (preset) {
            case RECOMMENDED -> optimizeCheck.setSelected(true);
            case FAST -> { optimizeCheck.setSelected(true); linesortTwoOptCheck.setSelected(true);
                linesimplifyCheck.setSelected(true); linesimplifyToleranceSpinner.setValue(.5);
                linemergeCheck.setSelected(true); linemergeToleranceSpinner.setValue(.75); }
            case CLEAN -> { optimizeCheck.setSelected(true);
                linesimplifyCheck.setSelected(true); linesimplifyToleranceSpinner.setValue(.25); }
            case HAND_DRAWN -> { handdrawnCheck.setSelected(true); optimizeCheck.setSelected(true); }
            case FILLED -> hatchCheck.setSelected(true);
            default -> { }
        }
        presetCombo.setSelectedItem(preset); presetHelp.setText(preset.help); applying=false;
        updateContext(); fireChanged();
    }

    private void clearProcessing() {
        hatchCheck.setSelected(false); handdrawnCheck.setSelected(false); optimizeCheck.setSelected(false);
        linesimplifyCheck.setSelected(false); linemergeCheck.setSelected(false); linesortCheck.setSelected(false);
        linesortTwoOptCheck.setSelected(false); reloopCheck.setSelected(false);
        simplifyToleranceSpinner.setValue(0d); rotateSpinner.setValue(0d); cropCombo.setSelectedItem("None");
    }

    private void installListeners() {
        ActionListener action=e->controlChanged();
        ChangeListener change=e->controlChanged();
        for (AbstractButton b : List.of(hatchCheck, handdrawnCheck, optimizeCheck, linesimplifyCheck,
                linemergeCheck, linesortCheck, linesortTwoOptCheck, reloopCheck, printStatsCheck)) b.addActionListener(action);
        hatchPatternCombo.addActionListener(action); cropCombo.addActionListener(action);
        for (JSpinner s : List.of(hatchAngleSpinner,hatchGapSpinner,hatchAmplitudeSpinner,hatchWavelengthSpinner,
                dotRadiusSpinner,handdrawnMagnitudeSpinner,handdrawnSegmentSpinner,handdrawnWavelengthSpinner,
                handdrawnSeedSpinner,linesimplifyToleranceSpinner,linemergeToleranceSpinner,
                simplifyToleranceSpinner,rotateSpinner)) s.addChangeListener(change);
        DocumentListener docs=new DocumentListener(){public void insertUpdate(DocumentEvent e){controlChanged();}
            public void removeUpdate(DocumentEvent e){controlChanged();} public void changedUpdate(DocumentEvent e){controlChanged();}};
        for(JTextField f:List.of(strokeWidthField,paletteField,hiddenLayersField,cropCustomField))f.getDocument().addDocumentListener(docs);
    }

    private void controlChanged() {
        if (applying) return;
        presetCombo.setSelectedItem(Preset.CUSTOM); presetHelp.setText(Preset.CUSTOM.help);
        updateContext(); fireChanged();
    }

    private void updateContext() {
        hatchDetails.setVisible(hatchCheck.isSelected()); handdrawnDetails.setVisible(handdrawnCheck.isSelected());
        String pattern=selectedPattern(); waveDetails.setVisible("wave".equals(pattern)||"zigzag".equals(pattern));
        dotDetails.setVisible("dot".equals(pattern));
        boolean conflict=handdrawnCheck.isSelected() && (linesimplifyCheck.isSelected() || ((Number)simplifyToleranceSpinner.getValue()).doubleValue()>0);
        warningLabel.setText("Simplifying after adding wobble can straighten the hand-drawn effect. Turn off point removal for the most natural result.");
        warningLabel.setVisible(conflict); revalidate();
    }

    void addChangeListener(Runnable listener) { changeListeners.add(listener); }
    private void fireChanged() { changeListeners.forEach(Runnable::run); }
    boolean isHatchEnabled(){return hatchCheck.isSelected();}
    void addHatchActionListener(ActionListener l){hatchCheck.addActionListener(l);}
    boolean isHanddrawnEnabled(){return handdrawnCheck.isSelected();}
    void addHanddrawnActionListener(ActionListener l){handdrawnCheck.addActionListener(l);}
    boolean hasProcessingEnabled(){return isHatchEnabled()||isHanddrawnEnabled()||optimizeCheck.isSelected()||linesimplifyCheck.isSelected()
            ||linemergeCheck.isSelected()||linesortCheck.isSelected()||reloopCheck.isSelected()
            ||((Number)simplifyToleranceSpinner.getValue()).doubleValue()>0||((Number)rotateSpinner.getValue()).doubleValue()!=0
            ||!"None".equals(cropCombo.getSelectedItem());}
    String selectedPresetLabel(){return ((Preset)presetCombo.getSelectedItem()).label;}
    boolean conflictWarningVisible(){return warningLabel.isVisible();}
    boolean hatchDetailsVisible(){return hatchDetails.isVisible();}
    void selectPreset(Preset preset){applyPreset(preset);}
    void resetToOriginal(){applyPreset(Preset.UNCHANGED);}

    void setHanddrawnOptions(boolean enabled,double magnitude,double segment,double wavelength,int seed){
        applying=true;handdrawnCheck.setSelected(enabled);handdrawnMagnitudeSpinner.setValue(magnitude);
        handdrawnSegmentSpinner.setValue(segment);handdrawnWavelengthSpinner.setValue(wavelength);handdrawnSeedSpinner.setValue(seed);
        applying=false;controlChanged();}
    void setHatchOverride(String color,String pattern,double angle,double gap){hatchOverridesPanel.setOverride(color,pattern,angle,gap);}

    Config buildConfig() {
        float strokeWidth;
        try { strokeWidth=Float.parseFloat(strokeWidthField.getText().trim()); }
        catch(NumberFormatException e){throw new IllegalArgumentException("Stroke width must be a number.");}
        List<Color> palette=parseColors(paletteField.getText());
        List<String> hidden=Arrays.stream(hiddenLayersField.getText().split(",")).map(String::trim).filter(s->!s.isEmpty()).map(String::toLowerCase).collect(Collectors.toList());
        String pattern=selectedPattern();
        HatchStyle style=new HatchStyle(num(hatchAngleSpinner),num(hatchGapSpinner),pattern,num(hatchAmplitudeSpinner),num(hatchWavelengthSpinner),num(dotRadiusSpinner));
        Rectangle2D crop=PaperSizes.resolve((String)cropCombo.getSelectedItem(),cropCustomField.getText());
        Config c=new Config.Builder().inputPath("").outputPath("").strokeWidth(strokeWidth).palette(palette)
                .enableHatching(hatchCheck.isSelected()).globalStyle(style).overrides(hatchOverridesPanel.buildOverrides())
                .strokeWidthOverrides(Map.of()).hiddenLayers(hidden).noHatchColors(List.of())
                .simplifyTolerance(num(simplifyToleranceSpinner)).hatchPattern(pattern).rotationDegrees(num(rotateSpinner))
                .printStats(printStatsCheck.isSelected()).cropBounds(crop).optimizePaths(optimizeCheck.isSelected())
                .linesimplify(linesimplifyCheck.isSelected()).linesimplifyTolerance(num(linesimplifyToleranceSpinner))
                .linemerge(linemergeCheck.isSelected()).linemergeTolerance(num(linemergeToleranceSpinner))
                .linesort(linesortCheck.isSelected()).linesortTwoOpt(linesortTwoOptCheck.isSelected()).reloop(reloopCheck.isSelected())
                .handdrawn(handdrawnCheck.isSelected()).handdrawnMagnitude(num(handdrawnMagnitudeSpinner))
                .handdrawnSegment(num(handdrawnSegmentSpinner)).handdrawnWavelength(num(handdrawnWavelengthSpinner))
                .handdrawnSeed(((Number)handdrawnSeedSpinner.getValue()).longValue()).build();
        savedConfig=c; return c;
    }

    void applyConfig(Config c) {
        if(c==null)return; applying=true;
        strokeWidthField.setText(Float.toString(c.strokeWidth())); paletteField.setText(c.palette().stream().map(ToolboxOptionsPanel::hex).collect(Collectors.joining(", ")));
        hiddenLayersField.setText(String.join(", ",c.hiddenLayers())); hatchCheck.setSelected(c.enableHatching()); selectPattern(c.hatchPattern());
        hatchAngleSpinner.setValue(c.globalStyle().angle()); hatchGapSpinner.setValue(c.globalStyle().gap());
        hatchAmplitudeSpinner.setValue(c.globalStyle().amplitude()); hatchWavelengthSpinner.setValue(c.globalStyle().wavelength()); dotRadiusSpinner.setValue(c.globalStyle().dotRadius());
        c.overrides().forEach((color,s)->hatchOverridesPanel.setOverride(color,s.patternName(),s.angle(),s.gap()));
        simplifyToleranceSpinner.setValue(c.simplifyTolerance()); rotateSpinner.setValue(c.rotationDegrees());
        applyCrop(c.cropBounds());
        optimizeCheck.setSelected(c.optimizePaths()); linesimplifyCheck.setSelected(c.linesimplify()); linesimplifyToleranceSpinner.setValue(c.linesimplifyTolerance());
        linemergeCheck.setSelected(c.linemerge()); linemergeToleranceSpinner.setValue(c.linemergeTolerance()); linesortCheck.setSelected(c.linesort());
        linesortTwoOptCheck.setSelected(c.linesortTwoOpt()); reloopCheck.setSelected(c.reloop()); printStatsCheck.setSelected(c.printStats());
        handdrawnCheck.setSelected(c.handdrawn()); handdrawnMagnitudeSpinner.setValue(c.handdrawnMagnitude()); handdrawnSegmentSpinner.setValue(c.handdrawnSegment());
        handdrawnWavelengthSpinner.setValue(c.handdrawnWavelength()); handdrawnSeedSpinner.setValue((int)Math.min(Integer.MAX_VALUE,c.handdrawnSeed()));
        Preset detected=detectPreset();presetCombo.setSelectedItem(detected);presetHelp.setText(detected.help);applying=false;updateContext();
    }

    private Preset detectPreset(){
        boolean layoutChanged=num(simplifyToleranceSpinner)>0||num(rotateSpinner)!=0||!"None".equals(cropCombo.getSelectedItem());
        boolean baseClear=!hatchCheck.isSelected()&&!handdrawnCheck.isSelected()&&!linesortCheck.isSelected()&&!reloopCheck.isSelected()&&!layoutChanged;
        if(baseClear&&!optimizeCheck.isSelected()&&!linesimplifyCheck.isSelected()&&!linemergeCheck.isSelected()&&!linesortTwoOptCheck.isSelected())return Preset.UNCHANGED;
        if(baseClear&&optimizeCheck.isSelected()&&!linesimplifyCheck.isSelected()&&!linemergeCheck.isSelected()&&!linesortTwoOptCheck.isSelected())return Preset.RECOMMENDED;
        if(baseClear&&optimizeCheck.isSelected()&&linesimplifyCheck.isSelected()&&close(num(linesimplifyToleranceSpinner),.5)
                &&linemergeCheck.isSelected()&&close(num(linemergeToleranceSpinner),.75)&&linesortTwoOptCheck.isSelected())return Preset.FAST;
        if(baseClear&&optimizeCheck.isSelected()&&linesimplifyCheck.isSelected()&&close(num(linesimplifyToleranceSpinner),.25)&&!linemergeCheck.isSelected())return Preset.CLEAN;
        if(!hatchCheck.isSelected()&&handdrawnCheck.isSelected()&&optimizeCheck.isSelected()&&!linesimplifyCheck.isSelected()&&!linemergeCheck.isSelected()&&!layoutChanged)return Preset.HAND_DRAWN;
        if(hatchCheck.isSelected()&&!handdrawnCheck.isSelected()&&!optimizeCheck.isSelected()&&!linesimplifyCheck.isSelected()&&!linemergeCheck.isSelected()&&!layoutChanged)return Preset.FILLED;
        return Preset.CUSTOM;
    }

    private String selectedPattern(){Pattern p=(Pattern)hatchPatternCombo.getSelectedItem();return p==null?"linear":p.id;}
    private void applyCrop(Rectangle2D crop){
        if(crop==null){cropCombo.setSelectedItem("None");return;}
        if(close(crop.getWidth(),PaperSizes.A4_WIDTH)&&close(crop.getHeight(),PaperSizes.A4_HEIGHT))cropCombo.setSelectedItem("A4");
        else if(close(crop.getWidth(),PaperSizes.LETTER_WIDTH)&&close(crop.getHeight(),PaperSizes.LETTER_HEIGHT))cropCombo.setSelectedItem("Letter");
        else{cropCombo.setSelectedItem("Custom");cropCustomField.setText(crop.getWidth()+"x"+crop.getHeight());}
    }
    private static boolean close(double a,double b){return Math.abs(a-b)<.001;}
    private void selectPattern(String id){for(Pattern p:PATTERNS)if(p.id.equals(id)){hatchPatternCombo.setSelectedItem(p);return;}}
    private static double num(JSpinner s){return ((Number)s.getValue()).doubleValue();}
    private static JSpinner spinner(double value,double min,double max,double step){return new JSpinner(new SpinnerNumberModel(value,min,max,step));}
    private static List<Color> parseColors(String text){if(text.trim().isEmpty())return List.of();List<Color> out=new ArrayList<>();for(String part:text.split(",")){
        try{out.add(Color.decode(part.trim()));}catch(NumberFormatException e){throw new IllegalArgumentException("Invalid palette color: "+part.trim());}}return out;}
    private static String hex(Color c){return String.format("#%06x",c.getRGB()&0xffffff);}

    private static JPanel verticalPanel(){JPanel p=new JPanel();p.setOpaque(false);p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));return p;}
    private static JPanel card(String title){JPanel p=verticalPanel();p.setAlignmentX(LEFT_ALIGNMENT);p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(title),BorderFactory.createEmptyBorder(5,8,7,8)));return p;}
    private static Component gap(){return Box.createVerticalStrut(6);}
    private static JPanel row(String label,JComponent field){JPanel p=new JPanel(new BorderLayout(10,0));p.setOpaque(false);p.setMaximumSize(new Dimension(Integer.MAX_VALUE,field.getPreferredSize().height+8));
        JLabel l=new JLabel(label);l.setLabelFor(field);p.add(l,BorderLayout.CENTER);p.add(field,BorderLayout.EAST);return p;}
    private static JComponent slider(JSpinner spinner,double min,double max,int scale){
        JSlider slider=new JSlider((int)Math.round(min*scale),(int)Math.round(max*scale),(int)Math.round(num(spinner)*scale));
        slider.setPreferredSize(new Dimension(130,24));slider.setToolTipText("Drag for a quick visual adjustment; type an exact value at the right");
        final boolean[] syncing={false};slider.addChangeListener(e->{if(syncing[0])return;syncing[0]=true;spinner.setValue(slider.getValue()/(double)scale);syncing[0]=false;});
        spinner.addChangeListener(e->{if(syncing[0])return;syncing[0]=true;int v=(int)Math.round(num(spinner)*scale);slider.setValue(Math.max(slider.getMinimum(),Math.min(slider.getMaximum(),v)));syncing[0]=false;});
        JPanel p=new JPanel(new BorderLayout(4,0));p.setOpaque(false);p.add(slider,BorderLayout.CENTER);p.add(spinner,BorderLayout.EAST);return p;
    }
    private static JPanel indented(JComponent c){JPanel p=new JPanel(new BorderLayout());p.setOpaque(false);p.setBorder(BorderFactory.createEmptyBorder(3,18,0,0));p.add(c);return p;}
    private static JComponent disclosure(String title,JComponent content){JPanel p=verticalPanel();JToggleButton b=new JToggleButton(title);b.setAlignmentX(LEFT_ALIGNMENT);content.setVisible(false);
        b.addActionListener(e->{content.setVisible(b.isSelected());p.revalidate();});p.add(b);p.add(indented(content));return p;}
    private static JLabel helpLabel(){JLabel l=new JLabel();l.setAlignmentX(LEFT_ALIGNMENT);return l;}
}
