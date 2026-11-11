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
        if(dark) FlatDarkLaf.setup(); else FlatLightLaf.setup();
        boolean compact="Compact".equals(config.uiDensity);
        UIManager.put("defaultFont", new Font(Font.SANS_SERIF,Font.PLAIN,compact?12:13));
        UIManager.put("Component.focusWidth", 2);
        UIManager.put("Button.default.background", new Color(49, 103, 208));
        UIManager.put("Button.default.foreground", Color.WHITE);
        UIManager.put("Button.default.focusedBackground", new Color(42, 88, 180));
        UIManager.put("Button.default.hoverBackground", new Color(40, 86, 177));
        UIManager.put("Button.default.pressedBackground", new Color(32, 69, 145));
        UIManager.put("Button.default.focusColor", dark ? new Color(156, 193, 255) : new Color(26, 68, 147));
        UIManager.put("Button.arc",10); UIManager.put("Component.arc",8); UIManager.put("TextComponent.arc",6);
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
        button.putClientProperty("JButton.buttonType", "roundRect");
        button.putClientProperty("FlatLaf.style", "background: #3167d0; foreground: #ffffff; focusedBackground: #2a58b4; hoverBackground: #2856b1; pressedBackground: #204591");
    }
    static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
    static JPanel stack(JComponent...components){JPanel p=new JPanel();p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));p.setBorder(BorderFactory.createEmptyBorder(10,12,10,12));for(JComponent c:components){c.setAlignmentX(Component.LEFT_ALIGNMENT);p.add(c);p.add(Box.createVerticalStrut(6));}return p;}
}
