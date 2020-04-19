package hudson.plugins.pbc_compile;

import hudson.console.LineTransformationOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.regex.Matcher;

public class PbcCompilerConsoleAnnotator extends LineTransformationOutputStream {
    private final OutputStream out;
    private final Charset charset;
    
    private int numberOfWarnings = 0;
    private int numberOfErrors = 0;
    
    public PbcCompilerConsoleAnnotator(OutputStream out, Charset charset) {
        this.out = out;
        this.charset = charset;
    }
    
    public int getNumberOfWarnings() {
        return numberOfWarnings;
    }
    
    public int getNumberOfErrors() {
        return numberOfErrors;
    }
    
    @Override
    protected void eol(byte[] b, int len) throws IOException {
        String line = charset.decode(ByteBuffer.wrap(b, 0, len)).toString();
        
        // trim off CR/LF from the end
        line = trimEOL(line);
        
        // Error messages handler
        Matcher m = PbcCompileErrorNote.PATTERN.matcher(line);
        if (m.matches()) { // Match the number of warnings
            new PbcCompileErrorNote().encodeTo(out);
            this.numberOfErrors++;
        }
        
        // Warning messages handler
        m = PbcCompileWarningNote.PATTERN.matcher(line);
        if (m.matches()) { // Match the number of warnings
            new PbcCompileWarningNote().encodeTo(out);
            this.numberOfWarnings++;
        }
        
        out.write(b, 0, len);
    }
    
    @Override
    public void close() throws IOException {
        super.close();
        out.close();
    }
}
