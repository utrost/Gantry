package org.trostheide.gantry.model.io;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.*;
import java.net.*;
import static org.junit.jupiter.api.Assertions.*;
class SafeSvgXmlTest {
    @TempDir Path directory;
    private File svg(String text) throws IOException { return Files.writeString(directory.resolve("input.svg"), text).toFile(); }
    @Test void rejectsLocalEntitiesAndExpansion() throws Exception {
        Path secret = Files.writeString(directory.resolve("secret"), "sentinel");
        assertThrows(IOException.class, () -> SafeSvgXml.read(svg("<!DOCTYPE svg [<!ENTITY x SYSTEM '"+secret.toUri()+"'>]><svg>&x;</svg>")));
        assertThrows(IOException.class, () -> SafeSvgXml.read(svg("<!DOCTYPE svg [<!ENTITY x 'expanded'>]><svg>&x;</svg>")));
    }
    @Test void neverRequestsRemoteResources() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(200);
            String uri = "http://localhost:"+server.getLocalPort()+"/remote";
            assertThrows(IOException.class, () -> SafeSvgXml.read(svg("<!DOCTYPE svg SYSTEM '"+uri+"'><svg/>")));
            SafeSvgXml.read(svg("<?xml-stylesheet href='"+uri+"'?><svg xmlns='http://www.w3.org/2000/svg'><image href='"+uri+"'/><style>@import url("+uri+");</style></svg>"));
            assertThrows(SocketTimeoutException.class, server::accept);
        }
    }
    @Test void boundsDepthAndParsesNormalSvg() throws Exception {
        assertThrows(IOException.class, () -> SafeSvgXml.read(svg("<svg>"+"<g>".repeat(300)+"</g>".repeat(300)+"</svg>")));
        assertEquals("svg", SafeSvgXml.read(svg("<svg xmlns='http://www.w3.org/2000/svg'><path d='M0 0L1 1'/></svg>")).getDocumentElement().getLocalName());
    }
}
