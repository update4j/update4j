/*
 * Copyright 2018 Mordechai Meisels
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * 		http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
/*
 * The update4j framework keeps your application files synchronized and
 * dynamically launches the application. The framework gives you the ability to
 * customize almost every single process; as how it is started, what to do
 * before or after the update, decide whether to first launch then update
 * silently or the other way around and much more.
 * 
 * The main focus revolves around the {@link Configuration} class which contains almost all
 * logic to update and launch.
 * 
 * 
 */
module org.update4j {

	/*
	 * Public API
	 */
	exports org.update4j;
	exports org.update4j.service;

	/*
	 * For use especially by automated clients.
	 */
	exports org.update4j.mapper;

	/*
	 * We list all system modules to make it available to layers requiring them.
	 */
	requires java.se;
	requires transitive java.xml;

	/*
	 * TODO:
	 * How do we continue with Java 11???
	 */
	requires javafx.base;
	requires javafx.controls;
	requires javafx.fxml;
	requires javafx.graphics;
	requires javafx.media;
	requires javafx.swing;
	requires javafx.web;

	/*
	 * Rarely used. If you want them resolved, require it in bootstrap application module.
	 */
	//	requires java.jnlp;
	//	requires java.smartcardio;
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

	uses org.update4j.service.Delegate;
	uses org.update4j.service.UpdateHandler;
	uses org.update4j.service.Launcher;

	provides org.update4j.service.Delegate with org.update4j.service.DefaultBootstrap;
	provides org.update4j.service.UpdateHandler with org.update4j.service.DefaultUpdateHandler;
	provides org.update4j.service.Launcher with org.update4j.service.DefaultLauncher;

}