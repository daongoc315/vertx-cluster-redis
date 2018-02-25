/*
 * Copyright (c) 2018 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.spi.cluster.redis;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.impl.MessageImpl;
import io.vertx.core.eventbus.impl.clustered.ClusterNodeInfo;
import io.vertx.core.eventbus.impl.clustered.ClusteredEventBus;
import io.vertx.core.eventbus.impl.clustered.ClusteredMessage;
import io.vertx.core.eventbus.impl.clustered.ReflectUtil;
import io.vertx.core.impl.HAManager;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.impl.ServerID;

/**
 * Non Public API Utility
 * 
 * @author Leo Tu - leo.tu.taipei@gmail.com
 */
public class NonPublicAPI {
	// private static final Logger log = LoggerFactory.getLogger(NonPublicAPI.class);

	// static private boolean debug = true;

	public static final String HA_CLUSTER_MAP_NAME;
	public static final String EB_SERVER_ID_HA_KEY;
	public static final String EB_SUBS_MAP_NAME;

	static {
		HA_CLUSTER_MAP_NAME = Reflection.getStaticFinalField(HAManager.class, "CLUSTER_MAP_NAME");
		EB_SERVER_ID_HA_KEY = Reflection.getStaticFinalField(ClusteredEventBus.class, "SERVER_ID_HA_KEY");
		EB_SUBS_MAP_NAME = Reflection.getStaticFinalField(ClusteredEventBus.class, "SUBS_MAP_NAME");
	}

	/**
	 * FIX: Non Vert.x thread
	 */
	public static void runOnContext(Vertx vertx, Handler<Void> action) {
		if (Vertx.currentContext() != null) { // FIXME
			action.handle(null);
		} else {
			vertx.getOrCreateContext().runOnContext(action);
		}
	}

	// public static boolean isInactive(Vertx vertx, RedissonClient redisson) {
	// final ClusteredEventBus eventBus = (ClusteredEventBus) vertx.eventBus();
	// if (eventBus != null) {
	// final HAManager haManager = ClusteredEventBusAPI.getHAManager(vertx);
	// final VertxInternal vertxInternal = (VertxInternal) vertx;
	// if (haManager != null) {
	// final boolean haManagerStopped = Reflection.getField(haManager, HAManager.class, "stopped");
	// return vertxInternal.isKilled() || redisson.isShutdown() || redisson.isShuttingDown() || haManager.isKilled()
	// || haManagerStopped;
	// } else {
	// return vertxInternal.isKilled() || redisson.isShutdown() || redisson.isShuttingDown();
	// }
	// } else {
	// return redisson.isShutdown() || redisson.isShuttingDown();
	// }
	// }

	/**
	 * 
	 * @see HAManager#addDataToAHAInfo
	 * @see HAManager#addHaInfoIfLost
	 */
	public static void addHaInfoIfLost(HAManager haManager, String nodeId) {
		final JsonObject haInfo = ClusteredEventBusAPI.getHaInfo(haManager);
		final Map<String, String> clusterMap = HAManagerAPI.getClusterMap(haManager);
		clusterMap.put(nodeId, haInfo.encode());
	}

	protected static class HAManagerAPI {
		public static Map<String, String> getClusterMap(HAManager haManager) {
			return Reflection.getFinalField(haManager, HAManager.class, "clusterMap");
		}
	}

	public static class ClusteredEventBusAPI {

		public static ClusteredEventBus getEventBus(Vertx vertx) {
			return (ClusteredEventBus) vertx.eventBus();
		}

		public static HAManager getHAManager(ClusteredEventBus eventBus) {
			return Reflection.getFinalField(eventBus, ClusteredEventBus.class, "haManager");
		}

		/**
		 * self
		 */
		public static ServerID getServerID(ClusteredEventBus eventBus) {
			return ReflectUtil.getFinalField(eventBus, ClusteredEventBus.class, "serverID");
		}

		/**
		 * Local ConcurrentHashSet
		 */
		public static Set<String> getOwnSubs(ClusteredEventBus eventBus) {
			return Reflection.getField(eventBus, ClusteredEventBus.class, "ownSubs");
		}

		/**
		 * @see ClusteredEventBus#sendRemote
		 */
		public static void sendRemote(ClusteredEventBus eventBus, ServerID serverID, ClusteredMessage<?, ?> message) {
			Reflection.callMethod(eventBus, ClusteredEventBus.class, "sendRemote",
					new Class[] { ServerID.class, MessageImpl.class }, new Object[] { serverID, message });
		}

		public static ClusterNodeInfo getNodeInfo(ClusteredEventBus eventBus) {
			return Reflection.getField(eventBus, ClusteredEventBus.class, "nodeInfo");
		}

