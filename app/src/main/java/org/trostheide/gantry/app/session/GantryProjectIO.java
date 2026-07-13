package org.trostheide.gantry.app.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.File;
import java.io.IOException;

/** JSON persistence for the versioned {@code .gantry} editable project format. */
public final class GantryProjectIO {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule()).enable(SerializationFeature.INDENT_OUTPUT);

    private GantryProjectIO() { }

    public static GantryProject load(File file) throws IOException {
        GantryProject project = MAPPER.readValue(file, GantryProject.class);
        if (project.formatVersion() != GantryProject.CURRENT_VERSION) {
            throw new IOException("Unsupported Gantry project version " + project.formatVersion());
        }
        if (project.output() == null) {
            throw new IOException("Project contains no drawing");
        }
        return project;
    }

    public static void save(GantryProject project, File file) throws IOException {
        MAPPER.writeValue(file, project);
    }
}
