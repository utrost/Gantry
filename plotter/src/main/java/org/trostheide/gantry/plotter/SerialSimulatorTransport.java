package org.trostheide.gantry.plotter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/**
 * In-memory GRBL subset beneath the production serial backend. Acknowledged XY/Z moves
 * execute on a separate planner worker; realtime status reports observed, not planned, position.
 * Motion/dwell run at 20x speed for practice. This is not a firmware or actuator emulator.
 */
public final class SerialSimulatorTransport implements SerialTransport {
    public enum Fault { DROP_ACK, DELAY_ACK, REJECT_COMMAND, ALARM, DISCONNECT, RESET }
    public record Snapshot(boolean open, String state, double x, double y, double z,
                           boolean penDown, int queued) { }
    private enum Ack { NORMAL, DROP, DELAY }
    @FunctionalInterface private interface Action { void run(long generation) throws InterruptedException; }
    private record Task(Action action, Ack ack, boolean ackAtStart, boolean ackAtEnd, long generation) { }
    private static final Pattern WORD = Pattern.compile("([A-Z])([-+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+))");
    private static final int PLANNER_CAPACITY = 16;
    private static final double SPEEDUP = 20;

    private final Object lock = new Object();
    private final BlockingQueue<String> responses = new LinkedBlockingQueue<>();
    private final ArrayDeque<Task> planner = new ArrayDeque<>();
    private final ArrayDeque<String> delayedAcks = new ArrayDeque<>();
    private final List<String> commands = new ArrayList<>();
    private final Map<String, CountDownLatch> signals = new ConcurrentHashMap<>();
    private final Map<Integer, Double> settings = new TreeMap<>();
    private final StringBuilder input = new StringBuilder();
    private final GcodeOptions options;
    private final Thread worker;
    private volatile boolean open = true;
    private boolean absolute = true, held, alarm, suspended, penDown, suppressStatus, discardLine;
    private double x, y, z, offsetX, offsetY, offsetZ, feed;
    private int override = 100;
    private long generation;
    private Task active;
    private Fault nextFault;
    private String frozenStatus;

    public SerialSimulatorTransport(GcodeOptions options) {
        this.options = Objects.requireNonNull(options);
        feed = options.feedRateTravel;
        settings.putAll(Map.of(20,0.0,21,0.0,22,1.0,23,3.0,100,80.0,101,80.0,
                110,6000.0,111,6000.0,112,3000.0));
        settings.put(13,0.0);
        settings.put(130,options.machineWidth); settings.put(131,options.machineHeight);
        responses.add("Grbl 1.1h [Gantry serial simulator; no hardware]");
        worker = new Thread(this::execute, "grbl-simulator-planner");
        worker.setDaemon(true); worker.start();
    }

