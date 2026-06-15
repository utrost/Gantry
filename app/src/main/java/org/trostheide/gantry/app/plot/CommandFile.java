package org.trostheide.gantry.app.plot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.trostheide.gantry.model.ProcessorOutput;

import java.io.File;
import java.io.IOException;

/** Reads the polymorphic {@code ProcessorOutput} JSON command files produced by the SVG pipeline. */
public class CommandFile {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private CommandFile() {
    }

    public static ProcessorOutput load(File file) throws IOException {
        return MAPPER.readValue(file, ProcessorOutput.class);
    }
}
