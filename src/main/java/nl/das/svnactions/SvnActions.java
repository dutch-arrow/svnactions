/*
 * Copyright © 2022 Dutch Arrow Software - All Rights Reserved
 * You may use, distribute and modify this code under the
 * terms of the Apache Software License 2.0.
 *
 * Created 14 Oct 2022.
 */


package nl.das.svnactions;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.wc.SVNConflictChoice;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc2.SvnCheckout;
import org.tmatesoft.svn.core.wc2.SvnCommit;
import org.tmatesoft.svn.core.wc2.SvnCopySource;
import org.tmatesoft.svn.core.wc2.SvnExport;
import org.tmatesoft.svn.core.wc2.SvnGetInfo;
import org.tmatesoft.svn.core.wc2.SvnGetMergeInfo;
import org.tmatesoft.svn.core.wc2.SvnGetStatus;
import org.tmatesoft.svn.core.wc2.SvnInfo;
import org.tmatesoft.svn.core.wc2.SvnList;
import org.tmatesoft.svn.core.wc2.SvnLog;
import org.tmatesoft.svn.core.wc2.SvnMerge;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnRemoteCopy;
import org.tmatesoft.svn.core.wc2.SvnRemoteDelete;
import org.tmatesoft.svn.core.wc2.SvnResolve;
import org.tmatesoft.svn.core.wc2.SvnRevisionRange;
import org.tmatesoft.svn.core.wc2.SvnStatus;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.core.wc2.SvnUpdate;

/**
 */
public class SvnActions {
	private Properties props;
	private SvnOperationFactory svnOperationFactory;
	private SVNURL trunkUrl;
	private SVNURL trunkSqlUrl;
	private SVNURL branchBaseUrl;
	private String workdir;
	private String svnUser;
	private String svnPswd;
	private String branchUiPath;
	private String trunkUiPath;

	/**
	 * Constructor<br><br>
	 * Properties:<br>
	 * <table>
	 * <tr><td>host</td><td>Name of the host where the manager runs on (e.g. localhost)</td></tr>
	 * <tr><td>port</td><td>Port number where the manager runs on (e.g.4545)</td></tr>
	 * <tr><td>repohost</td><td>SVN host URL (e.g. http://localhost)</td></tr>
	 * <tr><td>path.trunk</td><td>Path of the SVN trunk (e.g. /svn/test/trunk/nodered-live)</td></tr>
	 * <tr><td>path.branches</td><td>Path of the SVN branches (e.g. /svn/test/branches)</td></tr>
	 * <tr><td>path.sql</td><td>Path to the SVN trunk of the SQL Scripts (e.g. /svn/test/trunk/sql)</td></tr>
	 * <tr><td>workdir</td><td>Absolute path of the NodeRED work directory (e.g. /homes/tom/.node-red)</td></tr>
	 * </table>
	 *
	 * @param properties (see above)
	 * @throws SVNException
	 */
	public SvnActions(Properties properties) {
		this.props = properties;
		try {
			this.trunkUrl = SVNURL.parseURIEncoded(this.props.getProperty("repohost") + this.props.getProperty("path.trunk"));
			this.branchBaseUrl = SVNURL.parseURIEncoded(this.props.getProperty("repohost") + this.props.getProperty("path.branches"));
			this.trunkSqlUrl = SVNURL.parseURIEncoded(this.props.getProperty("repohost") + this.props.getProperty("path.sql"));
		} catch (SVNException e) {
		}
		this.workdir = this.props.getProperty("workdir");
		this.svnUser = this.props.getProperty("username");
		this.svnPswd = this.props.getProperty("password");
		ISVNAuthenticationManager authManager = BasicAuthenticationManager.newInstance(this.svnUser, this.svnPswd.toCharArray());
		this.svnOperationFactory = new SvnOperationFactory();
		this.svnOperationFactory.setAuthenticationManager(authManager);
	}

