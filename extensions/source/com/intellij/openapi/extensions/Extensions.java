/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.extensions;

import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import org.apache.commons.collections.MultiHashMap;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

// todo: make default area instance a non-null object

public abstract class Extensions {
  private static LogProvider ourLogger = new SimpleLogProvider();

  public static final String AREA_LISTENER_EXTENSION_POINT = "jetbrains.fabrique.platform.areaListeners";

  private static Map ourAreaClass2prototypeArea;
  private static Map ourAreaInstance2area;
  private static MultiHashMap ourAreaClass2instances;
  private static Map ourAreaInstance2class;
  private static Map ourAreaClass2Configuration;

  public static ExtensionsArea getRootArea() {
    return getArea(null);
  }

  public static ExtensionsArea getArea(AreaInstance areaInstance) {
    init();
    if (!ourAreaInstance2area.containsKey(areaInstance)) {
      throw new IllegalArgumentException("No area instantiated for: " + areaInstance);
    }
    return (ExtensionsArea) ourAreaInstance2area.get(areaInstance);
  }

  public static Object[] getExtensions(String extensionPointName) {
    return getExtensions(extensionPointName, null);
  }

  public static Object[] getExtensions(String extensionPointName, AreaInstance areaInstance) {
    ExtensionsArea area = getArea(areaInstance);
    assert area != null: "Unable to get area for " + areaInstance;
    ExtensionPoint extensionPoint = area.getExtensionPoint(extensionPointName);
    assert extensionPoint != null: "Unable to get extension point " + extensionPoint + " for " + areaInstance;
    return extensionPoint.getExtensions();
  }

  private static void init() {
    if (ourAreaInstance2area == null) {
      ourAreaInstance2area = new HashMap();
      ourAreaClass2prototypeArea = new HashMap();
      ourAreaClass2instances = new MultiHashMap();
      ourAreaInstance2class = new HashMap();
      ourAreaClass2Configuration = new HashMap();
      ExtensionsAreaImpl rootArea = new ExtensionsAreaImpl(null, null, null, ourLogger);
      ourAreaInstance2area.put(null, rootArea);
      ourAreaClass2prototypeArea.put(null, rootArea);
      rootArea.registerExtensionPoint(AREA_LISTENER_EXTENSION_POINT, AreaListener.class.getName());
    }
  }

  static void reset() {
    ourAreaInstance2area = null;
    ourAreaClass2instances = null;
    ourAreaClass2prototypeArea = null;
    ourAreaInstance2class = null;
  }

  public static void instantiateArea(String areaClass, AreaInstance areaInstance, AreaInstance parentAreaInstance) {
    if (areaClass == null) {
      throw new IllegalArgumentException("Should not try to instantiate the root area");
    }
    init();
    if (!ourAreaClass2Configuration.containsKey(areaClass)) {
      throw new IllegalArgumentException("Area class is not registered: " + areaClass);
    }
    if (ourAreaInstance2area.containsKey(areaInstance)) {
      throw new IllegalArgumentException("Area already instantiated for: " + areaInstance);
    }
    ExtensionsArea parentArea = getArea(parentAreaInstance);
    AreaClassConfiguration configuration = (AreaClassConfiguration)ourAreaClass2Configuration.get(areaClass);
    if (!equals(parentArea.getAreaClass(), configuration.getParentClassName())) {
      throw new IllegalArgumentException("Wrong parent area. Expected class: " + configuration.getParentClassName() + " actual class: " + parentArea.getAreaClass());
    }
    ExtensionsAreaImpl area = new ExtensionsAreaImpl(areaClass, areaInstance, parentArea.getPicoContainer(), ourLogger);
    ourAreaInstance2area.put(areaInstance, area);
    ourAreaClass2instances.put(areaClass, areaInstance);
    ourAreaInstance2class.put(areaInstance, areaClass);
    AreaListener[] listeners = getAreaListeners();
    for (int i = 0; i < listeners.length; i++) {
      AreaListener listener = listeners[i];
      listener.areaCreated(areaClass, areaInstance);
    }
  }

  private static AreaListener[] getAreaListeners() {
    AreaListener[] listeners = (AreaListener[]) getRootArea().getExtensionPoint(AREA_LISTENER_EXTENSION_POINT).getExtensions();
    return listeners;
  }

