package com.txtech.mds.server.util;

import com.google.common.reflect.ClassPath;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

public class ClassLoaders {
    public static Set<Class<?>> findAllClassesUsingClassLoader(ClassLoader classLoader, String packageName) throws IOException {
        return ClassPath.from(classLoader).getAllClasses().stream()
                .filter(clazz -> clazz.getPackageName().startsWith(packageName))
                .map(ClassPath.ClassInfo::load)
                .collect(Collectors.toSet());
    }
}