    @Override public boolean isOpen() { return open; }
    @Override public String readLine() throws IOException {
        try { return responses.poll(25, TimeUnit.MILLISECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Simulator read interrupted", e); }
    }
    @Override public void writeBytes(byte[] bytes) throws IOException {
        synchronized (lock) {
            if (!open) throw new IOException("Serial simulator is disconnected");
            for (byte value : bytes) {
                int b = value & 255;
                switch (b) {
                    case '?' -> { if (!suppressStatus) responses.add(frozenStatus == null ? status() : frozenStatus); }
                    case '!' -> { held = true; lock.notifyAll(); }
                    case '~' -> { held = false; lock.notifyAll(); }
                    case 0x18 -> reset();
                    case 0x85 -> { generation++; planner.clear(); held=false; lock.notifyAll(); }
                    case 0x90 -> override=100;
                    case 0x91 -> override=Math.min(200,override+10);
                    case 0x92 -> override=Math.max(10,override-10);
                    case '\r' -> { }
                    case '\n' -> {
                        String line=input.toString().trim(); input.setLength(0);
                        if (!discardLine && !line.isEmpty()) receive(line);
                        discardLine=false;
                    }
                    default -> {
                        if (discardLine) continue;
                        if (b < 32 || b > 126 || input.length() >= 512) {
                            input.setLength(0); discardLine=true; responses.add("error:24");
                        } else input.append((char)b);
                    }
                }
            }
        }
    }

    private void receive(String original) {
        String line=original.toUpperCase(Locale.ROOT).replaceAll("\\([^)]*\\)", "").split(";",2)[0].replaceAll("\\s+", "");
        commands.add(original);
        Fault fault=nextFault;
        boolean motion=line.matches("(?:G[01](?:[XYZF].*)?|\\$H)");
        if (fault == Fault.ALARM || fault == Fault.DISCONNECT || fault == Fault.RESET) {
            if (motion) {
                nextFault=null;
                if (fault==Fault.ALARM) raiseAlarm();
                else if (fault==Fault.DISCONNECT) close();
                else reset();
                signal(original); return;
            }
            fault=null; // wait for a motion command; realtime polls never consume faults
        } else nextFault=null;
        if (fault==Fault.REJECT_COMMAND) { responses.add("error:20"); signal(original); return; }
        Ack ack=fault==Fault.DROP_ACK?Ack.DROP:fault==Fault.DELAY_ACK?Ack.DELAY:Ack.NORMAL;
        try {
            if (line.equals("$X")) { alarm=false; ack(ack); lock.notifyAll(); }
            else if (line.equals("$$")) { settings.forEach((key,value)->responses.add("$"+key+"="+value)); ack(ack); }
            else if (line.equals("$I")) { responses.add("[VER:1.1h:Gantry serial simulator]"); ack(ack); }
            else if (line.equals("$G")) { responses.add("[GC:G0 G54 G17 G21 "+(absolute?"G90":"G91")+" G94 M5]"); ack(ack); }
            else if (line.matches("\\$[0-9]+=.*")) setting(line,ack);
            else if (alarm) responses.add("error:9");
            else if (line.equals("$H")) {
                if (settings.get(22)!=1) responses.add("error:5");
                else enqueue(g -> home(g), ack, true);
            }
            else parse(line,ack);
        } catch (IllegalArgumentException rejected) { responses.add("error:20"); }
        signal(original);
    }

    private void parse(String line, Ack ack) {
        if (line.isEmpty()) { ack(ack); return; }
        Matcher matcher=WORD.matcher(line);
        if (!matcher.find() || matcher.start()!=0) throw new IllegalArgumentException();
        String family=matcher.group(1); double code=Double.parseDouble(matcher.group(2));
        Map<String,Double> words=new HashMap<>(); int end=matcher.end();
        while (matcher.find()) {
            if (matcher.start()!=end || words.put(matcher.group(1),Double.parseDouble(matcher.group(2)))!=null)
                throw new IllegalArgumentException();
            end=matcher.end();
        }
        if (end!=line.length() || words.values().stream().anyMatch(v->!Double.isFinite(v))) throw new IllegalArgumentException();
        if (family.equals("G") && (code==90 || code==91 || code==21 || code==94)) {
            requireWords(words,Set.of());
            if (code==90 || code==91) absolute=code==90;
            ack(ack);
        } else if (family.equals("G") && (code==0 || code==1)) {
            requireWords(words,Set.of("X","Y","Z","F"));
            double speed=words.getOrDefault("F",feed);
            if (!Double.isFinite(speed) || speed<=0) throw new IllegalArgumentException();
            feed=speed; boolean abs=absolute;
            enqueue(g -> move(words,abs,speed,g), ack, false);
        } else if (family.equals("G") && code==4) {
            requireWords(words,Set.of("P"));
            if (!words.containsKey("P")) throw new IllegalArgumentException();
            double seconds=words.get("P");
            if(seconds<0 || seconds>3600)throw new IllegalArgumentException();
            // GRBL dwell synchronizes the planner and acknowledges after execution, including P0.
            enqueue(g -> delay(seconds*1000/SPEEDUP,g), ack, true);
        } else if (family.equals("G") && code==92) {
            requireWords(words,Set.of("X","Y","Z"));
            enqueue(g -> { synchronized(lock) {
                check(g);
                if(words.containsKey("X"))offsetX=x-words.get("X");
                if(words.containsKey("Y"))offsetY=y-words.get("Y");
                if(words.containsKey("Z"))offsetZ=z-words.get("Z");
            }}, ack, true);
        } else if (family.equals("M") && (code==280 || code==3 || code==5)) {
            requireWords(words,code==280?Set.of("P","S"):Set.of("S"));
            if(code==280 && words.getOrDefault("P",0.0)!=options.servoPin)throw new IllegalArgumentException();
            double value=words.getOrDefault("S",(double)options.penServoUp);
            if (code!=5 && (!words.containsKey("S") || (value!=options.penServoUp && value!=options.penServoDown)))
                throw new IllegalArgumentException();
            enqueue(g -> { synchronized(lock) { check(g); penDown=code!=5 && value==options.penServoDown; } },ack,false);
        } else throw new IllegalArgumentException();
    }

    private static void requireWords(Map<String,Double> words, Set<String> allowed) {
        if(!allowed.containsAll(words.keySet()))throw new IllegalArgumentException();
    }
    private void setting(String line,Ack ack) {
        String[] parts=line.substring(1).split("=",2);int key=Integer.parseInt(parts[0]);double value=Double.parseDouble(parts[1]);
        if(!settings.containsKey(key)||!Double.isFinite(value))throw new IllegalArgumentException();
        if(key>=100 ? value<=0 : value!=Math.rint(value)||value<0||value>(key==23?7:1))throw new IllegalArgumentException();
        settings.put(key,value); ack(ack);
    }
    private void enqueue(Action action,Ack ack,boolean ackAtEnd) {
        boolean deferred=!ackAtEnd && planner.size()>=PLANNER_CAPACITY;
        planner.add(new Task(action,ack,deferred,ackAtEnd,generation));
        if(!deferred&&!ackAtEnd)ack(ack);
        lock.notifyAll();
    }
    private void ack(Ack mode) {
        if(mode==Ack.NORMAL)responses.add("ok");
        else if(mode==Ack.DELAY)delayedAcks.add("ok");
    }
    private void signal(String command) { signals.computeIfAbsent(command,k->new CountDownLatch(1)).countDown(); }

    private void execute() {
        while(open) {
            Task task;
            synchronized(lock) {
                try { while(open&&(planner.isEmpty()||held||suspended||alarm))lock.wait(); }
                catch(InterruptedException e) { if(!open)return; continue; }
                if(!open)return;
                task=planner.remove();active=task;
                if(task.ackAtStart())ack(task.ack());
            }
            try {
                task.action().run(task.generation());
                synchronized(lock) { if(open && task.generation()==generation && task.ackAtEnd())ack(task.ack()); }
            } catch(CancellationException cancelled) { /* reset/Stop discarded this generation */ }
            catch(InterruptedException interrupted) { if(!open)return; }
            catch(RuntimeException failed) { synchronized(lock) { if(open)raiseAlarm(); } }
            finally { synchronized(lock) { if(active==task)active=null;lock.notifyAll(); } }
        }
    }

    private void move(Map<String,Double> words,boolean absolute,double speed,long gen) throws InterruptedException {
        double fromX,fromY,fromZ,toX,toY,toZ;
        synchronized(lock) {
            check(gen);fromX=x;fromY=y;fromZ=z;
            toX=target(words,"X",x,offsetX,absolute);toY=target(words,"Y",y,offsetY,absolute);toZ=target(words,"Z",z,offsetZ,absolute);
            if(settings.get(20)==1 && (toX<0||toY<0||toX>settings.get(130)||toY>settings.get(131))) {raiseAlarm();return;}
        }
        double distance=Math.sqrt(Math.pow(toX-fromX,2)+Math.pow(toY-fromY,2)+Math.pow(toZ-fromZ,2));
        double remaining=Math.max(2,distance/speed*60000/SPEEDUP), elapsed=0;
        while(elapsed<remaining) {
            synchronized(lock) { while(open&&gen==generation&&(held||suspended))lock.wait(); check(gen); }
            long tick=(long)Math.min(10,Math.ceil(remaining-elapsed));Thread.sleep(tick);
            synchronized(lock) {
                check(gen);if(held||suspended)continue;
                elapsed=Math.min(remaining,elapsed+tick*override/100.0);
                double fraction=elapsed/remaining;
                x=fromX+(toX-fromX)*fraction;y=fromY+(toY-fromY)*fraction;z=fromZ+(toZ-fromZ)*fraction;
                if(words.containsKey("Z")) {
                    double workZ=z-offsetZ;
                    penDown=options.zDown<options.zUp ? workZ<options.zUp-.001 : workZ>options.zUp+.001;
                }
            }
        }
    }
    private static double target(Map<String,Double> words,String axis,double current,double offset,boolean absolute) {
        return words.containsKey(axis)?(absolute?words.get(axis)+offset:current+words.get(axis)):current;
    }
    private void delay(double millis,long gen) throws InterruptedException {
        double elapsed=0;
        while(elapsed<millis) {
            synchronized(lock) { while(open&&gen==generation&&(held||suspended))lock.wait();check(gen); }
            long tick=(long)Math.min(10,Math.ceil(millis-elapsed));Thread.sleep(tick);
            synchronized(lock) {check(gen);if(!held&&!suspended)elapsed+=tick;}
        }
        synchronized(lock) {check(gen);}
    }
    private void home(long gen) throws InterruptedException {
        move(Map.of("X",-offsetX,"Y",-offsetY,"Z",-offsetZ),true,options.feedRateTravel,gen);
        synchronized(lock) {check(gen);x=0;y=0;z=0;penDown=false;}
    }
    private void check(long gen) { if(!open||generation!=gen||alarm)throw new CancellationException("Simulator motion invalidated"); }
    private String state() { return !open?"Disconnected":alarm?"Alarm":held?"Hold:0":active!=null||!planner.isEmpty()?"Run":"Idle"; }
    private String status() {
        double unit=settings.get(13)==1 ? 25.4 : 1;
        return String.format(Locale.ROOT,"<%s|MPos:%.3f,%.3f,%.3f|WCO:%.3f,%.3f,%.3f|FS:0,0|Ov:%d,100,100>",
                state(),x/unit,y/unit,z/unit,offsetX/unit,offsetY/unit,offsetZ/unit,override);
    }
    private void reset() {
        generation++;planner.clear();delayedAcks.clear();responses.clear();input.setLength(0);discardLine=false;
        held=false;alarm=false;suspended=false;absolute=true;offsetX=0;offsetY=0;offsetZ=0;penDown=false;override=100;
        responses.add("Grbl 1.1h [Gantry simulator reset]");lock.notifyAll();
    }
    private void raiseAlarm() {
        generation++;planner.clear();delayedAcks.clear();alarm=true;held=false;
        responses.add("ALARM:9");lock.notifyAll();
    }
    @Override public void close() {
        synchronized(lock) {open=false;generation++;planner.clear();delayedAcks.clear();lock.notifyAll();}
        worker.interrupt();
    }

    /** Deterministic fault/control seams for software acceptance; none communicate with hardware. */
    public void armFault(Fault fault) { synchronized(lock) {if(!open)throw new IllegalStateException("Disconnected");nextFault=Objects.requireNonNull(fault);} }
    public void pauseExecution() { synchronized(lock) {suspended=true;} }
    public void resumeExecution() { synchronized(lock) {suspended=false;lock.notifyAll();} }
    public void releaseDelayedAcknowledgements() { synchronized(lock) {while(!delayedAcks.isEmpty())responses.add(delayedAcks.remove());} }
    public void freezeStatusReports() { synchronized(lock) {frozenStatus=status();} }
    public void resumeStatusReports() { synchronized(lock) {frozenStatus=null;suppressStatus=false;} }
    public void suppressStatusReports() { synchronized(lock) {suppressStatus=true;} }
    public boolean awaitCommand(String command,long timeout,TimeUnit unit) throws InterruptedException {
        return signals.computeIfAbsent(command,k->new CountDownLatch(1)).await(timeout,unit);
    }
    public List<String> commands() { synchronized(lock) {return List.copyOf(commands);} }
    public Snapshot snapshot() { synchronized(lock) {return new Snapshot(open,state(),x-offsetX,y-offsetY,z-offsetZ,penDown,planner.size());} }
}