  public static void registerAreaClass(String areaClass, String parentAreaClass) {
    init();
    if (ourAreaClass2Configuration.containsKey(areaClass)) {
      // allow duplicate area class registrations if they are the same - fixing duplicate registration in tests is much more trouble
      AreaClassConfiguration configuration = (AreaClassConfiguration)ourAreaClass2Configuration.get(areaClass);
      if (!equals(configuration.getParentClassName(), parentAreaClass)) {
        throw new RuntimeException("Area class already registered: " + areaClass, ((AreaClassConfiguration)ourAreaClass2Configuration.get(areaClass)).getCreationPoint());
      }
      else {
        return;
      }
    }
    AreaClassConfiguration configuration = new AreaClassConfiguration(areaClass, parentAreaClass);
    ourAreaClass2Configuration.put(areaClass, configuration);
  }

  public static void disposeArea(AreaInstance areaInstance) {
    assert ourAreaInstance2area.containsKey(areaInstance);
    if (areaInstance == null) {
      throw new IllegalArgumentException("Cannot dispose root area");
    }

    AreaListener[] listeners = getAreaListeners();
    String areaClass = (String) ourAreaInstance2class.get(areaInstance);
    if (areaClass == null) {
      throw new IllegalArgumentException("Area class is null (area never instantiated?). Instance: " + areaInstance);
    }
    try {
      for (int i = 0; i < listeners.length; i++) {
        listeners[i].areaDisposing(areaClass, areaInstance);
      }
    } finally {
      ourAreaInstance2area.remove(areaInstance);
      ourAreaClass2instances.remove(ourAreaInstance2class.remove(areaInstance), areaInstance);
      ourAreaInstance2class.remove(areaInstance);
    }
  }

  public static AreaInstance[] getAllAreas() {
    init();
    final Set keys = ourAreaInstance2area.keySet();
    return (AreaInstance[]) keys.toArray(new AreaInstance[keys.size()]);
  }

  public static AreaInstance[] getAllAreas(String areaClass) {
    Collection instances = (Collection) ourAreaClass2instances.get(areaClass);
    if (instances != null) {
      return (AreaInstance[]) instances.toArray(new AreaInstance[instances.size()]);
    }
    return new AreaInstance[0];
  }

  public static Object getAreaClass(AreaInstance areaInstance) {
    if (areaInstance == null) return null;

    assert ourAreaInstance2class.containsKey(areaInstance);
    return ourAreaInstance2class.get(areaInstance);
  }

  public static void unregisterAreaClass(String areaClass) {
    init();
    assert ourAreaClass2Configuration.containsKey(areaClass) : "Area class is not registered: " + areaClass;
    ourAreaClass2Configuration.remove(areaClass);
  }

  private static boolean equals(Object object1, Object object2) {
      if (object1 == object2) {
          return true;
      }
      if ((object1 == null) || (object2 == null)) {
          return false;
      }
      return object1.equals(object2);
  }

  public static void setLogProvider(LogProvider logProvider) {
    ourLogger = logProvider;
  }

  private static class AreaClassConfiguration {
    private String myClassName;
    private String myParentClassName;
    private Throwable myCreationPoint;

    AreaClassConfiguration(String className, String parentClassName) {
      myCreationPoint = new Throwable();
      myClassName = className;
      myParentClassName = parentClassName;
    }

    public Throwable getCreationPoint() {
      return myCreationPoint;
    }

    public String getClassName() {
      return myClassName;
    }

    public String getParentClassName() {
      return myParentClassName;
    }
  }

  public static class SimpleLogProvider implements LogProvider {
    public void error(String message) {
      new Throwable(message).printStackTrace();
    }

    public void error(String message, Throwable t) {
      System.err.println(message);
      t.printStackTrace();
    }

    public void error(Throwable t) {
      t.printStackTrace();
    }

    public void warn(String message) {
      System.err.println(message);
    }

    public void warn(String message, Throwable t) {
      System.err.println(message);
      t.printStackTrace();
    }

    public void warn(Throwable t) {
      t.printStackTrace();
    }
  }
}
