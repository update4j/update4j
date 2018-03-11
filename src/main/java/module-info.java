module uptodate {

	/*
	 * Public API
	 */
	exports uptodate;
	exports uptodate.service;

	/*
	 * We list all system modules to make it available to layers requiring them;
	 */
	requires java.jnlp;
	requires java.se;
	requires java.se.ee;
	requires java.smartcardio;
	requires javafx.base;
	requires javafx.controls;
	requires javafx.fxml;
	requires javafx.graphics;
	requires javafx.media;
	requires javafx.swing;
	requires javafx.web;
	requires java.xml.bind;

	//	requires jdk.attach;
	//	requires jdk.accessibility;
	//	requires jdk.charsets;
	//	requires jdk.crypto.cryptoki;
	//	requires jdk.crypto.ec;
	//	requires jdk.crypto.mscapi;
	//	requires jdk.deploy;
	//	requires jdk.deploy.controlpanel;
	//	requires jdk.dynalink;
	//	requires jdk.httpserver;
	//	requires jdk.incubator.httpclient;
	//	requires jdk.internal.le;
	//	requires jdk.internal.vm.ci;
	//	requires jdk.javaws;
	//	requires jdk.jdwp.agent;
	//	requires jdk.jfr;
	//	requires jdk.jsobject;
	//	requires jdk.localedata;
	//	requires jdk.management;
	//	requires jdk.management.agent;
	//	requires jdk.management.cmm;
	//	requires jdk.management.jfr;
	//	requires jdk.management.resource;
	//	requires jdk.naming.dns;
	//	requires jdk.naming.rmi;
	//	requires jdk.net;
	//	requires jdk.pack;
	//	requires jdk.plugin;
	//	requires jdk.plugin.dom;
	//	requires jdk.plugin.server;
	//	requires jdk.scripting.nashorn;
	//	requires jdk.scripting.nashorn.shell;
	//	requires jdk.sctp;
	//	requires jdk.security.auth;
	//	requires jdk.security.jgss;
	//	requires jdk.snmp;
	//	requires jdk.unsupported;
	//	requires jdk.xml.dom;
	//	requires jdk.zipfs;
	//	requires jdk.compiler;
	//	requires jdk.editpad;
	//	requires jdk.hotspot.agent;
	//	requires jdk.internal.ed;
	//	requires jdk.internal.jvmstat;
	//	requires jdk.internal.opt;
	//	requires jdk.jartool;
	//	requires jdk.javadoc;
	//	requires jdk.jcmd;
	//	requires jdk.jconsole;
	//	requires jdk.jdeps;
	//	requires jdk.jdi;
	//	requires jdk.jlink;
	//	requires jdk.jshell;
	//	requires jdk.jstatd;
	//	requires jdk.packager;
	//	requires jdk.packager.services;
	//	requires jdk.policytool;
	//	requires jdk.rmic;
	//	requires jdk.xml.ws;
	//	requires oracle.desktop;
	//	requires oracle.net;

	/*
	 * JAXB framework for Configuration read/write
	 */
	opens uptodate to java.xml.bind;
	opens uptodate.binding to java.xml.bind;

	uses uptodate.service.Delegate;
	uses uptodate.service.UpdateHandler;
	uses uptodate.service.LaunchHandler;

	provides uptodate.service.UpdateHandler with uptodate.service.DefaultUpdateHandler;

}