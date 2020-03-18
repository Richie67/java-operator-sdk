package com.github.containersolutions.operator;

import com.github.containersolutions.operator.api.Controller;
import com.github.containersolutions.operator.api.ResourceController;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import io.fabric8.kubernetes.client.CustomResourceList;
import javassist.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


class ControllerUtils {

    private final static Logger log = LoggerFactory.getLogger(Operator.class);
    private static ClassPool pool;

    static String getDefaultFinalizer(ResourceController controller) {
        return getAnnotation(controller).finalizerName();
    }

    static <R extends CustomResource> Class<R> getCustomResourceClass(ResourceController controller) {
        return (Class<R>) getAnnotation(controller).customResourceClass();
    }

    static String getCrdName(ResourceController controller) {
        return getAnnotation(controller).crdName();
    }

    public static <R extends CustomResource> Class<? extends CustomResourceList<R>> getCustomResourceListClass(ResourceController<R> controller) {
//        return (Class<? extends CustomResourceList<R>>) getAnnotation(controller).customResourceListClass();
               return createCustomResourceListClass(controller);
    }

    public static <R extends CustomResource> Class<? extends CustomResourceDoneable<R>> getCustomResourceDoneableClass(ResourceController<R> controller) {
        return createCustomResourceDoneableClass(controller);
//        return  (Class<? extends CustomResourceDoneable<R>>) getAnnotation(controller).customResourceDoneableClass();
    }

    private static <R extends CustomResource> Class<? extends CustomResourceDoneable<R>> createCustomResourceDoneableClass(ResourceController<R> controller) {
        Class<R> customResourceClass = (Class<R>) getAnnotation(controller).customResourceClass();
        pool = ClassPool.getDefault();
        pool.insertClassPath(new ClassClassPath(ControllerUtils.class));

        CtClass customResource = null;
        CtClass superClass = null;
        try {
            superClass = pool.get("io.fabric8.kubernetes.client.CustomResourceDoneable");
            customResource = pool.makeClass(String.valueOf(customResourceClass));
        } catch (NotFoundException e) {
            log.error("Error getting superClass: {}", e);
        }
        String className = getPackageName(customResourceClass.getName(), "GeneratedTestCustomResourceDoneable");
        CtClass customDoneable = pool.makeClass(className, superClass);
        try {
            CtClass function = pool.get("io.fabric8.kubernetes.api.builder.Function");
            CtClass[] argTypes = {customResource, function};
            CtConstructor cc = CtNewConstructor.make(argTypes, null, customDoneable);
            customDoneable.addConstructor(cc);
        } catch (CannotCompileException | NotFoundException e) {
            log.error("Error compiling constructor for CustomResourceDoneable class: {}", e);
        }

        Class<? extends CustomResourceDoneable<R>> doneableClass = getClassFromCtClass(customDoneable);
        return doneableClass;
    }

    private static <R extends CustomResource> Class<? extends CustomResourceList<R>> createCustomResourceListClass(ResourceController<R> controller) {
        Class<R> customResourceClass = (Class<R>) getAnnotation(controller).customResourceClass();
        pool = ClassPool.getDefault();
        pool.insertClassPath(new ClassClassPath(ControllerUtils.class));

        CtClass superClass = null;
        try {
            superClass = pool.get("io.fabric8.kubernetes.client.CustomResourceList");
        } catch (NotFoundException e) {
            log.error("Error getting superClass: {}", e);
        }
        String className = getPackageName(customResourceClass.getName(), "GeneratedTestCustomResourceList");
        CtClass customList = pool.makeClass(className, superClass);

        Class<? extends CustomResourceList<R>> listClass = getClassFromCtClass(customList);
        return listClass;
    }

    private static Controller getAnnotation(ResourceController controller) {
        return controller.getClass().getAnnotation(Controller.class);
    }

    private static String getPackageName(String customResourceName, String newClassName) {
        CtClass customResource = null;
        try {
            customResource = pool.get(customResourceName);
        } catch (NotFoundException e) {
            log.error("Error getting class: {}", e);
        }
        String packageName = customResource != null ? customResource.getPackageName() : "";
        return packageName + "." + newClassName;
    }

    private static Class getClassFromCtClass(CtClass customCtClass) {
        Class customClass = null;
        try {
            customClass = customCtClass.toClass();
        } catch (CannotCompileException e) {
            log.error("Error transforming CtClass to Class: {}", e);
        }
        return customClass;
    }
}