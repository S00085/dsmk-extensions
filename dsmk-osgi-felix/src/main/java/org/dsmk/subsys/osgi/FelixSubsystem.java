package org.dsmk.subsys.osgi;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.UUID;

import javax.naming.NamingException;

import org.apache.felix.main.AutoProcessor;
import org.dsmk.api.Log;
import org.dsmk.api.LogServer;
import org.dsmk.api.NameServer;
import org.dsmk.api.Result;
import org.dsmk.api.Subsystem;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.dsmk.api.Result.Status;

import com.github.krukow.clj_lang.PersistentHashMap;
import com.google.common.base.Preconditions;

public class FelixSubsystem implements Subsystem {

	private static UUID uuid = UUID.fromString("f7a7e8e6-f138-4701-9bfd-b02ee26903f1");
	private static String NAME = "dsmk.subsys.osgi.felix";
	
	private static final Map<String, Object> persistentMap;
	static {
		Map<String, Object> tempMap = new HashMap();
		tempMap.put("UUID", uuid);
		tempMap.put("NAME", NAME);
		persistentMap = PersistentHashMap.create(tempMap);
	}
	
	private NameServer nameServer;
	private Log log;
	private Properties configProperties;
	private Properties osgiConfiguration;
	private Framework osgiFramework;
	
	public String name() {
		return NAME;
	}

	public UUID id() {
		return uuid;
	}

	public Map<String, Object> attributes() {
		// TODO Auto-generated method stub
		return persistentMap;
	}

	public Result configure(Map<String, Object> config) {
		nameServer = (NameServer)config.get(NameServer.class.getName());
		
		configProperties = (Properties)config.get("config-properties");
		
		Preconditions.checkArgument(nameServer != null, "A valid instance of %s is required for configuring EventSubsystem %s",NameServer.class.getName(),name());
		
		try {
			LogServer logServer = nameServer.lookup1(LogServer.class.getName(),LogServer.class);
			log = logServer.getLog(this);
		} catch (NamingException e) {
			e.printStackTrace(System.err);
			return new Result(Status.NOT_OK,"dmsk.event.notok","dmsk.event.err.onConfig");
		}
		
		Result configLoadResults = loadOSGIFelixConfiguration();
		
		if(configLoadResults.isNotOK()) {
			log.error("Failed to load OSGI felix configuration");
			return configLoadResults;
		}
		log.info("Configuration for felix-subsystem {}",osgiConfiguration);
		
		log.debug("{} configuration complete", name());
		return Result.OK;

	}

	private Result loadOSGIFelixConfiguration() {
		osgiConfiguration = new Properties();
		try {
			osgiConfiguration.load(FelixSubsystem.class.getResourceAsStream("/dsmk-osgi-felix-conf.properties"));
			//override with the main config properties which is a cumulative of kernel config and system config in that order
			osgiConfiguration.putAll(configProperties);
		} catch (IOException e) {
			e.printStackTrace(System.err);
			return new Result(Status.NOT_OK,"ddsmk.osgi.felix.notok","dsmk.osgi.felix.err.configload");
		}
		return Result.OK;
	}

	@SuppressWarnings("unchecked")
	public Result start() {
		log.info("Starting FelixSubsystem - {}", name());
		
		ServiceLoader<FrameworkFactory> frameworkFactoryLoader = ServiceLoader.load(FrameworkFactory.class);
		
		if(frameworkFactoryLoader == null || !frameworkFactoryLoader.iterator().hasNext()) {
			log.error("Failed to find a valid osgi framework");
			return new Result(Status.NOT_OK,"dsmk.osgi.felix.notok","dsmk.osgi.felix.err.frameworknotfound");
		}
		
		
		FrameworkFactory osgiFwFactory = frameworkFactoryLoader.iterator().next();
		
		
		osgiFramework = osgiFwFactory.newFramework((Map)osgiConfiguration);
		
		try {
			osgiFramework.init();
			BundleContext bc = osgiFramework.getBundleContext();
			
			AutoProcessor.process(osgiConfiguration,osgiFramework.getBundleContext());
			osgiFramework.start();
			log.info("Felix osgi framework started successfully..");
			
		} catch (BundleException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return Result.NOT_OK;
		}
		log.info("Started FelixSubsystem - {}", name());
		return Result.OK;
	}

	public Result stop() {
		log.info("Stopping FelixSubsystem - {}", name());
		
		if(osgiFramework != null) {
			log.info("Stopping osgiFramework instance..");
			try {
				osgiFramework.stop();
				
				osgiFramework.waitForStop(0);
			} catch (InterruptedException | BundleException e) {
				e.printStackTrace(System.err);
				return new Result(Status.NOT_OK,"dsmk.osgi.felix.notok","dsmk.osgi.felix.err.stop");
			}
		}
		log.info("Stopped FelixSubsystem - {}", name());
		return Result.OK;
	}

}
