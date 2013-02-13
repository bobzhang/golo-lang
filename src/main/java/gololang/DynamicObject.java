package gololang;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.SwitchPoint;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.lang.invoke.MethodHandles.constant;
import static java.lang.invoke.MethodHandles.dropArguments;
import static java.lang.invoke.MethodType.methodType;

public class DynamicObject {

  private final Map<String, Set<SwitchPoint>> switchPoints = new HashMap<>();
  private final Map<String, Object> properties = new HashMap<>();

  public DynamicObject define(String name, Object value) {
    properties.put(name, value);
    if (switchPoints.containsKey(name)) {
      invalidate(name);
    } else {
      switchPoints.put(name, new HashSet<SwitchPoint>());
    }
    return this;
  }

  private void invalidate(String name) {
    Set<SwitchPoint> switches = switchPoints.get(name);
    SwitchPoint.invalidateAll(switches.toArray(new SwitchPoint[switches.size()]));
    switches.clear();
  }

  public Object get(String name) {
    return properties.get(name);
  }

  public DynamicObject undefine(String name) {
    if (properties.containsKey(name)) {
      properties.remove(name);
      invalidate(name);
      switchPoints.remove(name);
    }
    return this;
  }

  private static final MethodHandle PROPERTY_MISSING;

  static {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    try {
      PROPERTY_MISSING = lookup.findStatic(DynamicObject.class, "propertyMissing",
          methodType(Object.class, String.class, Object[].class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new Error("Could not bootstrap the required method handles");
    }
  }

  public static Object propertyMissing(String name, Object[] args) throws NoSuchMethodException {
    throw new NoSuchMethodException("Missing DynamicObject definition for " + name);
  }

  public MethodHandle plug(String name, MethodType type, MethodHandle fallback) {
    Object value = properties.get(name);
    MethodHandle target;
    if (value != null) {
      if (value instanceof MethodHandle) {
        target = (MethodHandle) value;
        if (wrongFunctionSignature(target)) {
          throw new IllegalArgumentException(name + " must have a first a non-array first argument as the dynamic object");
        }
      } else {
        target = dropArguments(constant(Object.class, value), 0, DynamicObject.class);
      }
    } else {
      target = PROPERTY_MISSING
          .bindTo(name)
          .asCollector(Object[].class, type.parameterCount())
          .asType(type);
      switchPoints.put(name, new HashSet<SwitchPoint>());
    }
    SwitchPoint switchPoint = new SwitchPoint();
    switchPoints.get(name).add(switchPoint);
    return switchPoint.guardWithTest(target.asType(type), fallback);
  }

  private boolean wrongFunctionSignature(MethodHandle target) {
    return target.type().parameterCount() < 1 || target.type().parameterType(0).isArray();
  }
}
