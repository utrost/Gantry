import org.trostheide.gantry.app.GantryApp;
import org.trostheide.gantry.app.gui.PlotterPanel;
import org.trostheide.gantry.app.plot.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Native Robot keyboard/focus checks on an isolated mock profile. Requires a desktop focus manager. */
public final class KeyboardWorkflowAcceptance extends GuiAcceptance {
    public static void main(String[] args) {
        try {
            Path root = Path.of(args[0]).toAbsolutePath();
            profile=Files.createTempDirectory("gantry-keyboard-review-");
            System.setProperty("gantry.config.file",profile.resolve("config.json").toString());
            GantryConfig config=new GantryConfig();config.mock=true;config.showWelcomeOnStartup=false;config.preflightBeforeStart=false;
            config.machineOrigin="Top-Left";config.canvasAlignment=null;
            ConfigStore.save(config,profile.resolve("config.json").toFile());
            GantryApp.main(new String[0]);
            await(()->{for(Window w:Window.getWindows())if(w instanceof JFrame f&&f.isShowing()){frame=f;panel=(PlotterPanel)f.getContentPane();return true;}return false;});
            edt(()->{frame.setSize(1280,900);frame.toFront();return null;});
            Robot robot=new Robot();robot.setAutoDelay(35);
            menu("Open SVG or Vector Drawing...");choose(root.resolve("docs/samples/multi-colour-layers.svg"));click(dialog("Open SVG or vector drawing"),"Open drawing");
            await(()->project().output()!=null);
            edt(()->{checkUniqueAccelerators(frame.getJMenuBar(),new HashMap<>());return null;});
            JFormattedTextField width=edt(()->((JSpinner.DefaultEditor)findSpinner(panel,"Artwork Width in millimetres").getEditor()).getTextField());
            var before=edt(()->project().placement());
            focus(width);edt(()->{width.setText("80");return null;});key(robot,KeyEvent.VK_ENTER);
            await(()->!project().placement().equals(before));
            edt(()->{check(!jobs().isPlotting(),"Enter edits rather than starting a job");return null;});
            click(panel,"Undo");await(()->project().placement().equals(before));
            focus(width);edt(()->{width.setText("90");return null;});key(robot,KeyEvent.VK_ESCAPE);
            check(edt(()->project().placement().equals(before)),"Escape restores uncommitted edit");
            focus(width);edt(()->{width.setText("85");return null;});key(robot,KeyEvent.VK_TAB);
            await(()->!project().placement().equals(before));click(panel,"Undo");await(()->project().placement().equals(before));
            focus(width);edt(()->{width.setText("12garbage");return null;});key(robot,KeyEvent.VK_ENTER);
            check(edt(()->project().placement().equals(before)),"invalid suffix cannot change placement");key(robot,KeyEvent.VK_ESCAPE);
            System.out.println("PASS native Enter/Tab commit once, Escape reverts, malformed numbers do not apply");
            click(panel,"Connect");await(()->jobs().isConnected());
            double[] position=jobs().backend().queryPosition();
            focus(width);for(int key:new int[]{KeyEvent.VK_LEFT,KeyEvent.VK_UP,KeyEvent.VK_DOWN,KeyEvent.VK_RIGHT})key(robot,key);
            key(robot,KeyEvent.VK_ESCAPE);
            JTable layers=edt(()->findLayers(panel));focus(layers);key(robot,KeyEvent.VK_DOWN);key(robot,KeyEvent.VK_UP);
            double[] after=jobs().backend().queryPosition();
            check(Arrays.equals(position,after),"arrows in fields and layer list never jog");
            // Use real key strokes to distinguish the formerly conflicting commands.
            edt(()->{Object history=field(panel,"plotHistory");((PlotJobHistory)history).add(new PlotJobHistory.Job(Instant.now(),project().output(),config.toPlotSettings()));invoke(panel,"updateReplotMenuItem");return null;});
            focus(width);chord(robot,true,KeyEvent.VK_R);
            click(dialog("Adjust artwork processing"),"Cancel");
            check(edt(()->field(panel,"pendingJob")==null),"Ctrl+Shift+R opens processing, never repeat");
            focus(width);chord(robot,false,KeyEvent.VK_R);
            await(()->field(panel,"pendingJob")!=null);
            click(panel,"Return to artwork");
            System.out.println("PASS unique native repeat/reprocess shortcuts and no jogging from fields/layers");
            edt(()->{Object shell=field(panel,"workspace");((JToggleButton)field(shell,"machineToggle")).doClick();return null;});
            JButton right=edt(()->findButton(panel,"Jog right"));check(right!=null,"manual direction button available");
            focus(right);double start=jobs().backend().queryPosition()[0];key(robot,KeyEvent.VK_RIGHT);
            await(()->!jobs().isMachineBusy()&&jobs().backend().queryPosition()[0]>start);
            // A held mouse jog stops when focus leaves the explicit manual controls.
            focus(right);
            // Feed the production mouse-hold listener directly: this Wayland desktop does not
            // reliably deliver Robot pointer presses. Focus transitions remain native.
            edt(()->{right.dispatchEvent(new MouseEvent(right,MouseEvent.MOUSE_PRESSED,System.currentTimeMillis(),InputEvent.BUTTON1_DOWN_MASK,10,10,1,false,MouseEvent.BUTTON1));return null;});
            await(()->jobs().isMachineBusy());
            focus(width);await(()->!jobs().isMachineBusy());
            edt(()->{right.dispatchEvent(new MouseEvent(right,MouseEvent.MOUSE_RELEASED,System.currentTimeMillis(),0,10,10,1,false,MouseEvent.BUTTON1));return null;});
            check(edt(()->!(boolean)field(field(panel,"jogPanel"),"holding")),"focus loss ends held jog");
            System.out.println("PASS native explicit-focus jogging and focus-loss cancellation (simulated mouse hold, mock only)");
            System.exit(0);
        } catch(Throwable failure){failure.printStackTrace();System.exit(1);}
    }
    private static void checkUniqueAccelerators(MenuElement element,Map<KeyStroke,String> seen){
        if(element instanceof JMenuItem item && item.getAccelerator()!=null){String old=seen.putIfAbsent(item.getAccelerator(),item.getText());check(old==null,"duplicate shortcut: "+old+" / "+item.getText());}
        for(MenuElement child:element.getSubElements())checkUniqueAccelerators(child,seen);
    }
    private static void focus(Component component)throws Exception{edt(()->{frame.toFront();component.requestFocusInWindow();return null;});await(component::isFocusOwner);}
    private static void key(Robot robot,int code){robot.keyPress(code);robot.keyRelease(code);robot.waitForIdle();}
    private static void chord(Robot robot,boolean shift,int code){robot.keyPress(KeyEvent.VK_CONTROL);if(shift)robot.keyPress(KeyEvent.VK_SHIFT);robot.keyPress(code);robot.keyRelease(code);if(shift)robot.keyRelease(KeyEvent.VK_SHIFT);robot.keyRelease(KeyEvent.VK_CONTROL);robot.waitForIdle();}
    private static Object invoke(Object target,String name)throws Exception{Method method=target.getClass().getDeclaredMethod(name);method.setAccessible(true);return method.invoke(target);}
}
