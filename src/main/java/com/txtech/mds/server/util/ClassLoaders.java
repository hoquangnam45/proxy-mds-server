package com.txtech.mds.server.util;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ClassLoaders {
    public static Set<Class<?>> findAllClassesUsingClassLoader(ClassLoader classLoader, String packageName) throws IOException {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(classLoader);
        String pattern = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX
                + ClassUtils.convertClassNameToResourcePath(packageName) + "/**/*.class";
        Resource[] resources = resolver.getResources(pattern);
        return Stream.of(resources)
                .map(resource -> (UrlResource) resource)
                .map(UrlResource::getDescription)
                .map(desc -> desc.split("!"))
                .map(tokens -> tokens[tokens.length - 1])
                .map(token -> token.substring(0, token.lastIndexOf(".class")))
                .map(token -> token.substring(1))
                .map(ClassUtils::convertResourcePathToClassName)
                .map(className -> {
                    try {
                        return classLoader.loadClass(className);
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toSet());
    }
}
