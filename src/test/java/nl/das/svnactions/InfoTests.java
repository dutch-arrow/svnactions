/*
 * Copyright © 2022 Dutch Arrow Software - All Rights Reserved
 * You may use, distribute and modify this code under the
 * terms of the Apache Software License 2.0.
 *
 * Created 14 Oct 2022.
 */


package nl.das.svnactions;

import java.util.List;
import java.util.Properties;

import org.junit.BeforeClass;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNException;

/**
 *
 */
public class InfoTests {
	private static Properties props;
	private static SvnActions svnActions;

	@BeforeClass
	public static void before() throws SVNException {
		props = new Properties();
		props.setProperty("repohost", "http://192.168.178.140");
		props.setProperty("path.trunk", "/svn/dasrepo/trunk/nodered-live");
		props.setProperty("path.branches", "/svn/dasrepo/branches");
		props.setProperty("path.sql", "/svn/dasrepo/trunk/sql");
		props.setProperty("workdir", "/homes/tom/.node-red");
		props.setProperty("username", "tom");
		props.setProperty("password", "Thomas1953!");
		svnActions = new SvnActions(props);
	}

	@Test
	public void testWorkDirInfo() {
		try {
			String branchName = svnActions.getWCUrl().replace(props.getProperty("repohost") + props.getProperty("path.branches") + "/", "");
			System.out.println(branchName);
			List<String> dirtyPaths = svnActions.getWCModifications();
			for ( String s : dirtyPaths) {
				System.out.println(s);
			}
		} catch (SVNException e) {
			e.printStackTrace();
		}
	}
}
