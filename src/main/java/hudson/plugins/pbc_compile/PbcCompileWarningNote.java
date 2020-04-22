package hudson.plugins.pbc_compile;

import hudson.Extension;
import hudson.MarkupText;
import hudson.console.ConsoleAnnotationDescriptor;
import hudson.console.ConsoleAnnotator;
import hudson.console.ConsoleNote;
import hudson.plugins.pbc_compile.Messages;

import org.jenkinsci.Symbol;

import java.util.regex.Pattern;

/**
 * Annotation for PBC warning messages
 */
@SuppressWarnings("rawtypes")
public class PbcCompileWarningNote extends ConsoleNote {
    /**
	 * 
	 */
	private static final long serialVersionUID = 5058082724408336863L;
	/** Pattern to identify warning messages */
    public final static Pattern PATTERN = Pattern.compile("(.*)\\(\\d+(,\\d+){0,1}\\):\\s[Ww]arning\\s(([A-Z]*)\\d+){0,1}:\\s(.*)");
    
    public PbcCompileWarningNote() {
    }

	@Override
    public ConsoleAnnotator annotate(Object context, MarkupText text, int charPos) {
        text.addMarkup(0, text.length(), "<span class=warning-inline>", "</span>");
        return null;
    }

    @Extension @Symbol("pbcCompileWarning")
    public static final class DescriptorImpl extends ConsoleAnnotationDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.PbcCompileBuilder_WarningNoteDescription();
        }
    }
}
