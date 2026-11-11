package org.trostheide.gantry.app.gui;

import com.formdev.flatlaf.*;
import org.trostheide.gantry.app.plot.GantryConfig;
import javax.swing.*;
import java.awt.*;

/** Shared desktop presentation defaults; never changes document or machine settings. */
public final class WorkspaceStyle {
    private WorkspaceStyle() { }
    public static void install(GantryConfig config) {
        boolean dark = "Dark".equals(config.uiTheme) || ("System".equals(config.uiTheme) && systemDark());
        FlatLaf look = dark ? new FlatDarkLaf() : new FlatLightLaf();
        java.util.Map<String,String> palette = new java.util.HashMap<>();
        palette.put("@background", dark ? "#20242d" : "#f5f6fa");
        palette.put("@foreground", dark ? "#e5eaf4" : "#202937");
        palette.put("@componentBackground", dark ? "#2b303c" : "#e9edf4");
        palette.put("@buttonBackground", dark ? "#303746" : "#e8edf5");
        palette.put("@menuBackground", dark ? "#191d25" : "#ffffff");
        palette.put("@selectionBackground", dark ? "#304e79" : "#d7e6ff");
        palette.put("@selectionForeground", dark ? "#ffffff" : "#173965");
        palette.put("@disabledForeground", dark ? "#798293" : "#737d8f");
        palette.put("Component.borderColor", dark ? "#353d4c" : "#d8dfea");
        palette.put("Component.disabledBorderColor", dark ? "#292f3b" : "#e3e7ee");
        palette.put("Component.focusColor", "#7faaff");
        palette.put("Component.focusedBorderColor", "#7faaff");
        palette.put("Button.disabledBackground", dark ? "#292e38" : "#e9ecf1");
        palette.put("Button.disabledText", dark ? "#929baa" : "#737d8f");
        palette.put("Button.borderWidth", "0");
        palette.put("Button.paintShadow", "false");
        palette.put("Button.toolbar.hoverBackground", dark ? "#333d4d" : "#e2e8f2");
        palette.put("Button.toolbar.pressedBackground", dark ? "#3d4c62" : "#d6e0ef");
        palette.put("Spinner.buttonStyle", "none");
        palette.put("ComboBox.buttonStyle", "none");
        palette.put("Table.background", "$Panel.background");
        palette.put("TableHeader.background", "$Panel.background");
        palette.put("TableHeader.foreground", dark ? "#a9b5c9" : "#526079");
        palette.put("TableHeader.separatorColor", "$Panel.background");
        palette.put("TableHeader.bottomSeparatorColor", dark ? "#333c4c" : "#dce2ed");
        palette.put("ScrollBar.track", "$Panel.background");
        palette.put("ScrollBar.thumb", dark ? "#465165" : "#bbc6d7");
        palette.put("ScrollBar.thumbArc", "999");
        palette.put("ScrollBar.thumbInsets", "2,2,2,2");
        palette.put("ScrollPane.border", "0,0,0,0");
        palette.put("Separator.foreground", dark ? "#343e4f" : "#dce3ee");
        palette.put("ProgressBar.arc", "999");
        palette.put("ProgressBar.foreground", "#5283e8");
        palette.put("Gantry.motion.background", dark ? "#3b3021" : "#fff0d4");
        palette.put("Gantry.motion.foreground", dark ? "#f4bd62" : "#784500");
        palette.put("Gantry.motion.hover", dark ? "#504027" : "#ffe1a5");
        palette.put("Gantry.motion.pressed", dark ? "#64502f" : "#f4ce87");
        palette.put("Gantry.success", dark ? "#83d5aa" : "#21683f");
        palette.put("Gantry.warning", dark ? "#f4bd62" : "#784500");
        palette.put("Gantry.error", dark ? "#ff9696" : "#a82b35");
        look.setExtraDefaults(palette);
        FlatLaf.setup(look);
        boolean compact="Compact".equals(config.uiDensity);
        UIManager.put("defaultFont", new Font(Font.SANS_SERIF,Font.PLAIN,compact?12:13));
        UIManager.put("Component.focusWidth", 2);
        UIManager.put("Button.default.background", new Color(49, 103, 208));
        UIManager.put("Button.default.foreground", Color.WHITE);
        UIManager.put("Button.default.focusedBackground", new Color(42, 88, 180));
        UIManager.put("Button.default.hoverBackground", new Color(40, 86, 177));
        UIManager.put("Button.default.pressedBackground", new Color(32, 69, 145));
        UIManager.put("Button.default.focusColor", dark ? new Color(156, 193, 255) : new Color(26, 68, 147));
        UIManager.put("Button.arc",12); UIManager.put("Component.arc",12); UIManager.put("TextComponent.arc",12);
        UIManager.put("Button.margin",new Insets(compact?3:5,12,compact?3:5,12));
        UIManager.put("TabbedPane.tabHeight",compact?30:38);
        CanvasPalette.setLight("Light".equals(config.canvasTheme));
    }
    static void fitDialog(JDialog dialog,int width,int height){
        Rectangle available=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        dialog.setSize(Math.min(width,available.width-32),Math.min(height,available.height-32));
        dialog.setLocationRelativeTo(dialog.getOwner());
    }
    static boolean systemDark() {
        Object value=Toolkit.getDefaultToolkit().getDesktopProperty("win.lightTheme.on");
        if(value instanceof Boolean light)return !light;
        Object appearance=Toolkit.getDefaultToolkit().getDesktopProperty("apple.awt.application.appearance");
        return appearance!=null && appearance.toString().toLowerCase().contains("dark");
    }
    static JLabel heading(String text){JLabel l=new JLabel(text);l.setFont(l.getFont().deriveFont(Font.BOLD,14f));l.setBorder(BorderFactory.createEmptyBorder(2,0,2,0));l.setAlignmentX(Component.LEFT_ALIGNMENT);return l;}
    static JPanel section(String title,String context){
        JPanel row=new JPanel(new BorderLayout(8,0));
        JLabel label=new JLabel(title.toUpperCase(java.util.Locale.ROOT));
        label.setFont(label.getFont().deriveFont(Font.BOLD,11f));
        label.setForeground(UIManager.getColor("TableHeader.foreground"));
        row.add(label,BorderLayout.WEST);
        if(context!=null){JLabel detail=new JLabel(context);detail.setFont(detail.getFont().deriveFont(11f));
            detail.setForeground(UIManager.getColor("TableHeader.foreground"));row.add(detail,BorderLayout.EAST);}
        row.setBorder(BorderFactory.createEmptyBorder(3,0,3,0));row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }
    static JTextArea note(String text){
        JTextArea note=new JTextArea(text,2,20);note.setEditable(false);note.setFocusable(false);
        note.setLineWrap(true);note.setWrapStyleWord(true);note.setOpaque(false);note.setFont(UIManager.getFont("Label.font"));
        note.setForeground(UIManager.getColor("Label.foreground"));note.setAlignmentX(Component.LEFT_ALIGNMENT);
        note.setMaximumSize(new Dimension(Integer.MAX_VALUE,note.getPreferredSize().height));return note;
    }
    static JButton button(String text,Runnable action){JButton b=new JButton(text);b.addActionListener(e->action.run());b.setAlignmentX(Component.LEFT_ALIGNMENT);return b;}
    static void quiet(AbstractButton button) {
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    static void primary(JButton button) {
        action(button, ActionRole.PREPARE, true);
    }
    enum ActionRole { NEUTRAL, PREPARE, MOTION, PAUSE, STOP }
    /** Explicit consequences, never inferred from translated labels or wired to machine state. */
    static void action(AbstractButton button, ActionRole role, boolean prominent) {
        button.putClientProperty("Gantry.actionRole", role);
        button.putClientProperty("JButton.buttonType", null);
        String style = switch(role) {
            case NEUTRAL -> "";
            case PREPARE -> "background: #3167d0; foreground: #ffffff; focusedBackground: #2a58b4; hoverBackground: #2856b1; pressedBackground: #204591";
            case MOTION, PAUSE -> prominent
                    ? "background: #edb459; foreground: #292013; focusedBackground: #f3c477; hoverBackground: #f3c477; pressedBackground: #d99c3c"
                    : "background: $Gantry.motion.background; foreground: $Gantry.motion.foreground; focusedBackground: $Gantry.motion.hover; hoverBackground: $Gantry.motion.hover; pressedBackground: $Gantry.motion.pressed";
            case STOP -> "background: #ad2935; foreground: #ffffff; focusedBackground: #c33340; hoverBackground: #c33340; pressedBackground: #90212b";
        };
        if (!style.isEmpty()) style += "; disabledBackground: $Button.disabledBackground; disabledText: $Button.disabledText";
        button.putClientProperty("FlatLaf.style", style);
        if (role == ActionRole.MOTION && !Boolean.TRUE.equals(button.getClientProperty("jogDirection"))) {
            button.setIcon(new WorkspaceIcon(WorkspaceIcon.Kind.MOTION));
            button.getAccessibleContext().setAccessibleDescription("Can send machine movement commands. " + button.getText());
        } else if (button.getIcon() instanceof WorkspaceIcon icon && icon.isMotion()) {
            button.setIcon(null);
            button.getAccessibleContext().setAccessibleDescription(null);
        }
    }
    static JButton motion(JButton button) {
        action(button, ActionRole.MOTION, false);
        if(button.getToolTipText()==null)button.setToolTipText("Can move the machine: " + button.getText() + ". Requires a connected, available plotter.");
        return button;
    }
    static ActionRole roleFor(WorkspacePresentation.Action action) {
        return switch(action) {
            case START, CONTINUE, RESUME -> ActionRole.MOTION;
            case PAUSE -> ActionRole.PAUSE;
            case CHECK -> ActionRole.PREPARE;
            default -> ActionRole.NEUTRAL;
        };
    }
    static void status(JComponent component, String key) {
        component.putClientProperty("FlatLaf.style", "foreground: $" + key);
    }
    /** Keep unavailable controls visible and explain why without losing their normal tooltip. */
    static void availability(JComponent control, String reason) {
        if(control.getClientProperty("Gantry.unavailableReason")==null && reason!=null)
            control.putClientProperty("Gantry.availableTooltip",control.getToolTipText());
        String previous=(String)control.getClientProperty("Gantry.unavailableReason");
        control.putClientProperty("Gantry.unavailableReason",reason);
        control.setEnabled(reason==null);
        if(reason!=null)control.setToolTipText(reason);
        else if(previous!=null)control.setToolTipText((String)control.getClientProperty("Gantry.availableTooltip"));
    }
    static JPanel surface(LayoutManager layout) {
        JPanel panel=new JPanel(layout){
            @Override protected void paintComponent(Graphics graphics){
                Graphics2D g=(Graphics2D)graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(UIManager.getColor("Panel.background"));g.fillRoundRect(0,0,getWidth(),getHeight(),18,18);
                g.dispose();super.paintComponent(graphics);
            }
        };
        panel.setOpaque(false);return panel;
    }
    static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
    static JPanel stack(JComponent...components){JPanel p=new JPanel();p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));p.setBorder(BorderFactory.createEmptyBorder(10,12,10,12));for(JComponent c:components){c.setAlignmentX(Component.LEFT_ALIGNMENT);p.add(c);p.add(Box.createVerticalStrut(6));}return p;}
}
