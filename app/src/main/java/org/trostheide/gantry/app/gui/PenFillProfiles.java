package org.trostheide.gantry.app.gui;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.trostheide.gantry.svgtoolbox.PenFill;
import org.trostheide.gantry.app.plot.ConfigStore;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Separate local pen/paper library; no machine settings or connection state. */
final class PenFillProfiles {
    record Profile(String name,double nominalWidthMm,PenFill fill) {
        Profile {if(name==null||name.isBlank()||name.length()>120||!Double.isFinite(nominalWidthMm)||nominalWidthMm<=0||fill==null)throw new IllegalArgumentException("Enter a profile name, positive nominal width and valid fill settings.");}
        @Override public String toString(){return name;}
    }
    private final Path file;
    PenFillProfiles(Path file){this.file=file;}
    static PenFillProfiles local(){return new PenFillProfiles(ConfigStore.guiConfigFile(null).toPath().resolveSibling("pen-paper-profiles.json"));}
    List<Profile> load() throws IOException {
        if(!Files.exists(file))return List.of();
        return List.of(new ObjectMapper().readValue(file.toFile(),Profile[].class));
    }
    void save(Profile profile)throws IOException {
        List<Profile> profiles=new ArrayList<>(load());profiles.removeIf(p->p.name().equals(profile.name()));profiles.add(profile);
        Files.createDirectories(file.toAbsolutePath().getParent());Path tmp=Files.createTempFile(file.toAbsolutePath().getParent(),"pen-profiles-",".tmp");
        try{new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(),profiles);try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}}
        finally{Files.deleteIfExists(tmp);}
    }
}