	public String getWCUrl() throws SVNException {
		List<SvnInfo> infos = new ArrayList<>();
		SvnGetInfo gi = this.svnOperationFactory.createGetInfo();
		gi.addTarget(SvnTarget.fromFile(new File(this.workdir)));
		gi.run(infos);
		return infos.get(0).getUrl().toString();
	}

	public List<String> getWCModifications() throws SVNException {
		List<SvnStatus> sts = new ArrayList<>();
		SvnGetStatus status = this.svnOperationFactory.createGetStatus();
		status.setDepth(SVNDepth.INFINITY);
		status.addTarget(SvnTarget.fromFile(new File(this.workdir)));
		status.run(sts);
		List<String> dirtyPaths = new ArrayList<>();
		for ( SvnStatus s : sts) {
			if (	(s.getNodeStatus() == SVNStatusType.STATUS_MODIFIED) ||
					(s.getNodeStatus() == SVNStatusType.STATUS_ADDED) ||
					(s.getNodeStatus() == SVNStatusType.STATUS_DELETED) ||
					(s.getNodeStatus() == SVNStatusType.STATUS_UNVERSIONED)) {
				dirtyPaths.add(s.getPath().getAbsolutePath().replace(this.workdir,""));
			}
		}
		return dirtyPaths;
	}

	public long getLatestRevision() throws SVNException {
		List<SvnInfo> infos = new ArrayList<>();
		SvnGetInfo gi = this.svnOperationFactory.createGetInfo();
		gi.addTarget(SvnTarget.fromFile(new File(this.workdir)));
		gi.run(infos);
		return infos.get(0).getRevision();
	}

	public List<String> getMyBranches(String user) throws SVNException {
		List<String> myBranches = new ArrayList<>();
		SvnList lst = this.svnOperationFactory.createList();
		lst.addTarget(SvnTarget.fromURL(this.branchBaseUrl));
		lst.setDepth(SVNDepth.IMMEDIATES);
		lst.setRevision(SVNRevision.HEAD);
		List<SVNDirEntry> dirs = new ArrayList<>();
		lst.run(dirs);
		for (SVNDirEntry de : dirs) {
			String br = de.getRelativePath();
			if (br != "") {
				myBranches.add(br);
			}
		}
		return myBranches;
	}

	public void createBranch(String name) throws Exception {
		SvnRemoteCopy remoteCopy = this.svnOperationFactory.createRemoteCopy();
		SVNURL brurl = this.branchBaseUrl.appendPath(name, true);
		SVNURL trurl = this.trunkUrl;
		SvnCopySource src = SvnCopySource.create(SvnTarget.fromURL(trurl), SVNRevision.HEAD);
		remoteCopy.addCopySource(src);
		remoteCopy.addTarget(SvnTarget.fromURL(brurl));
		remoteCopy.setCommitMessage("Branch created");
		remoteCopy.setMakeParents(true);
		remoteCopy.run();

		// Checkout branch in workdir
		// Clear work folder
		SimpleFileVisitor<Path> visitor = new SimpleFileVisitor<Path>() {

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				if (exc != null) {
					throw exc;
				}
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			}
		};
		Files.walkFileTree(Paths.get(this.workdir), visitor);

