/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014, Kyle Sweeney, Gregory Boissinot and other contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.pbc_compile;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.CopyOnWrite;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.Result;
//import hudson.plugins.pbc_compile.
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tools.ToolInstallation;
import hudson.util.ArgumentListBuilder;

/**
 * @author kyle.sweeney@valtech.com
 */
public class PbcCompileBuilder extends Builder {
	/**
	 * GUI fields
	 */
	private final String execName;
	private final String pbcCompileName;
	private final String cmdLineArgs;
	private final boolean continueOnBuildFailure;
	private final boolean unstableIfWarnings;

	/**
	 * When this builder is created in the project configuration step, the
	 * builder object will be created from the strings below.
	 *
	 * @param execName
	 *            The PBC utility file name
	 * @param pbcCompileName
	 *            The PowerBuilder logical name
	 * @param cmdLineArgs
	 *            Whitespace separated list of command line arguments for pbc utility
	 * @param continueOnBuildFailure
	 *            If true, job will continue despite pbc build failure
	 * @param unstableIfWarnings
	 *            If true, job will be unstable if there are warnings
	 */
	@DataBoundConstructor
	public PbcCompileBuilder(String execName, String pbcCompileName, String cmdLineArgs, boolean continueOnBuildFailure, boolean unstableIfWarnings) {
		this.execName = execName;
		this.pbcCompileName = pbcCompileName;
		this.cmdLineArgs = cmdLineArgs;
		this.continueOnBuildFailure = continueOnBuildFailure;
		this.unstableIfWarnings = unstableIfWarnings;
	}

	public String getExecName() {
		return execName;
	}
	
	public String getPbcCompileName() {
		return pbcCompileName;
	}

	public String getCmdLineArgs() {
		return cmdLineArgs;
	}

	public boolean getContinueOnBuildFailure() {
		return continueOnBuildFailure;
	}

	public boolean getUnstableIfWarnings() {
		return unstableIfWarnings;
	}

