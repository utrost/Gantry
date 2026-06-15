package org.trostheide.gantry.pipeline.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.trostheide.gantry.model.ProcessorOutput;

import java.io.File;
import java.io.IOException;

/** Reads and writes the polymorphic {@link ProcessorOutput} command JSON used as the save/interchange format. */
public final class ProcessorOutputIO {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    private ProcessorOutputIO() {
    }

    public static ProcessorOutput load(File file) throws IOException {
        return MAPPER.readValue(file, ProcessorOutput.class);
    }

    public static void save(ProcessorOutput output, File file) throws IOException {
        MAPPER.writeValue(file, output);
    }
}