		// Checkout branch in workdir
		SvnCheckout checkout = this.svnOperationFactory.createCheckout();
		checkout.addTarget(SvnTarget.fromFile(new File(this.workdir)));
		checkout.setSource(SvnTarget.fromURL(brurl));
		checkout.run();
		// Run "npm install" in workdir
		boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
		ProcessBuilder builder = new ProcessBuilder();
		if (isWindows) {
		    builder.command("cmd.exe", "/c", "npm install");
		} else {
		    builder.command("sh", "-c", "npm install");
		}
		builder.directory(new File(this.workdir));
		Process process = builder.start();
		StreamGobbler streamGobbler = new StreamGobbler(process.getInputStream(), l -> {});
		Executors.newSingleThreadExecutor().submit(streamGobbler);
		int exitCode = process.waitFor();
		assert exitCode == 0;
	}

	public void removeBranch(String name) throws SVNException {
		SvnRemoteDelete remoteDel = this.svnOperationFactory.createRemoteDelete();
		SVNURL brurl = this.branchBaseUrl.appendPath(name, true);
		remoteDel.addTarget(SvnTarget.fromURL(brurl));
		remoteDel.setCommitMessage("No longer needed");
		remoteDel.run();
	}

	/**
	 * From the trunk-repo get the latest revision numbers of the NodeRED files:
	 * <ul>
	 * <li>flows.json</li>
	 * <li>uibuilder/[uiPath]/src/index.html</li>
	 * <li>uibuilder/[uiPath]/src/index.js</li>
	 * <li>uibuilder/[uiPath]/src/index.css</li>
	 * </ul>
	 *
	 * @return Array of 4 longs
	 * @throws SVNException
	 * @throws UnsupportedEncodingException
	 */
	public long[] getLatestTrunkRevisions() throws SVNException, UnsupportedEncodingException {
		// flows.json, index.html, index.js, index.css
		long[] rev = {0,0,0,0};
		List<SvnInfo> infos = new ArrayList<>();
		SvnGetInfo gi = this.svnOperationFactory.createGetInfo();
		gi.addTarget(SvnTarget.fromURL(this.trunkUrl.appendPath("flows.json", false)));
		gi.run(infos);
		rev[0] = infos.get(infos.size() - 1).getLastChangedRevision();
		try {
			infos = new ArrayList<>();
			gi = this.svnOperationFactory.createGetInfo();
			gi.addTarget(SvnTarget.fromURL(this.trunkUrl.appendPath("/uibuilder/" + this.trunkUiPath + "/src/index.html", false)));
			infos = new ArrayList<>();
			gi.run(infos);
			rev[1] = infos.get(infos.size() - 1).getLastChangedRevision();
		} catch (SVNException e) {
			if (!(e.getMessage().contains("uibuilder") &&
					(e.getMessage().contains("non-existent") ||
					 e.getMessage().contains("was not found.") ||
					 e.getMessage().contains("path not found:")))) {
				throw e;
			}
		}
		try {
			gi = this.svnOperationFactory.createGetInfo();
			gi.addTarget(SvnTarget.fromURL(this.trunkUrl.appendPath("/uibuilder/" + this.trunkUiPath + "/src/index.js", false)));
			infos = new ArrayList<>();
			gi.run(infos);
			rev[2] = infos.get(infos.size() - 1).getLastChangedRevision();
		} catch (SVNException e) {
			if (!(e.getMessage().contains("uibuilder") &&
					(e.getMessage().contains("non-existent") ||
					 e.getMessage().contains("was not found.") ||
					 e.getMessage().contains("path not found:")))) {
				throw e;
			}
		}
		try {
			gi = this.svnOperationFactory.createGetInfo();
			gi.addTarget(SvnTarget.fromURL(this.trunkUrl.appendPath("/uibuilder/" + this.trunkUiPath + "/src/index.css", false)));
			infos = new ArrayList<>();
			gi.run(infos);
			rev[3] = infos.get(infos.size() - 1).getLastChangedRevision();
		} catch (SVNException e) {
			if (!(e.getMessage().contains("uibuilder") &&
					(e.getMessage().contains("non-existent") ||
					 e.getMessage().contains("was not found.") ||
					 e.getMessage().contains("path not found:")))) {
				throw e;
			}
		}
		return rev;
	}

	/**
	 * From the branch-repo get the latest revision number of the NodeRED files:
	 * <ul>
	 * <li>flows.json</li>
	 * <li>/uibuilder/[uiPath]/src/index.html</li>
	 * <li>/uibuilder/[uiPath]/src/index.js</li>
	 * <li>/uibuilder/[uiPath]/src/index.css</li>
	 * </ul>
	 *
	 * @return Array of 4 longs
	 * @throws SVNException
	 * @throws UnsupportedEncodingException
	 */
	public long[] getLatestBranchRevisions(String branchName) throws SVNException, UnsupportedEncodingException {
		// flows.json, index.html, index.js, index.css
		long[] rev = {0,0,0,0};
		SvnGetInfo gi = this.svnOperationFactory.createGetInfo();
		gi.addTarget(SvnTarget.fromURL(this.branchBaseUrl.appendPath(branchName + "/flows.json", false) ));
		SvnInfo info = gi.run();
		rev[0] = info.getLastChangedRevision();
		try {
			gi = this.svnOperationFactory.createGetInfo();
			gi.addTarget(SvnTarget.fromURL(this.branchBaseUrl.appendPath(branchName + "/uibuilder/" + this.branchUiPath + "/src/index.html", false)));
			info = gi.run();
			rev[1] = info.getLastChangedRevision();
		} catch (SVNException e) {
//			System.out.println("getLatestBranchRevisions: " + e.getMessage());
			if (!(e.getMessage().contains("uibuilder") &&
					(e.getMessage().contains("non-existent") ||
					 e.getMessage().contains("was not found.") ||
					 e.getMessage().contains("path not found:")))) {
				throw e;
			}
		}
		try {
			gi = this.svnOperationFactory.createGetInfo();
			gi.addTarget(SvnTarget.fromURL(this.branchBaseUrl.appendPath(branchName + "/uibuilder/" + this.branchUiPath + "/src/index.js", false)));
			info = gi.run();
			rev[2] = info.getLastChangedRevision();
			} catch (SVNException e) {
				if (!(e.getMessage().contains("uibuilder") &&
						(e.getMessage().contains("non-existent") ||
						 e.getMessage().contains("was not found.") ||
						 e.getMessage().contains("path not found:")))) {
					throw e;
				}
			}
		try {
			gi = this.svnOperationFactory.createGetInfo();
			gi.addTarget(SvnTarget.fromURL(this.branchBaseUrl.appendPath(branchName + "/uibuilder/" + this.branchUiPath + "/src/index.css", false)));
			info = gi.run();
			rev[3] = info.getLastChangedRevision();
			} catch (SVNException e) {
				if (!(e.getMessage().contains("uibuilder") &&
						(e.getMessage().contains("non-existent") ||
						 e.getMessage().contains("was not found.") ||
						 e.getMessage().contains("path not found:")))) {
					throw e;
				}
		}
		return rev;
	}

	/**
	 * Get the latest revision number of trunk that is merged into the branch
	 *
	 * @param fromWC from Working Copy (true) or Repository (false)?
	 * @return the revision number as long.
	 * @throws SVNException
	 */
	public long getLatestTrunkRevInBranch(boolean fromWC, String branchName) throws SVNException {
		long rev = 0;
		SvnGetMergeInfo gmi = this.svnOperationFactory.createGetMergeInfo();
		if(fromWC) {
			gmi.setSingleTarget(SvnTarget.fromFile(new File(this.workdir)));
		} else {
			gmi.setSingleTarget(SvnTarget.fromURL(this.branchBaseUrl.appendPath(branchName, true)));
		}
		Map<SVNURL,SVNMergeRangeList> map = gmi.run();
		if (map != null) {
			for (SVNURL url : map.keySet()) {
				if (url.toString().equalsIgnoreCase(this.trunkUrl.toString())) {
					SVNMergeRangeList lst = map.get(url);
					for (SVNMergeRange r : lst.getRangesAsList()) {
						rev = r.getEndRevision() > rev ? r.getEndRevision() : rev;
					}
				}
			}
		}
		if (rev == 0) {
			rev = getFirstTrunkRevInBranch(fromWC, branchName);
		}
		return rev;
	}

	/**
	 * Determine the first trunk revision in the current branch
	 *
	 * @return
	 * @throws SVNException
	 */
	private long getFirstTrunkRevInBranch(boolean fromWC, String branchName) throws SVNException {
		SvnLog log = this.svnOperationFactory.createLog();
		if (fromWC) {
			log.addTarget(SvnTarget.fromFile(new File(this.props.getProperty("workdir")), SVNRevision.HEAD));
		} else {
			log.addTarget(SvnTarget.fromURL(this.branchBaseUrl.appendPath(branchName, true), SVNRevision.HEAD));
		}
		log.addRange(SvnRevisionRange.create(SVNRevision.HEAD, SVNRevision.create(1)));
		log.setStopOnCopy(true);
		log.setDiscoverChangedPaths(true);
		log.setUseMergeHistory(false);
		List<SVNLogEntry> les = new ArrayList<>();
		log.run(les);
		Map<String, SVNLogEntryPath> changedPaths = les.get(les.size() - 1).getChangedPaths();
		long rev = 0;
		for (String k : changedPaths.keySet()) {
			SVNLogEntryPath ep = changedPaths.get(k);
			rev = (ep.getCopyRevision() > rev ? ep.getCopyRevision() : rev);
		}
		if (rev == 0) {
			rev = les.get(les.size() - 1).getRevision();
		}
		return rev;
	}

	/**
	 * Get the flow.json content of the branch in the work folder.
	 *
	 * @param branch name of the branch
	 * @param revno revision number
	 * @param fromWC from Working Copy (true) or Repository (false)?
	 * @return the content of the flows.json file
	 * @throws IOException
	 * @throws SVNException
	 */
	public String getBranchFlow(String branch, long revno, boolean fromWC) throws IOException, SVNException {
		String content = "";
		if (fromWC) {
			content = new String(Files.readAllBytes(Paths.get(this.workdir + "/flows.json")));
		} else {
			File tmpdir = Files.createTempDirectory("nodered-").toFile();
			SvnExport export = this.svnOperationFactory.createExport();
			export.setSingleTarget(SvnTarget.fromFile(tmpdir));
			export.setForce(true);
			try {
				export.setSource(SvnTarget.fromURL(this.branchBaseUrl.appendPath(branch + "/flows.json", false), SVNRevision.create(revno)));
				export.run();
			} catch (SVNException e) {
				if (!e.getMessage().startsWith("svn: E170000:")) {
					throw e;
				}
				export.setSource(SvnTarget.fromURL(this.branchBaseUrl.appendPath(branch + "/flows.json", false), SVNRevision.create(-1)));
				export.run();
			}
			content = new String(Files.readAllBytes(Paths.get(tmpdir.getAbsolutePath() + "/flows.json")));
			this.branchUiPath = getUiUrl(content);
		}
		return content;
	}

	/**
	 * Get the content of the flows.json file in the trunk
	 *
	 * @param revision Revision number of the flows.json file
	 * @return the content of the flows.json file
	 * @throws SVNException
	 * @throws IOException
	 */
	public String getTrunkFlow(long revision) throws SVNException, IOException {
		String content = "";
		if (revision == 0) {
			// Get Workdir file
			content = new String(Files.readAllBytes(Paths.get(this.workdir + "/flows.json")));
		} else {
			File tmpdir = Files.createTempDirectory("anydb-").toFile();
			SvnExport export = this.svnOperationFactory.createExport();
			export.setSingleTarget(SvnTarget.fromFile(tmpdir));
			export.setForce(true);
			if (revision == -1) {
				// Get HEAD revision of flows.json in repo
				export.setSource(SvnTarget.fromURL(this.trunkUrl.appendPath("flows.json", false), SVNRevision.HEAD));
				export.run();
			} else {
				// Get given revision of flows.json in repo
				try {
					export.setSource(SvnTarget.fromURL(this.trunkUrl.appendPath("flows.json", false), SVNRevision.create(revision)));
					export.run();
				} catch (SVNException e) {
					if (!e.getMessage().startsWith("svn: E170000:")) {
						throw e;
					}
					export.setSource(SvnTarget.fromURL(this.trunkUrl.appendPath("flows.json", false), SVNRevision.create(-1)));
					export.run();
				}
			}
			content = new String(Files.readAllBytes(Paths.get(tmpdir.getAbsolutePath() + "/flows.json")));
			this.trunkUiPath = getUiUrl(content);
		}
		return content;
	}

	/**
	 * Get the content of the index.[type] file of the branch in the work folder.
	 *
	 * @param type "html", "js" or "css"
	 * @return the content of the file
	 * @throws SVNException
	 */
	public String getBranchUi(String type, String branch, long revno, boolean fromWC) throws SVNException {
		String content = "";
		try {
			if (fromWC) {
				content = new String(Files.readAllBytes(Paths.get(this.workdir + "/uibuilder/" + this.branchUiPath + "/src/index." + type)));
			} else {
				try {
					File tmpdir = Files.createTempDirectory("nodered-").toFile();
					SvnExport export = this.svnOperationFactory.createExport();
					export.setSingleTarget(SvnTarget.fromFile(tmpdir));
					export.setForce(true);
					export.setSource(SvnTarget.fromURL(this.branchBaseUrl.appendPath(branch + "/uibuilder/" + this.branchUiPath + "/src/index." + type, false), SVNRevision.create(revno)));
					export.run();
					content = new String(Files.readAllBytes(Paths.get(tmpdir.getAbsolutePath() + "/index." + type)));
				} catch (SVNException e) {
					if (!(e.getMessage().contains("uibuilder") &&
							(e.getMessage().contains("non-existent") ||
							 e.getMessage().contains("doesn't exist") ||
							 e.getMessage().contains("was not found.") ||
							 e.getMessage().contains("path not found:")))) {
						throw e;
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return content;
	}

	/**
	 * Get the content of the UI file determined by the type
	 *
	 * @param revision Revision number of the index.[type] file
	 * @param type 'html', 'js' or 'css'
	 * @return the content of the index-file
	 * @throws SVNException
	 * @throws IOException
	 */
	public String getTrunkUi(String type, long revision) throws SVNException, IOException {
		String content = "";
		try {
			if (revision == 0) {
				// Get Workdir file
				content = new String(Files.readAllBytes(Paths.get(this.workdir + "/uibuilder/" + this.trunkUiPath + "/src/index." + type)));
			} else {
				File tmpdir = Files.createTempDirectory("anydb-").toFile();
				SvnExport export = this.svnOperationFactory.createExport();
				export.setSingleTarget(SvnTarget.fromFile(tmpdir));
				export.setForce(true);
				if (revision == -1) {
					export.setSource(SvnTarget.fromURL(this.trunkUrl.appendPath("uibuilder/" + this.trunkUiPath + "/src/index." + type, false), SVNRevision.HEAD));
				} else {
					export.setSource(SvnTarget.fromURL(this.trunkUrl.appendPath("uibuilder/" + this.trunkUiPath + "/src/index." + type, false), SVNRevision.create(revision)));
				}
				export.run();
				content = new String(Files.readAllBytes(Paths.get(tmpdir.getAbsolutePath() + "/index." + type)));
			}
		} catch (SVNException e) {
			if (!(e.getMessage().contains("uibuilder") &&
					(e.getMessage().contains("non-existent") ||
					 e.getMessage().contains("doesn't exist") ||
					 e.getMessage().contains("was not found.") ||
					 e.getMessage().contains("path not found:")))) {
				throw e;
			}
		}
		return content;
	}

	/**
	 * Write new content to the flows.json file in the work folder
	 *
	 * @param flow the new content
	 * @throws IOException
	 */
	public void updateFlow(String flow) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(this.workdir + "/flows.json"));
        writer.write(flow);
        writer.close();
	}

	/**
	 * Write new content to the index.[type]  file in the work folder.
	 *
	 * @param type "html", "js" or "css"
	 * @param content the new content
	 * @throws IOException
	 */
	public void updateUi(String type, String content) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(this.workdir + "/uibuilder/" + this.branchUiPath + "/src/index." + type));
        writer.write(content);
        writer.close();
	}

	/**
	 * Commit the work folder
	 *
	 * @param commitMessage
	 * @return the new revision number
	 * @throws SVNException
	 */
	public long commit(final String commitMessage) throws SVNException {
		SvnCommit commit = this.svnOperationFactory.createCommit();
		commit.setCommitMessage(commitMessage);
		commit.addTarget(SvnTarget.fromFile(new File(this.workdir + "/flows.json")));
		if (Files.exists(Paths.get(this.workdir + "/uibuilder/" + this.branchUiPath + "/src/index.html"))) {
			commit.addTarget(SvnTarget.fromFile(new File(this.workdir + "/uibuilder/" + this.branchUiPath + "/src/index.html")));
			commit.addTarget(SvnTarget.fromFile(new File(this.workdir + "/uibuilder/" + this.branchUiPath + "/src/index.js")));
			commit.addTarget(SvnTarget.fromFile(new File(this.workdir + "/uibuilder/" + this.branchUiPath + "/src/index.css")));
		}
		commit.addTarget(SvnTarget.fromFile(new File(this.workdir)));
		SVNCommitInfo ci = commit.run();
		SvnUpdate update = this.svnOperationFactory.createUpdate();
		update.addTarget(SvnTarget.fromFile(new File(this.workdir)));
		update.run();
		return ci.getNewRevision();
	}

	/**
	 * Register the merge in the work folder
	 *
	 * @throws SVNException
	 */
	public void merge(boolean trunkInBranch, String branch) throws SVNException {
		SvnMerge merge = this.svnOperationFactory.createMerge();
		merge.addTarget(SvnTarget.fromFile(new File(this.workdir)));
		if (trunkInBranch) {
			merge.setSource(SvnTarget.fromURL(this.trunkUrl), true);
		} else {
			merge.setSource(SvnTarget.fromURL(this.branchBaseUrl.appendPath(branch, true)), true);
		}
		System.out.println("target: " + merge.getFirstTarget().getPathOrUrlString());
		System.out.println("source: " + merge.getSource().getPathOrUrlString());
		merge.setRecordOnly(true);
		merge.setAllowMixedRevisions(true);
		merge.run();
		SvnResolve resolve = this.svnOperationFactory.createResolve();
		resolve.setConflictChoice(SVNConflictChoice.MINE_FULL);
		resolve.addTarget(SvnTarget.fromFile(new File(this.workdir + "/flows.json")));
		resolve.run();
		if (Files.exists(Paths.get(this.workdir + "/uibuilder/" + this.branchUiPath + "/src/index.html"))) {
			resolve = this.svnOperationFactory.createResolve();
			resolve.addTarget(SvnTarget.fromFile(new File(this.workdir + "/uibuilder/" + this.branchUiPath + "/src/index.html")));
			resolve.run();
			resolve = this.svnOperationFactory.createResolve();
			resolve.addTarget(SvnTarget.fromFile(new File(this.workdir + "/uibuilder/" + this.branchUiPath + "/src/index.js")));
			resolve.run();
			resolve = this.svnOperationFactory.createResolve();
			resolve.addTarget(SvnTarget.fromFile(new File(this.workdir + "/uibuilder/" + this.branchUiPath + "/src/index.css")));
			resolve.run();
		}
		SvnUpdate update = this.svnOperationFactory.createUpdate();
		update.addTarget(SvnTarget.fromFile(new File(this.workdir)));
		update.run();
	}

	/**
	 * Gobble the inputstream through the given consumer.
	 */
	private static class StreamGobbler implements Runnable {
	    private InputStream inputStream;
	    private Consumer<String> consumer;

	    public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
	        this.inputStream = inputStream;
	        this.consumer = consumer;
	    }

	    @Override
	    public void run() {
	        new BufferedReader(new InputStreamReader(this.inputStream)).lines()
	          .forEach(this.consumer);
	    }
	}

	/**
	 * Determine the URL path of the UI
	 *
	 * @param flow
	 * @return
	 */
	private String getUiUrl(String flow) {
		String url = "";
		JsonArray jsonArray = Json.createReader(new StringReader(flow)).readArray();
		for (JsonValue jo : jsonArray) {
			if (jo.getClass() == JsonObject.class) {
				JsonObject obj = (JsonObject) jo;
				String type = obj.getString("type");
				if (type.equalsIgnoreCase("uibuilder")) {
					url = obj.getString("url");
				}
			}
		}
		return url;
	}
}