	public PbcCompileInstallation getPbcCompile() {
		DescriptorImpl descriptor = (DescriptorImpl) getDescriptor();
		for (PbcCompileInstallation i : descriptor.getInstallations()) {
			if (pbcCompileName != null && i.getName().equals(pbcCompileName))
				return i;
		}

		return null;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
			throws InterruptedException, IOException {
			return runPbcCompile(build, launcher, listener);
	}

	public boolean runPbcCompile(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
			throws InterruptedException, IOException {
		ArgumentListBuilder args = new ArgumentListBuilder();
		PbcCompileInstallation ai = getPbcCompile();

		if (ai == null) {
			listener.getLogger().println("Path To pbc utility: " + execName);
			args.add(execName);
		} else {
			EnvVars env = build.getEnvironment(listener);
			Node node = Computer.currentComputer().getNode();
			if (node != null) {
				ai = ai.forNode(node, listener);
				ai = ai.forEnvironment(env);
				String pathToPbcCompile = getToolFullPath(launcher, ai.getHome(), execName);
				FilePath exec = new FilePath(launcher.getChannel(), pathToPbcCompile);

				try {
					if (!exec.exists()) {
						listener.fatalError(pathToPbcCompile + " doesn't exist");
						return false;
					}
				} catch (IOException e) {
					listener.fatalError("Failed checking for existence of " + pathToPbcCompile);
					return false;
				}

				listener.getLogger().println("Path To pbc utility: " + pathToPbcCompile);
				args.add(pathToPbcCompile);

				if (ai.getDefaultArgs() != null) {
					args.add(tokenizeArgs(ai.getDefaultArgs()));
				}
			}
		}

		EnvVars env = build.getEnvironment(listener);
		String normalizedArgs = cmdLineArgs.replaceAll("[\t\r\n]+", " ");
		normalizedArgs = Util.replaceMacro(normalizedArgs, env);
		normalizedArgs = Util.replaceMacro(normalizedArgs, build.getBuildVariables());

		if (normalizedArgs.trim().length() > 0)
			args.add(tokenizeArgs(normalizedArgs));

		FilePath pwd = build.getModuleRoot();

		if (!launcher.isUnix()) {
			final int cpi = getCodePageIdentifier(build.getCharset());
			if (cpi != 0)
				args.prepend("cmd.exe", "/C", "\"", "chcp", String.valueOf(cpi), "&&");
			else
				args.prepend("cmd.exe", "/C", "\"");
			args.add("\"", "&&", "exit", "%%ERRORLEVEL%%");
		}

		try {
			listener.getLogger()
					.println(String.format("Executing the command %s from %s", args.toStringWithQuote(), pwd));
			// Parser to find the number of Warnings/Errors
			PbcCompileConsoleParser mbcp = new PbcCompileConsoleParser(listener.getLogger(), build.getCharset());
			PbcCompilerConsoleAnnotator annotator = new PbcCompilerConsoleAnnotator(listener.getLogger(),
					build.getCharset());
			// Launch the pbc utility
			int r = launcher.launch().cmds(args).envs(env).stdout(mbcp).stdout(annotator).pwd(pwd).join();
			// Check the number of warnings
			if (unstableIfWarnings && mbcp.getNumberOfWarnings() > 0) {
				listener.getLogger().println("> Set build UNSTABLE because there are warnings.");
				build.setResult(Result.UNSTABLE);
			}
			// Return the result of the compilation
			return continueOnBuildFailure ? true : (r == 0);
		} catch (IOException e) {
			Util.displayIOException(e, listener);
			build.setResult(Result.FAILURE);
			return false;
		}
	}

	/**
	 * Get the full path of the tool to run. If given path is a directory, this
	 * will append the executable name.
	 */
	static String getToolFullPath(Launcher launcher, String pathToTool, String execName)
			throws IOException, InterruptedException {
		String fullPath = pathToTool;
		FilePath exec = new FilePath(launcher.getChannel(), fullPath);
		if (exec.isDirectory()) {
			if (!fullPath.endsWith("\\")) {
				fullPath = fullPath + "\\";
			}
			fullPath = fullPath + execName;
		}
		return fullPath;
	}

	@Override
	public Descriptor<Builder> getDescriptor() {
		return super.getDescriptor();
	}

	/**
	 * Tokenize a set of arguments, preserving quotes.
	 *
	 * @param args
	 * @return
	 */
	static String[] tokenizeArgs(String args) {

		if (args == null) {
			return null;
		}

		final String[] tokenize = Util.tokenize(args);

		if (args.endsWith("\\")) {
			tokenize[tokenize.length - 1] = tokenize[tokenize.length - 1] + "\\";
		}

		return tokenize;
	}

	@Extension
	@Symbol("pbc-compile-2019")
	public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
		@CopyOnWrite
		private volatile PbcCompileInstallation[] installations = new PbcCompileInstallation[0];

		public DescriptorImpl() {
			super(PbcCompileBuilder.class);
			load();
		}

		@Override
		public String getDisplayName() {
			return Messages.PbcCompileBuilder_DisplayName();
		}

		@SuppressWarnings("rawtypes")
		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		public PbcCompileInstallation[] getInstallations() {
			return Arrays.copyOf(installations, installations.length);
		}

		public void setInstallations(PbcCompileInstallation... antInstallations) {
			this.installations = antInstallations;
			save();
		}

		public PbcCompileInstallation.DescriptorImpl getToolDescriptor() {
			return ToolInstallation.all().get(PbcCompileInstallation.DescriptorImpl.class);
		}
	}

	private static int getCodePageIdentifier(Charset charset) {
		final String s_charset = charset.name();
		if (s_charset.equalsIgnoreCase("utf-8")) // Unicode
			return 65001;
		else if (s_charset.equalsIgnoreCase("ibm437")) // US
			return 437;
		else if (s_charset.equalsIgnoreCase("ibm850")) // OEM Multilingual Latin
														// 1
			return 850;
		else if (s_charset.equalsIgnoreCase("ibm852")) // OEM Latin2
			return 852;
		else if (s_charset.equalsIgnoreCase("shift_jis") || s_charset.equalsIgnoreCase("windows-31j")) // Japanese
			return 932;
		else if (s_charset.equalsIgnoreCase("us-ascii")) // US-ASCII
			return 20127;
		else if (s_charset.equalsIgnoreCase("euc-jp")) // Japanese
			return 20932;
		else if (s_charset.equalsIgnoreCase("iso-8859-1")) // Latin 1
			return 28591;
		else if (s_charset.equalsIgnoreCase("iso-8859-2")) // Latin 2
			return 28592;
		else if (s_charset.equalsIgnoreCase("IBM00858"))
			return 858;
		else if (s_charset.equalsIgnoreCase("IBM775"))
			return 775;
		else if (s_charset.equalsIgnoreCase("IBM855"))
			return 855;
		else if (s_charset.equalsIgnoreCase("IBM857"))
			return 857;
		else if (s_charset.equalsIgnoreCase("ISO-8859-4"))
			return 28594;
		else if (s_charset.equalsIgnoreCase("ISO-8859-5"))
			return 28595;
		else if (s_charset.equalsIgnoreCase("ISO-8859-7"))
			return 28597;
		else if (s_charset.equalsIgnoreCase("ISO-8859-9"))
			return 28599;
		else if (s_charset.equalsIgnoreCase("ISO-8859-13"))
			return 28603;
		else if (s_charset.equalsIgnoreCase("ISO-8859-15"))
			return 28605;
		else if (s_charset.equalsIgnoreCase("KOI8-R"))
			return 20866;
		else if (s_charset.equalsIgnoreCase("KOI8-U"))
			return 21866;
		else if (s_charset.equalsIgnoreCase("UTF-16"))
			return 1200;
		else if (s_charset.equalsIgnoreCase("UTF-32"))
			return 12000;
		else if (s_charset.equalsIgnoreCase("UTF-32BE"))
			return 12001;
		else if (s_charset.equalsIgnoreCase("windows-1250"))
			return 1250;
		else if (s_charset.equalsIgnoreCase("windows-1251"))
			return 1251;
		else if (s_charset.equalsIgnoreCase("windows-1252"))
			return 1252;
		else if (s_charset.equalsIgnoreCase("windows-1253"))
			return 1253;
		else if (s_charset.equalsIgnoreCase("windows-1254"))
			return 1254;
		else if (s_charset.equalsIgnoreCase("windows-1257"))
			return 1257;
		else if (s_charset.equalsIgnoreCase("Big5"))
			return 950;
		else if (s_charset.equalsIgnoreCase("EUC-KR"))
			return 51949;
		else if (s_charset.equalsIgnoreCase("GB18030"))
			return 54936;
		else if (s_charset.equalsIgnoreCase("GB2312"))
			return 936;
		else if (s_charset.equalsIgnoreCase("IBM-Thai"))
			return 20838;
		else if (s_charset.equalsIgnoreCase("IBM01140"))
			return 1140;
		else if (s_charset.equalsIgnoreCase("IBM01141"))
			return 1141;
		else if (s_charset.equalsIgnoreCase("IBM01142"))
			return 1142;
		else if (s_charset.equalsIgnoreCase("IBM01143"))
			return 1143;
		else if (s_charset.equalsIgnoreCase("IBM01144"))
			return 1144;
		else if (s_charset.equalsIgnoreCase("IBM01145"))
			return 1145;
		else if (s_charset.equalsIgnoreCase("IBM01146"))
			return 1146;
		else if (s_charset.equalsIgnoreCase("IBM01147"))
			return 1147;
		else if (s_charset.equalsIgnoreCase("IBM01148"))
			return 1148;
		else if (s_charset.equalsIgnoreCase("IBM01149"))
			return 1149;
		else if (s_charset.equalsIgnoreCase("IBM037"))
			return 37;
		else if (s_charset.equalsIgnoreCase("IBM1026"))
			return 1026;
		else if (s_charset.equalsIgnoreCase("IBM273"))
			return 20273;
		else if (s_charset.equalsIgnoreCase("IBM277"))
			return 20277;
		else if (s_charset.equalsIgnoreCase("IBM278"))
			return 20278;
		else if (s_charset.equalsIgnoreCase("IBM280"))
			return 20280;
		else if (s_charset.equalsIgnoreCase("IBM284"))
			return 20284;
		else if (s_charset.equalsIgnoreCase("IBM285"))
			return 20285;
		else if (s_charset.equalsIgnoreCase("IBM297"))
			return 20297;
		else if (s_charset.equalsIgnoreCase("IBM420"))
			return 20420;
		else if (s_charset.equalsIgnoreCase("IBM424"))
			return 20424;
		else if (s_charset.equalsIgnoreCase("IBM500"))
			return 500;
		else if (s_charset.equalsIgnoreCase("IBM860"))
			return 860;
		else if (s_charset.equalsIgnoreCase("IBM861"))
			return 861;
		else if (s_charset.equalsIgnoreCase("IBM863"))
			return 863;
		else if (s_charset.equalsIgnoreCase("IBM864"))
			return 864;
		else if (s_charset.equalsIgnoreCase("IBM865"))
			return 865;
		else if (s_charset.equalsIgnoreCase("IBM869"))
			return 869;
		else if (s_charset.equalsIgnoreCase("IBM870"))
			return 870;
		else if (s_charset.equalsIgnoreCase("IBM871"))
			return 20871;
		else if (s_charset.equalsIgnoreCase("ISO-2022-JP"))
			return 50220;
		else if (s_charset.equalsIgnoreCase("ISO-2022-KR"))
			return 50225;
		else if (s_charset.equalsIgnoreCase("ISO-8859-3"))
			return 28593;
		else if (s_charset.equalsIgnoreCase("ISO-8859-6"))
			return 28596;
		else if (s_charset.equalsIgnoreCase("ISO-8859-8"))
			return 28598;
		else if (s_charset.equalsIgnoreCase("windows-1255"))
			return 1255;
		else if (s_charset.equalsIgnoreCase("windows-1256"))
			return 1256;
		else if (s_charset.equalsIgnoreCase("windows-1258"))
			return 1258;
		else
			return 0;
	}

}