		/**
		 * ? is ConnectionHolder type
		 * 
		 * @see ClusteredEventBus#connections
		 */
		public static ConcurrentMap<ServerID, ?> getConnections(ClusteredEventBus eventBus) {
			// return Reflection.getField(eventBus, ClusteredEventBus.class, "connections");
			return Reflection.callMethod(eventBus, ClusteredEventBus.class, "connections");
		}

		public static void setConnections(ClusteredEventBus eventBus, ConcurrentMap<ServerID, ?> connections) {
			Reflection.setFinalField(eventBus, ClusteredEventBus.class, "connections", connections);
		}

		public static JsonObject getHaInfo(HAManager haManager) {
			return Reflection.getFinalField(haManager, HAManager.class, "haInfo");
		}
	}

	public static class Reflection {

		private static <T> T getStaticFinalField(Class<?> clsObj, String staticFieldName) {
			return getFinalField(null, clsObj, staticFieldName);
		}

		/**
		 * 
		 * @param reflectObj null for static field
		 */
		@SuppressWarnings("unchecked")
		private static <T> T getFinalField(Object reflectObj, Class<?> clsObj, String fieldName) {
			Objects.requireNonNull(clsObj, "clsObj");
			Objects.requireNonNull(fieldName, "fieldName");
			try {
				Field field = clsObj.getDeclaredField(fieldName);
				boolean keepStatus = field.isAccessible();
				if (!keepStatus) {
					field.setAccessible(true);
				}
				try {
					Field modifiersField = Field.class.getDeclaredField("modifiers");
					modifiersField.setAccessible(true);
					modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
					//
					return (T) field.get(reflectObj);
				} finally {
					field.setAccessible(keepStatus);
				}
			} catch (Exception e) {
				Throwable t = e.getCause() != null && e instanceof InvocationTargetException ? e.getCause() : e;
				throw new RuntimeException(fieldName, t);
			}
		}

		/**
		 * @param reflectObj null for static field
		 */
		static public void setFinalField(Object reflectObj, Class<?> clsObj, String fieldName, Object newValue) {
			Objects.requireNonNull(clsObj, "clsObj");
			Objects.requireNonNull(fieldName, "fieldName");
			try {
				Field field = clsObj.getDeclaredField(fieldName);
				boolean keepStatus = field.isAccessible();
				if (!keepStatus) {
					field.setAccessible(true);
				}
				Field modifiersField = Field.class.getDeclaredField("modifiers");
				modifiersField.setAccessible(true);
				modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
				try {
					field.set(reflectObj, newValue);
				} finally {
					field.setAccessible(keepStatus);
					modifiersField.setInt(field, field.getModifiers() & Modifier.FINAL);
				}
			} catch (Exception e) {
				Throwable t = e.getCause() != null && e instanceof InvocationTargetException ? e.getCause() : e;
				throw new RuntimeException(fieldName, t);
			}
		}

		/**
		 * 
		 * @param reflectObj null for static field
		 */
		@SuppressWarnings("unchecked")
		public static <T> T getField(Object reflectObj, Class<?> clsObj, String fieldName) {
			Objects.requireNonNull(clsObj, "clsObj");
			Objects.requireNonNull(fieldName, "fieldName");
			try {
				Field field = clsObj.getDeclaredField(fieldName);
				boolean keepStatus = field.isAccessible();
				if (!keepStatus) {
					field.setAccessible(true);
				}
				try {
					return (T) field.get(reflectObj);
				} finally {
					field.setAccessible(keepStatus);
				}
			} catch (Exception e) {
				Throwable t = e.getCause() != null && e instanceof InvocationTargetException ? e.getCause() : e;
				throw new RuntimeException(fieldName, t);
			}
		}

		/**
		 *
		 * @param reflectObj null for static method
		 */
		public static <T> T callMethod(Object reflectObj, Class<?> clsObj, String methodName) {
			return callMethod(reflectObj, clsObj, methodName, new Class<?>[0], new Object[0]);
		}

		/**
		 *
		 * @param reflectObj null for static method
		 */
		@SuppressWarnings({ "unchecked", "unused" })
		public static <T> T callMethod(Object reflectObj, Class<?> clsObj, String methodName, Class<?>[] argsTypes,
				Object[] argsValues) {
			Objects.requireNonNull(clsObj, "clsObj");
			Objects.requireNonNull(methodName, "methodName");
			try {
				Method method = clsObj.getDeclaredMethod(methodName, argsTypes);
				boolean keepStatus = method.isAccessible();
				if (!keepStatus) {
					method.setAccessible(true);
				}
				try {
					return (T) method.invoke(reflectObj, argsValues);
				} finally {
					method.setAccessible(keepStatus);
				}
			} catch (Exception e) {
				Throwable t = e.getCause() != null && e instanceof InvocationTargetException ? e.getCause() : e;
				throw new RuntimeException(methodName, t);
			}
		}
	}

}